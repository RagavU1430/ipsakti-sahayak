package com.ipsakti.ip_sakti_backend.voice;

import com.ipsakti.ip_sakti_backend.auth.UserPrincipal;
import com.ipsakti.ip_sakti_backend.conversation.ConversationService;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageRequest;
import com.ipsakti.ip_sakti_backend.conversation.dto.ConversationMessageResponse;
import com.ipsakti.ip_sakti_backend.question.QuestionService;
import com.ipsakti.ip_sakti_backend.question.model.AnswerType;
import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import com.ipsakti.ip_sakti_backend.question.model.QuestionRequest;
import com.ipsakti.ip_sakti_backend.question.model.QuestionResponse;
import com.ipsakti.ip_sakti_backend.voice.config.VoiceProperties;
import com.ipsakti.ip_sakti_backend.voice.dto.SynthesizedSpeech;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceAskResponse;
import com.ipsakti.ip_sakti_backend.voice.dto.VoiceTranscript;
import com.ipsakti.ip_sakti_backend.voice.exception.VoiceException;
import com.ipsakti.ip_sakti_backend.voice.provider.SpeechToTextProvider;
import com.ipsakti.ip_sakti_backend.voice.provider.TextToSpeechProvider;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VoiceService {
    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    private static final Pattern SECTION_3P = Pattern.compile("(?i)\\bsection\\s+(?:three|3)\\s*(?:\\(\\s*(?:p|pee)\\s*\\)|(?:p|pee))\\b");
    private static final Pattern SECTION_3E = Pattern.compile("(?i)\\bsection\\s+(?:three|3)\\s*(?:\\(\\s*(?:e|ee)\\s*\\)|(?:e|ee))\\b");

    private final VoiceProperties properties;
    private final SpeechToTextProvider stt;
    private final TextToSpeechProvider tts;
    private final QuestionService questions;
    private final ConversationService conversations;

    public VoiceService(VoiceProperties properties, SpeechToTextProvider stt, TextToSpeechProvider tts,
                        QuestionService questions, ConversationService conversations) {
        this.properties = properties;
        this.stt = stt;
        this.tts = tts;
        this.questions = questions;
        this.conversations = conversations;
    }

    public VoiceAskResponse ask(byte[] audio, String mimeType, Language language, Jurisdiction jurisdiction,
                                String conversationId, UserPrincipal principal) {
        long started = System.nanoTime();
        validateAudio(audio, mimeType);
        VoiceTranscript transcript = stt.transcribe(audio, mimeType, language);
        if (transcript == null || transcript.text() == null || transcript.text().isBlank()) throw VoiceException.sttEmpty();

        String rawTranscript = transcript.text().trim();
        String canonicalVoiceQuestion = normalizeSpokenLegalReferences(rawTranscript);
        QuestionResponse answer;
        String activeConversationId = null;
        String userMessageId = null;
        String assistantMessageId = null;

        if (conversationId != null && !conversationId.isBlank()) {
            if (principal == null) {
                throw VoiceException.of("AUTH_REQUIRED", "Authentication is required for conversation voice messages.", org.springframework.http.HttpStatus.UNAUTHORIZED);
            }
            UUID id;
            try { id = UUID.fromString(conversationId.trim()); }
            catch (IllegalArgumentException ex) {
                throw VoiceException.of("INVALID_CONVERSATION_ID", "The conversation identifier is invalid.", org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            ConversationMessageResponse response = conversations.askInConversation(
                    principal, id, new ConversationMessageRequest(canonicalVoiceQuestion, jurisdiction, language));
            answer = new QuestionResponse(
                    response.answer(),
                    "GENERAL_FALLBACK".equalsIgnoreCase(response.responseType()) ? AnswerType.GENERAL_FALLBACK : AnswerType.RAG_GROUNDED,
                    response.route(), response.domain(), null, response.confidence(), response.abstained(),
                    response.jurisdiction(), response.language(), response.detectedLanguage(), response.processingLanguage(),
                    response.intent(), response.citations(), response.sources());
            activeConversationId = id.toString();
            userMessageId = response.userMessageId() == null ? null : response.userMessageId().toString();
            assistantMessageId = response.messageId() == null ? null : response.messageId().toString();
        } else {
            answer = questions.answer(new QuestionRequest(canonicalVoiceQuestion, jurisdiction, language));
        }

        SynthesizedSpeech speech = tts.synthesize(answer.answer(), answer.language());
        if (speech == null || speech.bytes() == null || speech.bytes().length == 0) throw VoiceException.ttsFailed();
        long latencyMs = elapsedMs(started);
        log.info("voice_v2_success language={} route={} domain={} abstained={} audioBytes={} latencyMs={}",
                answer.language(), answer.route(), answer.domain(), answer.abstained(), speech.bytes().length, latencyMs);
        return new VoiceAskResponse(
                rawTranscript, answer.language(), answer.jurisdiction(), answer.answer(), answer.answerType(),
                answer.route(), answer.domain(), answer.confidence(), safe(answer.citations()), safe(answer.sources()),
                answer.abstained(), Base64.getEncoder().encodeToString(speech.bytes()), speech.mimeType(),
                Boolean.TRUE.equals(answer.abstained()) ? "ABSTAINED" : "SUCCESS", latencyMs,
                activeConversationId, userMessageId, assistantMessageId);
    }

    public SynthesizedSpeech synthesize(String text, Language language) {
        if (text == null || text.isBlank()) throw VoiceException.ttsFailed();
        SynthesizedSpeech speech = tts.synthesize(text.trim(), language);
        if (speech == null || speech.bytes() == null || speech.bytes().length == 0) throw VoiceException.ttsFailed();
        return speech;
    }

    String normalizeSpokenLegalReferences(String text) {
        String normalized = SECTION_3P.matcher(text).replaceAll("Section 3(p)");
        return SECTION_3E.matcher(normalized).replaceAll("Section 3(e)");
    }

    private void validateAudio(byte[] audio, String mimeType) {
        if (audio == null || audio.length == 0) throw VoiceException.noAudio();
        if (audio.length > properties.getMaxAudioBytes()) throw VoiceException.tooLarge();
        if (mimeType == null || mimeType.isBlank()) throw VoiceException.unsupportedAudio();
        String clean = mimeType.split(";")[0].trim().toLowerCase();
        if (properties.getAllowedMimeTypes().stream().noneMatch(value -> value.equalsIgnoreCase(clean))) {
            throw VoiceException.unsupportedAudio();
        }
    }

    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
