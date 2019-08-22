
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    Button recordButton_;
    Button generateRandomIdButton_;
    Button clearLogButton_;
    TextView uiLog_;
    EditText streamNameInput_;
    EditText streamIdInput_;
    AudioStreamer streamer_;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        uiLog_ = (TextView) findViewById(R.id.ui_log);

        streamer_ = new AudioStreamer(this, new AudioStreamer.Callbacks() {
            @Override
            public void onAudioPacket(final Data audioPacket) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uiLog_.append("---" + "Time: " + System.currentTimeMillis() + "---" + "\n" +
                                "Network thread received audio data packet." + "\n" +
                                "Name: " + audioPacket.getName().toUri() + "\n" +
                                "\n");
                    }
                });
            }
        });

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

        clearLogButton_ = (Button) findViewById(R.id.clear_log_button);
        clearLogButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uiLog_.setText("");
            }
        });

        generateRandomIdButton_ = (Button) findViewById(R.id.generate_id_button);
        generateRandomIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Helpers.getRandomLongBetweenRange(0, 10000)));
            }
        });

    }
}