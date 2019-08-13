
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

        net_ = new NetworkThread(streamToNetworkQueue);
        net_.start();

        streamer_ = new AudioStreamer(streamToNetworkQueue, new Name("/fake_channel"));
        Log.d(TAG, "Streamer initialized, uuid: " + streamer_.getUuid());
        streamer_.start();

        try { Thread.sleep(1400); } catch (InterruptedException e) { e.printStackTrace(); }

        streamer_.stop();

    }
}