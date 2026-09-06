package com.ipsakti.ip_sakti_backend.voice;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.provider.SpeechToTextProvider;
import com.ipsakti.ip_sakti_backend.voice.provider.TextToSpeechProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class VoiceV2ControllerTest {
    @Mock VoiceService service;
    @Mock SpeechToTextProvider stt;
    @Mock TextToSpeechProvider tts;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new VoiceController(service, new VoiceProperties(), stt, tts)).build();
    }

    @Test void healthReportsBothProviders() throws Exception {
        when(stt.isConfigured()).thenReturn(true); when(tts.isConfigured()).thenReturn(true);
        when(stt.providerName()).thenReturn("stt"); when(tts.providerName()).thenReturn("tts");
        mvc.perform(get("/api/v1/voice/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.sttConfigured").value(true))
                .andExpect(jsonPath("$.ttsConfigured").value(true));
    }

    @Test void rejectsInvalidLanguage() throws Exception {
        var audio = new MockMultipartFile("audio", "speech.wav", "audio/wav", new byte[]{1,2,3});
        mvc.perform(multipart("/api/v1/voice/ask").file(audio).param("language", "xx"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LANGUAGE"));
    }

    @Test void rejectsEmptyAudio() throws Exception {
        var audio = new MockMultipartFile("audio", "speech.wav", "audio/wav", new byte[0]);
        mvc.perform(multipart("/api/v1/voice/ask").file(audio))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUDIO_EMPTY"));
    }

    @Test void rejectsMissingAudioPart() throws Exception {
        mvc.perform(multipart("/api/v1/voice/ask").param("language", "en"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUDIO_REQUIRED"));
    }
}
