
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.local_udp_sockets_test.audio_pipeline_recording.AACADTSFramePacketizer;
import com.example.local_udp_sockets_test.audio_pipeline_recording.AACADTSFrameProcessor;
import com.example.local_udp_sockets_test.audio_pipeline_recording.AudioRecordingRunnable;
import com.example.local_udp_sockets_test.helpers.InterModuleInfo;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.pib.Pib;
import net.named_data.jndn.security.pib.PibImpl;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.security.tpm.Tpm;
import net.named_data.jndn.security.tpm.TpmBackEnd;
import net.named_data.jndn.util.MemoryContentCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Button button_;
    int currentBundleNum_ = 0;
    long currentSegmentNum_ = 0;
    Face face_;
    MemoryContentCache mmc_;

    BroadcastReceiver AACADTSFrameProcessorListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Name dataName = new Name("/test/audio");
            dataName.appendVersion(1);
            dataName.appendSegment(currentSegmentNum_);
            boolean final_block = currentSegmentNum_ == 3 ? true : false;
            Data audioPacket = AACADTSFramePacketizer.generateAudioDataPacket(dataName,
                    intent.getByteArrayExtra(InterModuleInfo.AAC_ADTS_Frame_Processor_EXTRA_AUDIO_BUNDLE_ARRAY),
                    final_block, currentSegmentNum_);

            Log.d(TAG, "Name of audio data packet: " + audioPacket.getName());
            Log.d(TAG, "Contents of audio data packet: " + Helpers.bytesToHex(audioPacket.getContent().getImmutableArray()));

            mmc_.add(audioPacket);

            currentSegmentNum_++;

//            try {
//                File audioFile = new File(getExternalCacheDir().getAbsolutePath() + "/" + currentBundleNum_ + ".aac");
//                FileOutputStream os = new FileOutputStream(audioFile);
//                os.write(audioPacket.getContent().getImmutableArray());
//                os.close();
//                currentBundleNum_++;
//            }
//            catch (IOException e) { e.printStackTrace(); }

        }
    };

    private static KeyChain
    configureKeyChain() {
        final MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        final MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        final KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, privateKeyStorage),
                new SelfVerifyPolicyManager(identityStorage));

        Name name = new Name("/tmp-identity");

        try {
            // create keys, certs if necessary
            if (!identityStorage.doesIdentityExist(name)) {
                keyChain.createIdentityAndCertificate(name);

                // set default identity
                keyChain.getIdentityManager().setDefaultIdentity(name);
            }
        }
        catch (SecurityException e){
            // shouldn't really happen
            /// @todo add logging
        }

        return keyChain;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SetUpMMCTask task = new SetUpMMCTask();
        task.execute();

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

        LocalBroadcastManager.getInstance(this).registerReceiver(AACADTSFrameProcessorListener,
                                                                    AACADTSFrameProcessor.getIntentFilter());

        Thread audioRecordingThread = new Thread(new AudioRecordingRunnable(this));
        audioRecordingThread.run();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(AACADTSFrameProcessorListener);
    }

    private class SetUpMMCTask extends AsyncTask
    {
        @Override
        protected Object doInBackground(Object[] objects) {
            KeyChain keyChain = configureKeyChain();
            face_ = new Face();
            try {
                face_.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            mmc_ = new MemoryContentCache(face_);

            try {
                mmc_.registerPrefix(new Name("/test/audio"),
                    new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {
                            Log.d(TAG, "Prefix registration for " + prefix + " failed.");
                        }
                    },
                    mmc_.getStorePendingInterest()
            );
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            while (true) {
                try {
                    face_.processEvents();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (EncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);

            Log.d(TAG, "Finished setting up MMC.");
        }
    }
}