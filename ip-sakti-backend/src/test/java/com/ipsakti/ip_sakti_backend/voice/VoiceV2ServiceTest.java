package com.ipsakti.ip_sakti_backend.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizedSpeech;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceTranscript;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import com.ipsakti.ip_sakti_backend.voice.provider.SpeechToTextProvider;
import com.ipsakti.ip_sakti_backend.voice.provider.TextToSpeechProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceV2ServiceTest {
    @Mock SpeechToTextProvider stt;
    @Mock TextToSpeechProvider tts;
    @Mock QuestionService questions;
    @Mock ConversationService conversations;

    @Test
    void canonicalizesSpokenSectionAndUsesExistingQuestionPipeline() {
        byte[] input = "audio".getBytes();
        when(stt.transcribe(input, "audio/wav", Language.EN))
                .thenReturn(new VoiceTranscript("What is section 3P of the Patents Act?", Language.EN));
        QuestionResponse expected = new QuestionResponse("Grounded answer", AnswerType.RAG_GROUNDED,
                0.81, false, Jurisdiction.INDIA, Language.EN, null, List.of(), List.of());
        when(questions.answer(any())).thenReturn(expected);
        when(tts.synthesize("Grounded answer", Language.EN))
                .thenReturn(new SynthesizedSpeech(new byte[]{1, 2, 3}, "audio/wav"));

        var service = new VoiceService(new VoiceProperties(), stt, tts, questions, conversations);
        var response = service.ask(input, "audio/wav", Language.EN, Jurisdiction.INDIA, null, null);

        ArgumentCaptor<QuestionRequest> request = ArgumentCaptor.forClass(QuestionRequest.class);
        verify(questions).answer(request.capture());
        assertThat(request.getValue().question()).isEqualTo("What is Section 3(p) of the Patents Act?");
        assertThat(response.transcript()).isEqualTo("What is section 3P of the Patents Act?");
        assertThat(response.audioMimeType()).isEqualTo("audio/wav");
        assertThat(response.audioBase64()).isNotBlank();
    }

    @Test
    void canonicalizesSectionThreeE() {
        var service = new VoiceService(new VoiceProperties(), stt, tts, questions, conversations);
        assertThat(service.normalizeSpokenLegalReferences("Explain Section three ee"))
                .isEqualTo("Explain Section 3(e)");
    }

    @Test
    void voiceServiceProcessesSection377AndReturnsSynthesizedAudio() {
        byte[] input = "audio377".getBytes();
        when(stt.transcribe(input, "audio/wav", Language.EN))
                .thenReturn(new VoiceTranscript("What is section 377 in India?", Language.EN));
        QuestionResponse expected = new QuestionResponse("Section 377 grounded answer", AnswerType.RAG_GROUNDED, "DOMAIN_RAG", null, null,
                0.90, false, Jurisdiction.INDIA, Language.EN, Language.EN, Language.EN, null, List.of(), List.of());
        when(questions.answer(any())).thenReturn(expected);
        when(tts.synthesize("Section 377 grounded answer", Language.EN))
                .thenReturn(new SynthesizedSpeech(new byte[]{4, 5, 6}, "audio/wav"));

        var service = new VoiceService(new VoiceProperties(), stt, tts, questions, conversations);
        var response = service.ask(input, "audio/wav", Language.EN, Jurisdiction.INDIA, null, null);

        assertThat(response.route()).isEqualTo("DOMAIN_RAG");
        assertThat(response.answer()).isEqualTo("Section 377 grounded answer");
        assertThat(response.audioBase64()).isNotBlank();
        verify(tts).synthesize("Section 377 grounded answer", Language.EN);
    }

    @Test
    void rejectsUnsupportedMimeBeforeCallingSpeechProvider() {
        var service = new VoiceService(new VoiceProperties(), stt, tts, questions, conversations);

        assertThatThrownBy(() -> service.ask(new byte[]{1}, "application/octet-stream", Language.EN,
                Jurisdiction.INDIA, null, null))
                .isInstanceOfSatisfying(VoiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo("UNSUPPORTED_AUDIO"));
        verifyNoInteractions(stt, tts, questions, conversations);
    }

    @Test
    void rejectsOversizedRecordingBeforeCallingSpeechProvider() {
        VoiceProperties properties = new VoiceProperties();
        properties.setMaxAudioBytes(2);
        var service = new VoiceService(properties, stt, tts, questions, conversations);

        assertThatThrownBy(() -> service.ask(new byte[]{1, 2, 3}, "audio/wav", Language.EN,
                Jurisdiction.INDIA, null, null))
                .isInstanceOfSatisfying(VoiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo("AUDIO_TOO_LARGE"));
        verifyNoInteractions(stt, tts, questions, conversations);
    }

    @Test
    void rejectsBlankProviderTranscriptWithoutCreatingFakeAnswer() {
        byte[] input = "audio".getBytes();
        when(stt.transcribe(input, "audio/wav", Language.TA))
                .thenReturn(new VoiceTranscript("  ", Language.TA));
        var service = new VoiceService(new VoiceProperties(), stt, tts, questions, conversations);

        assertThatThrownBy(() -> service.ask(input, "audio/wav", Language.TA,
                Jurisdiction.INDIA, null, null))
                .isInstanceOfSatisfying(VoiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STT_EMPTY"));
        verifyNoInteractions(tts, questions, conversations);
    }
}
