package com.example.local_udp_sockets_test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Random;

/**
 *
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
abstract public class AbstractPacketizer {

    protected InputStream is = null;
    protected byte[] buffer;

    protected long ts = 0;

    File cacheDir;

    public AbstractPacketizer(File cacheDir) {
        int ssrc = new Random().nextInt();
        ts = new Random().nextInt();
        this.cacheDir = cacheDir;
    }

    public void setInputStream(InputStream is) {
        this.is = is;
    }

    /** Starts the packetizer. */
    public abstract void start();

    /** Stops the packetizer. */
    public abstract void stop();

    /** For debugging purposes. */
    protected static String printBuffer(byte[] buffer, int start,int end) {
        String str = "";
        for (int i=start;i<end;i++) str += "," + Integer.toHexString(buffer[i]&0xFF);
        return str;
    }

}