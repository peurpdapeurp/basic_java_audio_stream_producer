package com.example.local_udp_sockets_test.audio_pipeline_recording;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class AudioRecordingRunnable implements Runnable {

    private static final String TAG = "AudioRecordingRunnable";

    private Context ctx_;

    public AudioRecordingRunnable(Context ctx) {
        ctx_ = ctx;
    }

    @Override
    public void run() {
        ParcelFileDescriptor[] mParcelFileDescriptors = null;
        ParcelFileDescriptor mParcelRead;
        ParcelFileDescriptor mParcelWrite;

        AACADTSFrameProcessor mPacketizer = new AACADTSFrameProcessor(ctx_, ctx_.getExternalCacheDir());

        // create an array of parcel file descriptors
        try {
            mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        // create the recorder object to record audio and stream it to the local socket
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(8000);
        recorder.setAudioEncodingBitRate(10000);
        recorder.setOutputFile(mParcelWrite.getFileDescriptor());

        Log.d(TAG, "Recording started...");
        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);

        // set the packetizer to read the MediaRecorder's stream and packetize its ADTS frames
        mPacketizer.setInputStream(is);
        mPacketizer.start();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        recorder.stop();
        mPacketizer.stop();
        Log.d(TAG, "Recording stopped.");
    }
}
