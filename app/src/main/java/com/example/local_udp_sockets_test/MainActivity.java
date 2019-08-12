
package com.example.local_udp_sockets_test;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.local_udp_sockets_test.audio_pipeline_recording.AACADTSFramePacketizer;
import com.example.local_udp_sockets_test.audio_pipeline_recording.AudioReceiver;
import com.example.local_udp_sockets_test.audio_pipeline_recording.AudioRecorder;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.util.MemoryContentCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Button button_;
    int currentBundleNum_ = 0;
    long currentSegmentNum_ = 0;
    Face face_;
    MemoryContentCache mmc_;
    FileInputStream arecv_is_;
    FileOutputStream arec_os_;
    AudioReceiver arecv_;
    AudioRecorder arec_;

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

        ParcelFileDescriptor[] mParcelFileDescriptors = null;
        ParcelFileDescriptor mParcelRead;
        ParcelFileDescriptor mParcelWrite;

        // create an array of parcel file descriptors
        try {
            mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        arecv_is_ = new FileInputStream(mParcelRead.getFileDescriptor());
        arec_os_ = new FileOutputStream(mParcelWrite.getFileDescriptor());

        arecv_ = new AudioReceiver(this, arecv_is_, new AudioReceiver.AudioReceiverCallbacks() {
            @Override
            public void onReceivedAudioBundle(byte[] audioBundle) {
                Log.d(TAG, "onReceivedAudioBundle was triggered in MainActivity.");

                Name dataName = new Name("/test/audio");
                dataName.appendVersion(1);
                dataName.appendSegment(currentSegmentNum_);
                boolean final_block = currentSegmentNum_ == 3 ? true : false;
                Data audioPacket = AACADTSFramePacketizer.generateAudioDataPacket(dataName,
                                                                                  audioBundle,
                                                                                  final_block,
                                                                                  currentSegmentNum_);

                Log.d(TAG, "Name of audio data packet: " + audioPacket.getName());
                Log.d(TAG, "Contents of audio data packet: " + Helpers.bytesToHex(audioPacket.getContent().getImmutableArray()));

                mmc_.add(audioPacket);

                currentSegmentNum_++;
            }
        });
        arecv_.start();

        arec_ = new AudioRecorder(this, arec_os_);
        arec_.start();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            arecv_is_.close();
            arec_os_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}