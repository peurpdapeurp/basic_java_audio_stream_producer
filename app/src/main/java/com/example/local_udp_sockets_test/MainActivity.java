
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.LinkedTransferQueue;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Button button_;
    AudioStreamer arec_;
    NetworkThread net_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button_ = (Button) findViewById(R.id.button);
        button_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final MediaPlayer player = new MediaPlayer();
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        try {
                            player.release();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                try {
                    File audioBundleFile = new File(getExternalCacheDir().getAbsolutePath() + "/1.aac");
                    player.setDataSource(audioBundleFile.getAbsolutePath());
                    player.prepare();
                    player.start();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        LinkedTransferQueue streamToNetworkQueue = new LinkedTransferQueue();

        arec_ = new AudioStreamer(streamToNetworkQueue);
        arec_.start();

        net_ = new NetworkThread(streamToNetworkQueue);
        net_.start();

    }
}