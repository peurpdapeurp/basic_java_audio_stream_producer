package com.example.local_udp_sockets_test.audio_pipeline_recording;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class AudioReceiver implements Runnable {

    private static final String TAG = "AudioReceiverRunnable";

    private Context ctx_;
    private InputStream is_;
    private Thread t_;
    private byte[] buffer_;
    AudioReceiverCallbacks callbacks_;

    public interface AudioReceiverCallbacks {
        void onReceivedAudioBundle(byte[] audioBundle);
    }

    public AudioReceiver(Context ctx, InputStream is, AudioReceiverCallbacks callbacks) {
        ctx_ = ctx;
        is_ = is;
        callbacks_ = callbacks;
    }

    public void start() {
        if (t_ == null) {
            t_ = new Thread(this);
            t_.start();
        }
    }

    public void stop() {
        if (t_ != null) {
            t_.interrupt();
            try {
                t_.join();
            } catch (InterruptedException e) {}
            t_ = null;
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {

                int read_size = is_.available();
                if (read_size <= 0) { continue; }

                Log.d(TAG, "Attempting to read " + read_size + " bytes from input stream.");
                buffer_ = new byte[read_size];
                try {
                    int ret = is_.read(buffer_, 0, read_size);
                    if (ret == -1) { break; }
                }
                catch (IOException e) { e.printStackTrace(); }

                // assume that only full audio bundles will be read from input stream, we will never do
                // a partial read of an audio bundle
                callbacks_.onReceivedAudioBundle(buffer_);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
    }
}
