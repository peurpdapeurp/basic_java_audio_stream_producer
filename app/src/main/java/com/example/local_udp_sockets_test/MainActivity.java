
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
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

        AMRNBPacketizer mPacketizer = new AMRNBPacketizer(getExternalCacheDir());

        // create an array of parcel file descriptors
        try {
            mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        // create the recorder object to record audio and write it to the local send socket
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        recorder.setOutputFile(mParcelWrite.getFileDescriptor());
        //recorder.setOutputFile(getExternalCacheDir().getAbsolutePath() + "/test.wav");
        recorder.setAudioSamplingRate(8000);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        Log.d(TAG, "Recording started...");
        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);

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

//    Thread streamThread = new Thread(new Runnable(){
//        @Override
//        public void run() {
//            try {
//
//                // create the local send socket for sending data
//                DatagramSocket local_send_socket = new DatagramSocket(
//                        new InetSocketAddress("127.0.0.1", 50004)
//                );
//                Log.d(TAG, "Local send socket created");
//
//                // create the local receive socket for receiving data
//                DatagramSocket local_recv_socket = new DatagramSocket(
//                        new InetSocketAddress("127.0.0.1", 50005)
//                );
//                Log.d(TAG, "Local receive socket created");
//
//                // create the array of data to be sent from the local send socket to the local
//                // receive socket
//                byte[] sendData = new byte[1024];
//                for (int i = 0; i < 1024; i++) {
//                    sendData[i] = (byte) (i % 128);
//                }
//
//                // create the datagram packet to be sent from the local send socket to the
//                // local receive socket
//                InetAddress IPAddress = InetAddress.getByName("localhost");
//                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 50005);
//
//                // get the file descriptor of the local send socket and check its validity
//                ParcelFileDescriptor pfd = ParcelFileDescriptor.fromDatagramSocket(local_send_socket);
//                FileDescriptor local_send_socket_fd = pfd.getFileDescriptor();
//                String fd_validity = (local_send_socket_fd.valid()) ? "valid" : "invalid";
//                Log.d(TAG, "The local send socket's file descriptor was " + fd_validity);
//
//                // create the recorder object to record audio and write it to the local send socket
//                MediaRecorder recorder = new MediaRecorder();
//                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
//                //recorder.setOutputFile(getExternalCacheDir().getAbsolutePath() + "/test.wav");
//                recorder.setOutputFile(local_send_socket_fd);
//                recorder.setAudioSamplingRate(22050);
//                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//
//                // prepare the recorder object for recording
//                try {
//                    recorder.prepare();
//                } catch (IOException e) {
//                    Log.d(TAG, "recorder.prepare() failed: " + e.getMessage());
//                }
//
//                // record audio for five seconds
//                Log.d(TAG, "Started recording...");
//                recorder.start();
//                Thread.sleep(5000);
//                recorder.stop();
//                Log.d(TAG, "Stopped recording.");
//
//                // send the datagram packet from local send socket to local receive socket
//                //local_send_socket.send(sendPacket);
//
//                // create the datagram packet to receive the data from the local send socket
//                byte[] receiveData = new byte[1024];
//                DatagramPacket receivePacket = new DatagramPacket(receiveData, 1024);
//
//                // set the local receive socket to receive data
//                local_recv_socket.receive(receivePacket);
//
//                Log.d(TAG, "Data received by local receive socket: " + Arrays.toString(receiveData));
//
//            } catch (UnknownHostException e) {
//                Log.e(TAG, "UnknownHostException: " + e.getMessage());
//            } catch (IOException e) {
//                Log.e(TAG, "IOException: " + e.getMessage());
//                e.printStackTrace();
//            } catch (InterruptedException e) {
//                Log.e(TAG, "InterruptedException: " + e.getMessage());
//                e.printStackTrace();
//            }
//        }
//    });
//    streamThread.start();
