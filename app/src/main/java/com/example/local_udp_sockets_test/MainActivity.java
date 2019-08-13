
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import net.named_data.jndn.Name;

import java.util.concurrent.LinkedTransferQueue;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    AudioStreamer streamer_;
    NetworkThread net_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinkedTransferQueue streamToNetworkQueue = new LinkedTransferQueue();

        net_ = new NetworkThread(streamToNetworkQueue, getExternalCacheDir());
        net_.start();

        streamer_ = new AudioStreamer(streamToNetworkQueue);
        streamer_.start(new Name("/a/%FD%01"));

        try { Thread.sleep(5000); } catch (InterruptedException e) { e.printStackTrace(); }

        streamer_.stop();

        streamer_.start(new Name("/a/%FD%02"));

        try { Thread.sleep(5000); } catch (InterruptedException e) { e.printStackTrace(); }

        streamer_.stop();

    }
}