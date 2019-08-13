package com.example.local_udp_sockets_test;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.LinkedTransferQueue;

import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.util.Blob;

public class AudioStreamer implements Runnable {

    private final static String TAG = "AudioStreamer";

    private final static int MAX_READ_SIZE = 2000;

    private Thread t_;

    MediaRecorderThread mediaRecorderThread_;
    ParcelFileDescriptor[] mediaRecorderPfs_;
    ParcelFileDescriptor mediaRecorderReadPfs_, mediaRecorderWritePfs_;
    InputStream mediaRecorderInputStream_;
    ADTSFrameReadingState readingState_;
    FrameBundler bundler_;
    FramePacketizer packetizer_;

    LinkedTransferQueue outputQueue_;

    // reference for ADTS header format: https://wiki.multimedia.cx/index.php/ADTS
    private static class ADTSFrameReadingState {
        byte[] buffer = new byte[MAX_READ_SIZE];
        short current_frame_length = Short.MAX_VALUE; // includes ADTS header length
        int current_bytes_read = 0;
    }

    public AudioStreamer(LinkedTransferQueue outputQueue) {
        readingState_ = new ADTSFrameReadingState();
        bundler_ = new FrameBundler();
        packetizer_ = new FramePacketizer();
        outputQueue_ = outputQueue;

        // create an array of parcel file descriptors
        try {
            mediaRecorderPfs_ = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaRecorderReadPfs_ = new ParcelFileDescriptor(mediaRecorderPfs_[0]);
        mediaRecorderWritePfs_ = new ParcelFileDescriptor(mediaRecorderPfs_[1]);

        mediaRecorderThread_ = new MediaRecorderThread(mediaRecorderWritePfs_.getFileDescriptor());
        mediaRecorderInputStream_ = new ParcelFileDescriptor.AutoCloseInputStream(mediaRecorderReadPfs_);
    }

    public void start() {
        if (t_ == null) {
            t_ = new Thread(this);
            t_.start();
        }
    }

    public void stop() {
        if (t_ != null) {
            mediaRecorderThread_.stop();
            t_.interrupt();
            try {
                t_.join();
            } catch (InterruptedException e) {}
            t_ = null;
        }
    }

    public void run() {

        Log.d(TAG,"AudioStreamer frame processor started.");

        byte[] final_adts_frame_buffer;

        try {
            while (!Thread.interrupted()) {

                mediaRecorderThread_.start();

                int read_size = mediaRecorderInputStream_.available();
                if (read_size > MAX_READ_SIZE) {
                    read_size = MAX_READ_SIZE;
                }
                if (read_size <= 0) { continue; }

                Log.d(TAG, "Attempting to read " + read_size + " bytes from input stream.");
                try {
                    int ret = mediaRecorderInputStream_.read(readingState_.buffer,
                                       readingState_.current_bytes_read,
                                       read_size);
                    if (ret == -1) { break; }

                }
                catch (IOException e) { e.printStackTrace(); }

                Log.d(TAG, "Current contents of reading state buffer: " +
                        Helpers.bytesToHex(readingState_.buffer));

                readingState_.current_bytes_read += read_size;
                Log.d(TAG, "Current bytes read: " + readingState_.current_bytes_read);

                // we've finished reading enough of the header to get the frame length
                if (readingState_.current_bytes_read >= 5) {
                    readingState_.current_frame_length =
                            (short) ((((readingState_.buffer[3]&0x02) << 3 | (readingState_.buffer[4]&0xE0) >> 5) << 8) +
                                    ((readingState_.buffer[4]&0x1F) << 3 | (readingState_.buffer[5]&0xE0) >> 5));
                    Log.d(TAG, "Length of current ADTS frame: " +
                                    readingState_.current_frame_length);
                }

                // we've read the entirety of the current ADTS frame, deliver the full adts frame for
                // processing and set the reading state properly
                if (readingState_.current_bytes_read >= readingState_.current_frame_length) {

                    Log.d(TAG, "Detected that we read a full ADTS frame. Length of current ADTS frame: " +
                            readingState_.current_frame_length);

                    final_adts_frame_buffer = Arrays.copyOf(readingState_.buffer, readingState_.current_frame_length);
                    Log.d(TAG, "ADTS frame read from MediaRecorder stream: " +
                        Helpers.bytesToHex(final_adts_frame_buffer));

                    bundler_.addFrame(final_adts_frame_buffer);

                    // check if there is a full bundle of audio data in the bundler yet; if there is,
                    // deliver it to the FramePacketizer
                    if (bundler_.hasFullBundle()) {
                        Log.d(TAG, "Bundler did have a full bundle, packetizing full audio bundle...");

                        byte[] audioBundle = bundler_.getCurrentBundle();

                        Log.d(TAG, "Contents of full audio bundle: " + Helpers.bytesToHex(audioBundle));

                        Data audioPacket = packetizer_.generateAudioDataPacket(new Name("/test/data"), audioBundle, true, 0);

                        outputQueue_.add(audioPacket);
                    }
                    else {
                        Log.d(TAG, "Bundler did not yet have full bundle, extracting next ADTS frame...");
                    }

                    // we did not read past the end of the current ADTS frame
                    if (readingState_.current_bytes_read == readingState_.current_frame_length) {
                        readingState_.current_bytes_read = 0;
                        readingState_.current_frame_length = Short.MAX_VALUE;
                    }
                    else { // we did read past the end of the current ADTS frame
                        int read_over_length = readingState_.current_bytes_read - readingState_.current_frame_length;
                        System.arraycopy(readingState_.buffer, readingState_.current_frame_length,
                                         readingState_.buffer, 0,
                                         read_over_length);

                        readingState_.current_bytes_read = read_over_length;
                        readingState_.current_frame_length = Short.MAX_VALUE;
                    }

                }

            }

            Log.d(TAG, "AudioStreamer frame processor was interrupted; checking for last audio bundle...");
            if (bundler_.getCurrentBundleSize() > 0) {
                Log.d(TAG, "Detected a leftover audio bundle with " + bundler_.getCurrentBundleSize() + " frames.");
                byte[] audioBundle = bundler_.getCurrentBundle();

                Log.d(TAG, "Contents of last full audio bundle: " + Helpers.bytesToHex(audioBundle));

                Data audioPacket = packetizer_.generateAudioDataPacket(new Name("/test/data"), audioBundle, true, 0);

                outputQueue_.add(audioPacket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        Log.d(TAG,"AudioStreamer frame processor stopped.");

    }

    private class MediaRecorderThread implements Runnable {

        private static final String TAG = "AudioStreamer_MediaRecorderThread";

        private FileDescriptor ofs_;
        private Thread t_;
        private MediaRecorder recorder_;

        public MediaRecorderThread(FileDescriptor ofs) {
            ofs_ = ofs;
        }

        public void start() {
            if (t_ == null) {
                t_ = new Thread(this);
                t_.start();
            }
        }

        public void stop() {
            if (t_ != null) {
                recorder_.stop();
                recorder_.release();
                recorder_ = null;
                Log.d(TAG, "Recording stopped.");
                t_.interrupt();
                try {
                    t_.join();
                } catch (InterruptedException e) {}
                t_ = null;
            }
        }

        @Override
        public void run() {

            // configure the recorder
            recorder_ = new MediaRecorder();
            recorder_.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder_.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            recorder_.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder_.setAudioChannels(1);
            recorder_.setAudioSamplingRate(8000);
            recorder_.setAudioEncodingBitRate(10000);
            recorder_.setOutputFile(ofs_);

            Log.d(TAG, "Recording started...");
            try {
                recorder_.prepare();
                recorder_.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private class FrameBundler {

        private final static String TAG = "AudioStreamer_FrameBundler";

        // number of frames per audio bundle
        public final static int BUNDLE_SIZE = 10;

        private ArrayList<byte[]> bundle_;
        private int current_bundle_size_; // number of frames in current bundle

        FrameBundler() {
            bundle_ = new ArrayList<byte[]>();
            current_bundle_size_ = 0;
        }

        public int getCurrentBundleSize() {
            return current_bundle_size_;
        }

        public boolean addFrame(byte[] frame) {
            if (current_bundle_size_ == BUNDLE_SIZE)
                return false;

            bundle_.add(frame);
            current_bundle_size_++;

            return true;
        }

        public boolean hasFullBundle() {
            return (current_bundle_size_ == BUNDLE_SIZE);
        }

        public byte[] getCurrentBundle() {
            int bundleLength = 0;
            for (byte[] frame : bundle_) {
                bundleLength += frame.length;
            }
            Log.d(TAG, "Length of audio bundle: " + bundleLength);
            byte[] byte_array_bundle = new byte[bundleLength];
            int current_index = 0;
            for (byte[] frame : bundle_) {
                System.arraycopy(frame, 0, byte_array_bundle, current_index, frame.length);
                current_index += frame.length;
            }

            bundle_.clear();
            current_bundle_size_ = 0;

            return byte_array_bundle;
        }

    }

    private class FramePacketizer {

        private static final String TAG = "AudioStreamer_FramePacketizer";

        public Data generateAudioDataPacket(Name name, byte[] audioBundle, boolean final_block, long seg_num) {
            Data data = new Data(name);
            data.setContent(new Blob(audioBundle));
            // TODO: need to add real signing of data packet
            KeyChain.signWithHmacWithSha256(data, new Blob(Helpers.temp_key));

            MetaInfo metaInfo = new MetaInfo();
            if (final_block) {
                metaInfo.setFinalBlockId(Name.Component.fromSegment(seg_num));
                data.setMetaInfo(metaInfo);
            }

            return data;
        }

    }

}
