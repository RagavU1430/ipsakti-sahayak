package com.ipsakti.ip_sakti_backend.voice.provider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PcmWaveEncoder {
    private PcmWaveEncoder() {}

    static byte[] mono16Bit24Khz(byte[] pcm) {
        int dataSize = pcm.length;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + dataSize);
        wav.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short) 1);
        wav.putShort((short) 1).putInt(24000).putInt(48000).putShort((short) 2).putShort((short) 16);
        wav.put(new byte[]{'d','a','t','a'}).putInt(dataSize).put(pcm);
        return wav.array();
    }
}
