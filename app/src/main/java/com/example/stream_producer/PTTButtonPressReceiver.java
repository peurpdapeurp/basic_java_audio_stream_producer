package com.example.stream_producer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class PTTButtonPressReceiver extends BroadcastReceiver {

    private final String TAG = "PTTButtonPressReceiver";

    public static String ACTION_PTT_KEY_DOWN = "ACTION_PTT_KEY_DOWN";
    public static String ACTION_PTT_KEY_UP = "ACTION_PTT_KEY_UP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_DOWN")) {
            Intent pttButtonPressedIntent = new Intent(ACTION_PTT_KEY_DOWN);
            LocalBroadcastManager.getInstance(context).sendBroadcast(pttButtonPressedIntent);
        } else if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_UP")) {
            Intent pttButtonPressedIntent = new Intent(ACTION_PTT_KEY_UP);
            LocalBroadcastManager.getInstance(context).sendBroadcast(pttButtonPressedIntent);
        } else {
            Log.d(TAG, "unexpected intent " + intent.getAction());
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(ACTION_PTT_KEY_DOWN);
        ret.addAction(ACTION_PTT_KEY_UP);

        return ret;
    }

}
