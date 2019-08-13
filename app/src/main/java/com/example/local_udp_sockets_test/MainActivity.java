
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import java.util.concurrent.LinkedTransferQueue;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    AudioStreamer arec_;
    NetworkThread net_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinkedTransferQueue streamToNetworkQueue = new LinkedTransferQueue();

        net_ = new NetworkThread(streamToNetworkQueue);
        net_.start();

        arec_ = new AudioStreamer(streamToNetworkQueue);
        arec_.start();

        try { Thread.sleep(1400); } catch (InterruptedException e) { e.printStackTrace(); }

        arec_.stop();

    }
}