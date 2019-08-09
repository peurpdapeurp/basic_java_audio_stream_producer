package com.example.local_udp_sockets_test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import android.os.SystemClock;
import android.util.Log;

/**
 *
 *   RFC 3640.
 *
 *   This packetizer must be fed with an InputStream containing ADTS AAC.
 *   AAC will basically be rewrapped in an RTP stream and sent over the network.
 *   This packetizer only implements the aac-hbr mode (High Bit-rate AAC) and
 *   each packet only carry a single and complete AAC access unit.
 *
 */
public class AACADTSPacketizer extends AbstractPacketizer implements Runnable {

    private final static String TAG = "AACADTSPacketizer";

    private final static int MAXPACKETSIZE = 500;

    /** There are 13 supported frequencies by ADTS. **/
    public static final int[] AUDIO_SAMPLING_RATES = {
            96000, // 0
            88200, // 1
            64000, // 2
            48000, // 3
            44100, // 4
            32000, // 5
            24000, // 6
            22050, // 7
            16000, // 8
            12000, // 9
            11025, // 10
            8000,  // 11
            7350,  // 12
            -1,   // 13
            -1,   // 14
            -1,   // 15
    };

    private Thread t;
    private int samplingRate = 8000;

    public AACADTSPacketizer(File cacheDir) {
        super(cacheDir);
    }

    public void start() {
        if (t==null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        if (t != null) {
            try {
                is.close();
            } catch (IOException ignore) {}
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {}
            t = null;
        }
    }

    public void setSamplingRate(int samplingRate) {
        this.samplingRate = samplingRate;
    }

    public void run() {

        Log.d(TAG,"AAC ADTS packetizer started !");

        // "A packet SHALL carry either one or more complete Access Units, or a
        // single fragment of an Access Unit.  Fragments of the same Access Unit
        // have the same time stamp but different RTP sequence numbers.  The
        // marker bit in the RTP header is 1 on the last fragment of an Access
        // Unit, and 0 on all other fragments." RFC 3640

        // ADTS header fields that we need to parse
        boolean protection;
        int frameLength, sum, length, nbau, nbpk, samplingRateIndex, profile;
        long oldtime = SystemClock.elapsedRealtime(), now = oldtime;
        byte[] header = new byte[8];

        try {
            while (!Thread.interrupted()) {

                // Synchronisation: ADTS packet starts with 12bits set to 1
                while (true) {
                    if ( (is.read()&0xFF) == 0xFF ) {
                        header[1] = (byte) is.read();
                        if ( (header[1]&0xF0) == 0xF0) break;
                    }
                }

                // Parse adts header (ADTS packets start with a 7 or 9 byte long header)
                fill(header, 2, 5);

                // The protection bit indicates whether or not the header contains the two extra bytes
                protection = (header[1]&0x01)>0 ? true : false;
                frameLength = (header[3]&0x03) << 11 |
                        (header[4]&0xFF) << 3 |
                        (header[5]&0xFF) >> 5 ;
                frameLength -= (protection ? 7 : 9);

                // Number of AAC frames in the ADTS frame
                nbau = (header[6]&0x03) + 1;

                // The number of RTP packets that will be sent for this ADTS frame
                nbpk = frameLength/MAXPACKETSIZE + 1;

                // Read CRS if any
                if (!protection) is.read(header,0,2);

                samplingRate = AUDIO_SAMPLING_RATES[(header[2]&0x3C) >> 2];
                profile = ( (header[2]&0xC0) >> 6 ) + 1 ;

                // We update the RTP timestamp
                ts +=  1024L*1000000000L/samplingRate; //stats.average();

                //Log.d(TAG,"frameLength: "+frameLength+" protection: "+protection+" p: "+profile+" sr: "+samplingRate);

                sum = 0;
                while (sum < frameLength) {

                    buffer = new byte[500];

                    // Read frame
                    if (frameLength-sum > MAXPACKETSIZE-4) {
                        length = MAXPACKETSIZE-4;
                    }
                    else {
                        length = frameLength-sum;
                    }
                    sum += length;
                    fill(buffer, 4, length);

                    // AU-headers-length field: contains the size in bits of a AU-header
                    // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
                    // 13 bits will be enough because ADTS uses 13 bits for frame length
                    buffer[0] = 0;
                    buffer[1] = 0x10;

                    // AU-size
                    buffer[2] = (byte) (frameLength>>5);
                    buffer[3] = (byte) (frameLength<<3);

                    // AU-Index
                    buffer[3] &= 0xF8;
                    buffer[3] |= 0x00;

                    File tempAudioFile = File.createTempFile("temp", ".wav", cacheDir);
                    tempAudioFile.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempAudioFile);
                    fos.write(buffer);
                    fos.close();

                }

            }
        } catch (IOException e) {
            // Ignore
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG,"ArrayIndexOutOfBoundsException: "+(e.getMessage()!=null?e.getMessage():"unknown error"));
            e.printStackTrace();
        }

        Log.d(TAG,"AAC ADTS packetizer stopped !");

    }

    private int fill(byte[] buffer, int offset,int length) throws IOException {

        int sum = 0, len;
        while (sum<length) {
            len = is.read(buffer, offset+sum, length-sum);
            if (len<0) {
                throw new IOException("End of stream");
            }
            else sum+=len;
        }

        Log.d(TAG, "Contents of filled buffer from stream: " + Arrays.toString(buffer));

        return sum;

    }

}
