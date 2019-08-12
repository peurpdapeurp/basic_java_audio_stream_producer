package com.example.local_udp_sockets_test.audio_pipeline_recording;

import com.example.local_udp_sockets_test.Helpers;

import net.named_data.jndn.Data;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.util.Blob;

public class AACADTSFramePacketizer {

    private static final String TAG = "AACADTSFramePacketizer";

    public static Data generateAudioDataPacket(Name name, byte[] audioBundle) {
        Data data = new Data(name);
        data.setContent(new Blob(audioBundle));
        // TODO: need to add real signing of data packet
        KeyChain.signWithHmacWithSha256(data, new Blob(Helpers.temp_key));

        return data;
    }

}
