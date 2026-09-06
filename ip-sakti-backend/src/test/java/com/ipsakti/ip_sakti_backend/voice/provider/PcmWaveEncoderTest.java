package com.ipsakti.ip_sakti_backend.voice.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PcmWaveEncoderTest {
    @Test void wrapsPcmInBrowserPlayableWaveContainer() {
        byte[] wav = PcmWaveEncoder.mono16Bit24Khz(new byte[]{1,2,3,4});
        assertThat(new String(wav, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WAVE");
        assertThat(wav).hasSize(48);
    }
}
