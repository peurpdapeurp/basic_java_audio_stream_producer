
package com.example.stream_consumer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.named_data.jndn.Name;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    Button generateRandomIdButton_;
    EditText streamNameInput_;
    EditText streamIdInput_;
    EditText framesPerSegmentInput_;
    EditText producerSamplingRateInput_;
    StreamProducer streamer_;

    BroadcastReceiver pttPressListener_= new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_DOWN)) {
                streamer_.start(new Name(getString(R.string.network_prefix))
                                .append(streamNameInput_.getText().toString())
                                .append(streamIdInput_.getText().toString())
                                .appendVersion(0),
                        Integer.parseInt(framesPerSegmentInput_.getText().toString()));
            } else if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_UP)) {
                streamer_.stop();
            } else {
                Log.w(TAG, "pttPressListener got weird intent with action: " +
                        intent.getAction());
            }
        }
    };

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

        generateRandomIdButton_ = (Button) findViewById(R.id.generate_random_id_button);
        generateRandomIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Helpers.getRandomLongBetweenRange(0, 10000)));
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(pttPressListener_,
                PTTButtonPressReceiver.getIntentFilter());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttPressListener_);
    }
}