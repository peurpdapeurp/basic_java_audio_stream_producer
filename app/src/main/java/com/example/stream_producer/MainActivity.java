
package com.example.stream_producer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.stream_producer.custom_progress_bar.CustomProgressBar;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Private constants
    public static final int RECORDING_DELAY_MS = 3000;

    // Messages from Stream Producer
    public static final int MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED = 0;
    public static final int MSG_STREAM_PRODUCER_FINAL_SEGMENT_RECORDED = 1;

    CustomProgressBar publishingProgressBar_;
    TextView publishingProgressBarLabel_;
    Button incrementIdButton_;
    EditText streamNameInput_;
    EditText streamIdInput_;
    EditText framesPerSegmentInput_;
    EditText producerSamplingRateInput_;
    StreamProducer streamer_;
    HashMap<Name, StreamState> streamStates_;
    Handler handler_;
    BroadcastReceiver pttPressListener_;
    Name currentStreamName_;
    boolean recordingDisabled_ = false;
    Context ctx_;

    public static class UiEventInfo {
        public UiEventInfo(Name streamName, long arg1) {
            this.streamName = streamName;
            this.arg1 = arg1;
        }
        private Name streamName;
        private long arg1;
    }

    private class StreamState {
        // Public constants
        private static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        private static final int NO_SEGMENTS_PUBLISHED = -1;

        private StreamState() {
        }

        private long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
        private long numSegsPublished = 0;
        private long highestSegPublished = NO_SEGMENTS_PUBLISHED;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx_ = this;

        streamStates_ = new HashMap<>();

        handler_ = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                UiEventInfo uiEventInfo = (UiEventInfo) msg.obj;
                Name streamName = uiEventInfo.streamName;
                StreamState streamState = streamStates_.get(streamName);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg " + msg.what + " from stream name " +
                            streamName.toString());
                    return;
                }

                switch (msg.what) {
                    case MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED: {
                        streamState.numSegsPublished++;
                        long segNum = uiEventInfo.arg1;
                        if (segNum > streamState.highestSegPublished) {
                            streamState.highestSegPublished = segNum;
                        }
                        updatePublishingProgressBar(segNum, streamState);
                        updatePublishingProgressBarLabel(streamState);
                        break;
                    }
                    case MSG_STREAM_PRODUCER_FINAL_SEGMENT_RECORDED: {
                        streamState.finalBlockId = uiEventInfo.arg1;
                        updatePublishingProgressBar(streamState);
                        updatePublishingProgressBarLabel(streamState);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg " + msg.what);
                    }
                }
            }
        };

        pttPressListener_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_DOWN)) {
                    if (currentStreamName_ != null) {
                        streamStates_.remove(currentStreamName_);
                        currentStreamName_ = null;
                    }
                    publishingProgressBar_.reset();
                    currentStreamName_ = new Name(getString(R.string.network_prefix))
                            .append(streamNameInput_.getText().toString())
                            .append(streamIdInput_.getText().toString())
                            .appendVersion(0);
                    streamer_.start(currentStreamName_,
                            Integer.parseInt(framesPerSegmentInput_.getText().toString()));
                    streamStates_.put(currentStreamName_, new StreamState());
                } else if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_UP)) {
                    streamer_.stop();
                } else {
                    Log.w(TAG, "pttPressListener got weird intent with action: " +
                            intent.getAction());
                }
            }
        };

        streamNameInput_ = (EditText) findViewById(R.id.stream_name_input);
        streamIdInput_ = (EditText) findViewById(R.id.stream_id_input);
        framesPerSegmentInput_ = (EditText) findViewById(R.id.frames_per_segment_input);
        producerSamplingRateInput_ = (EditText) findViewById(R.id.producer_sampling_rate_input);

        streamer_ = new StreamProducer(this, handler_, new StreamProducer.Options(
                Long.parseLong(framesPerSegmentInput_.getText().toString()),
                Integer.parseInt(producerSamplingRateInput_.getText().toString())
        ));

        incrementIdButton_ = (Button) findViewById(R.id.increment_id_button);
        incrementIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Long.parseLong(streamIdInput_.getText().toString()) + 1));
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(pttPressListener_,
                PTTButtonPressReceiver.getIntentFilter());

        publishingProgressBar_ = (CustomProgressBar) findViewById(R.id.publishing_progress_bar);
        publishingProgressBar_.getThumb().setAlpha(0);
        publishingProgressBar_.init();
        publishingProgressBarLabel_ = (TextView) findViewById(R.id.publishing_progress_bar_label);
        initPublishingProgressBarLabel();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttPressListener_);
    }

    private void updatePublishingProgressBar(StreamState streamState) {
        boolean finalBlockIdKnown = streamState.finalBlockId != StreamState.FINAL_BLOCK_ID_UNKNOWN;
        if (finalBlockIdKnown) {
            publishingProgressBar_.setTotalSegments((int) streamState.finalBlockId + 1);
        }
        if (!finalBlockIdKnown &&
                ((float) streamState.highestSegPublished / (float) publishingProgressBar_.getTotalSegments()) > 0.90f) {
            publishingProgressBar_.setTotalSegments(publishingProgressBar_.getTotalSegments() * 2);
        }
    }

    private void updatePublishingProgressBar(long segNum, StreamState streamState) {
        updatePublishingProgressBar(streamState);
        publishingProgressBar_.updateSingleSegmentColor((int) segNum, R.color.green);
    }

    private void initPublishingProgressBarLabel() {
        String newProductionProgressBarLabel =
                getString(R.string.publishing_progress_bar_label) + "\n" + "(" +
                        "published " + "?" + ", " +
                        "total " + "?" +
                        ")";
        publishingProgressBarLabel_.setText(newProductionProgressBarLabel);
    }

    private void updatePublishingProgressBarLabel(StreamState streamState) {
        String label =
                getString(R.string.publishing_progress_bar_label) + "\n" + "(" +
                        "published " + streamState.numSegsPublished + ", " +
                        "total " +
                            ((streamState.finalBlockId == StreamState.FINAL_BLOCK_ID_UNKNOWN) ?
                                    "unknown" : streamState.finalBlockId) +
                        ")";
        publishingProgressBarLabel_.setText(label);
    }
}