package com.example.local_udp_sockets_test.audio_pipeline_recording;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioRecorder implements Runnable {

    private static final String TAG = "AudioRecorder";

    private Context ctx_;
    private OutputStream os_;
    private Thread t_;
    private MediaRecorder recorder_;
    private AACADTSFrameProcessor processor_;

    public AudioRecorder(Context ctx, OutputStream os) {
        ctx_ = ctx;
        os_ = os;
        processor_ = new AACADTSFrameProcessor(ctx, ctx.getExternalCacheDir());
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
            processor_.stop();
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
        ParcelFileDescriptor[] mParcelFileDescriptors = null;
        ParcelFileDescriptor mParcelRead;
        ParcelFileDescriptor mParcelWrite;

        // create an array of parcel file descriptors
        try {
            mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        // configure the recorder
        recorder_ = new MediaRecorder();
        recorder_.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder_.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        recorder_.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder_.setAudioChannels(1);
        recorder_.setAudioSamplingRate(8000);
        recorder_.setAudioEncodingBitRate(10000);
        recorder_.setOutputFile(mParcelWrite.getFileDescriptor());

        Log.d(TAG, "Recording started...");
        try {
            recorder_.prepare();
            recorder_.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);

        AACADTSFrameProcessor mProcessor = new AACADTSFrameProcessor(ctx_, ctx_.getExternalCacheDir());

        // set the packetizer to read the MediaRecorder's stream and packetize its ADTS frames
        mProcessor.setInputStream(is);
        mProcessor.setOutputStream(os_);
        mProcessor.start();
    }
}
