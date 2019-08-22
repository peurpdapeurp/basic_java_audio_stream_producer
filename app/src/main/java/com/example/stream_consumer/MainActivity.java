
package com.example.stream_consumer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.named_data.jndn.Data;
import net.named_data.jndn.Name;

import java.util.concurrent.LinkedTransferQueue;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    Button recordButton_;
    TextView uiLog_;
    EditText streamNameInput_;
    EditText streamIdInput_;
    AudioStreamer streamer_;
    NetworkThread net_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinkedTransferQueue streamToNetworkQueue = new LinkedTransferQueue();

        uiLog_ = (TextView) findViewById(R.id.ui_log);

        net_ = new NetworkThread(streamToNetworkQueue, new Name(getString(R.string.network_prefix)), getExternalCacheDir(),
                new NetworkThread.Callbacks() {
                    @Override
                    public void onAudioPacket(final Data audioPacket) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                uiLog_.append("---" + "Time: " + System.currentTimeMillis() + "---" + "\n" +
                                        "Published audio data packet." + "\n" +
                                        "Name: " + audioPacket.getName() + "\n" +
                                        "\n");
                            }
                        });
                    }
                });
        net_.start();

        streamer_ = new AudioStreamer(streamToNetworkQueue);

        streamNameInput_ = (EditText) findViewById(R.id.stream_name_input);
        streamIdInput_ = (EditText) findViewById(R.id.stream_id_input);

        recordButton_ = (Button) findViewById(R.id.record_button);
        recordButton_.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        streamer_.start(new Name(getString(R.string.network_prefix))
                                            .append(streamNameInput_.getText().toString())
                                            .append(streamIdInput_.getText().toString())
                                            .appendVersion(0));
                        return true;
                    case MotionEvent.ACTION_UP:
                        streamer_.stop();
                        return true;
                }
                return false;
            }
        });

    }
}