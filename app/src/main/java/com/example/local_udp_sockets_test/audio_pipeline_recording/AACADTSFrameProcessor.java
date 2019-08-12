package com.example.local_udp_sockets_test.audio_pipeline_recording;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.local_udp_sockets_test.Helpers;
import com.example.local_udp_sockets_test.helpers.InterModuleInfo;

public class AACADTSFrameProcessor implements Runnable {

    private final static String TAG = "AACADTSFrameProcessor";

    private final static int MAX_READ_SIZE = 2000;

    private Context ctx_;
    private InputStream is_ = null;
    private Thread t_;
    ADTSFrameReadingState readingState_;
    AACADTSFrameBundler bundler_;
    AACADTSFramePacketizer packetizer_;
    File cacheDir_;

    // reference for ADTS header format: https://wiki.multimedia.cx/index.php/ADTS
    private static class ADTSFrameReadingState {
        byte[] buffer = new byte[MAX_READ_SIZE];
        short current_frame_length = Short.MAX_VALUE; // includes ADTS header length
        int current_bytes_read = 0;
    }

    public AACADTSFrameProcessor(Context ctx, File cacheDir) {
        ctx_ = ctx;
        cacheDir_ = cacheDir;
        readingState_ = new ADTSFrameReadingState();
        bundler_ = new AACADTSFrameBundler();
        packetizer_ = new AACADTSFramePacketizer();
    }

    public void setInputStream(InputStream is) {
        this.is_ = is;
    }

    public void start() {
        if (t_ == null) {
            t_ = new Thread(this);
            t_.start();
        }
    }

    public void stop() {
        if (t_ != null) {
            try {
                is_.close();
            } catch (IOException ignore) {}
            t_.interrupt();
            try {
                t_.join();
            } catch (InterruptedException e) {}
            t_ = null;
        }
    }

    public void run() {

        Log.d(TAG,"AAC ADTS frame processor stopped.");

        byte[] final_adts_frame_buffer;

        try {
            while (!Thread.interrupted()) {

                int read_size = is_.available();
                if (read_size > MAX_READ_SIZE) {
                    read_size = MAX_READ_SIZE;
                }
                if (read_size <= 0) { continue; }

                Log.d(TAG, "Attempting to read " + read_size + " bytes from input stream.");
                try {
                    int ret = is_.read(readingState_.buffer,
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
                    // deliver it to the AACADTSFramePacketizer
                    if (bundler_.hasFullBundle()) {
                        Log.d(TAG, "Bundler did have a full bundle, packetizing full audio bundle...");

                        byte[] audioBundle = bundler_.getCurrentBundle();

                        Log.d(TAG, "Contents of full audio bundle: " + Helpers.bytesToHex(audioBundle));

                        Intent i = new Intent(InterModuleInfo.AAC_ADTS_Frame_Processor_AUDIO_BUNDLE_AVAILABLE);
                        i.putExtra(InterModuleInfo.AAC_ADTS_Frame_Processor_EXTRA_AUDIO_BUNDLE_ARRAY,
                                    audioBundle);
                        LocalBroadcastManager.getInstance(ctx_).sendBroadcast(i);
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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "AAC ADTS frame processor was interrupted; checking for last audio bundle...");
        if (bundler_.getCurrentBundleSize() > 0) {
            Log.d(TAG, "Detected a leftover audio bundle with " + bundler_.getCurrentBundleSize() + " frames.");
            byte[] audioBundle = bundler_.getCurrentBundle();

            Log.d(TAG, "Contents of last full audio bundle: " + Helpers.bytesToHex(audioBundle));

            Intent i = new Intent(InterModuleInfo.AAC_ADTS_Frame_Processor_AUDIO_BUNDLE_AVAILABLE);
            i.putExtra(InterModuleInfo.AAC_ADTS_Frame_Processor_EXTRA_AUDIO_BUNDLE_ARRAY,
                    audioBundle);
            LocalBroadcastManager.getInstance(ctx_).sendBroadcast(i);
        }

        Log.d(TAG,"AAC ADTS frame processor stopped.");

    }

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(InterModuleInfo.AAC_ADTS_Frame_Processor_AUDIO_BUNDLE_AVAILABLE);
        return ret;
    }

}
