
package com.example.local_udp_sockets_test;

import android.util.Log;

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
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;

public class NetworkThread implements Runnable {

    private final static String TAG = "NetworkThread";

    private Thread t_;
    private Queue inputQueue_;
    Face face_;
    MemoryContentCache mcc_;
    File cacheDir_;

    public NetworkThread(LinkedTransferQueue inputQueue, File cacheDir) {
        inputQueue_ = inputQueue;
        cacheDir_ = cacheDir;
    }

    public void start() {
        if (t_ == null) {
            t_ = new Thread(this);
            t_.start();
        }
    }

    public void stop() {
        if (t_ != null) {
            t_.interrupt();
            try {
                t_.join();
            } catch (InterruptedException e) {}
            t_ = null;
        }
    }

    public void run() {

        Log.d(TAG,"NetworkThread started.");

        try {

            // set up keychain
            KeyChain keyChain = configureKeyChain();

            // set up face
            face_ = new Face();
            try {
                face_.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            // set up memory content cache
            mcc_ = new MemoryContentCache(face_);
            mcc_.registerPrefix(new Name("/a"),
                    new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {
                            Log.d(TAG, "Prefix registration for " + prefix + " failed.");
                        }
                    },
                    mcc_.getStorePendingInterest()
            );

            while (!Thread.interrupted()) {
                if (inputQueue_.size() != 0) {
                    Data data = (Data) inputQueue_.poll();
                    Log.d(TAG, "NetworkThread received data packet." + "\n" +
                            "Name: " + data.getName() + "\n" +
                            "FinalBlockId: " + data.getMetaInfo().getFinalBlockId().getValue().toHex());
                    mcc_.add(data);

//                    // write the audio packets to the app's external cache for testing purposes
//                    Helpers.writeHexStringToFile(cacheDir_.getAbsolutePath() + "/" + data.getName().get(-1).toNumber() + ".aac",
//                                                Helpers.bytesToHex(data.getContent().getImmutableArray()));
                }
                face_.processEvents();
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EncodingException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        Log.d(TAG,"NetworkThread stopped.");

    }

    // taken from https://github.com/named-data-mobile/NFD-android/blob/4a20a88fb288403c6776f81c1d117cfc7fced122/app/src/main/java/net/named_data/nfd/utils/NfdcHelper.java
    private static KeyChain configureKeyChain() {

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
            e.printStackTrace();
        }

        return keyChain;
    }

}
