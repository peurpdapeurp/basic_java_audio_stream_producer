package com.example.local_udp_sockets_test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.util.Log;

/**
 *
 *   RFC 3267.
 *
 *   AMR Streaming over RTP.
 *
 *   Must be fed with an InputStream containing raw AMR NB
 *   Stream must begin with a 6 bytes long header: "#!AMR\n", it will be skipped
 *
 */
public class AMRNBPacketizer extends AbstractPacketizer implements Runnable {

    public final static String TAG = "AMRNBPacketizer";

    private final int AMR_HEADER_LENGTH = 6; // "#!AMR\n"
    private static final int AMR_FRAME_HEADER_LENGTH = 1; // Each frame has a short header
    private static final int[] sFrameBits = {95, 103, 118, 134, 148, 159, 204, 244};
    private int samplingRate = 8000;

    private Thread t;

    public AMRNBPacketizer(File cacheDir) {
        super(cacheDir);
    }

    public void start() {
        if (t==null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        if (t != null) {
            try {
                is.close();
            } catch (IOException ignore) {}
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {}
            t = null;
        }
    }

    public void run() {

        int frameLength, frameType;
        long now = System.nanoTime(), oldtime = now;
        byte[] header = new byte[AMR_HEADER_LENGTH];

        try {

            // Skip raw AMR header
            fill(header,0,AMR_HEADER_LENGTH);

            if (header[5] != '\n') {
                Log.e(TAG,"Bad header ! AMR not correcty supported by the phone !");
                return;
            }

            while (!Thread.interrupted()) {

                buffer = new byte[32];

                // First we read the frame header
                fill(buffer, 0, AMR_FRAME_HEADER_LENGTH);

                // Then we calculate the frame payload length
                frameType = (Math.abs(buffer[0]) >> 3) & 0x0f;
                frameLength = (sFrameBits[frameType]+7)/8;

                // And we read the payload
                fill(buffer, 1, frameLength);

                Log.d(TAG,"Frame length: " + frameLength + " frameType: " + frameType);

                // RFC 3267 Page 14: "For AMR, the sampling frequency is 8 kHz"
                // FIXME: Is this really always the case ??
                ts += 160L*1000000000L/samplingRate;

            }

        } catch (IOException e) {
            Log.d(TAG, "IOException: " + e.getMessage());
        }

        Log.d(TAG,"AMR packetizer stopped !");

    }

    private int fill(byte[] buffer, int offset,int length) throws IOException {

        int sum = 0, len;
        while (sum < length) {
            len = is.read(buffer, offset+sum, length-sum);
            if (len<0) {
                throw new IOException("End of stream");
            }
            else sum+=len;
        }

        Log.d(TAG, "Contents of filled buffer from stream: " + Arrays.toString(buffer));

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                samplingRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                32,
                AudioTrack.MODE_STREAM);

        audioTrack.play();

        MediaCodec decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        MediaFormat format = new MediaFormat();

        audioTrack.write(buffer, 0, 32);

        audioTrack.release();

        return sum;

    }


}