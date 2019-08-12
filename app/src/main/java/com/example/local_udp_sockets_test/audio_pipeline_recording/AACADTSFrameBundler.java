package com.example.local_udp_sockets_test.audio_pipeline_recording;

import android.util.Log;

import java.util.ArrayList;

public class AACADTSFrameBundler {

    private final static String TAG = "AACADTSFrameBundler";

    // number of ADTS frames per audio bundle
    public final static int BUNDLE_SIZE = 10;

    private ArrayList<byte[]> bundle_;
    private int current_bundle_size_; // number of frames in current bundle

    AACADTSFrameBundler() {
        bundle_ = new ArrayList<byte[]>();
        current_bundle_size_ = 0;
    }

    public int getCurrentBundleSize() {
        return current_bundle_size_;
    }

    public boolean addFrame(byte[] frame) {
        if (current_bundle_size_ == BUNDLE_SIZE)
            return false;

        bundle_.add(frame);
        current_bundle_size_++;

        return true;
    }

    public boolean hasFullBundle() {
        return (current_bundle_size_ == BUNDLE_SIZE);
    }

    public byte[] getCurrentBundle() {
        int bundleLength = 0;
        for (byte[] frame : bundle_) {
            bundleLength += frame.length;
        }
        Log.d(TAG, "Length of audio bundle: " + bundleLength);
        byte[] byte_array_bundle = new byte[bundleLength];
        int current_index = 0;
        for (byte[] frame : bundle_) {
            System.arraycopy(frame, 0, byte_array_bundle, current_index, frame.length);
            current_index += frame.length;
        }

        bundle_.clear();
        current_bundle_size_ = 0;

        return byte_array_bundle;
    }

}
