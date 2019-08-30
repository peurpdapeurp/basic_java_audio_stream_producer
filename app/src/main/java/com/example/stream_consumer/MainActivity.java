
package com.example.stream_consumer;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.named_data.jndn.Name;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    Button recordButton_;
    Button generateRandomIdButton_;
    EditText streamNameInput_;
    EditText streamIdInput_;
    EditText framesPerSegmentInput_;
    EditText producerSamplingRateInput_;
    StreamProducer streamer_;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        streamNameInput_ = (EditText) findViewById(R.id.stream_name_input);
        streamIdInput_ = (EditText) findViewById(R.id.stream_id_input);
        framesPerSegmentInput_ = (EditText) findViewById(R.id.frames_per_segment_input);
        producerSamplingRateInput_ = (EditText) findViewById(R.id.producer_sampling_rate_input);

        streamer_ = new StreamProducer(this, new StreamProducer.Options(
                Long.parseLong(framesPerSegmentInput_.getText().toString()),
                Integer.parseInt(producerSamplingRateInput_.getText().toString())
        ));

        recordButton_ = (Button) findViewById(R.id.record_button);
        recordButton_.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        streamer_.start(new Name(getString(R.string.network_prefix))
                                            .append(streamNameInput_.getText().toString())
                                            .append(streamIdInput_.getText().toString())
                                            .appendVersion(0),
                                        Integer.parseInt(framesPerSegmentInput_.getText().toString()));
                        return true;
                    case MotionEvent.ACTION_UP:
                        streamer_.stop();
                        return true;
                }
                return false;
            }
        });

        generateRandomIdButton_ = (Button) findViewById(R.id.generate_random_id_button);
        generateRandomIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Helpers.getRandomLongBetweenRange(0, 10000)));
            }
        });

    }
}