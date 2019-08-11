
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ParcelFileDescriptor[] mParcelFileDescriptors = null;
        ParcelFileDescriptor mParcelRead;
        ParcelFileDescriptor mParcelWrite;

        AACADTSPacketizer mPacketizer = new AACADTSPacketizer(getExternalCacheDir());

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
        recorder.setAudioEncodingBitRate(32000);
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
        Log.d(TAG, "Recording stopped.");

    }

}