package com.ipsakti.ip_sakti_backend.multilingual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranslationProtectionRegressionTest {

    private static final String ENGLISH_ANSWER = """
            ## Patent assessment
            1. Under Section 3(p), Traditional Knowledge may affect Patent eligibility. [Source: IND-PAT-ACT-1970]
            2. The Biological Diversity Act and NBA may require verification; this does not establish legal approval.
            See https://ipindia.gov.in and retain 89% confidence on 2026-09-06.
            """;

    @Test
    void protectsAndRestoresLegalCitationMarkdownAndValuesForEveryIndicLanguage() {
        for (Language language : new Language[] {Language.HI, Language.TA, Language.TE, Language.KN, Language.ML}) {
            TranslationService service = new TranslationService(echoingProvider());
            String translated = service.fromCanonical(ENGLISH_ANSWER,
                    new LanguageMetadata(language, language, Language.EN), "answer-" + language);

            assertThat(translated).isEqualTo(ENGLISH_ANSWER);
            assertThat(translated).contains("Section 3(p)", "Traditional Knowledge", "Biological Diversity Act", "NBA",
                    "[Source: IND-PAT-ACT-1970]", "https://ipindia.gov.in", "89%", "2026-09-06", "##", "1.");
        }
    }

    @Test
    void failsClosedWhenGeminiDropsAnImmutableToken() {
        TranslationProvider provider = mock(TranslationProvider.class);
        when(provider.translate(any(), eq(Language.EN), eq(Language.TA))).thenReturn("translated answer without tokens");
        TranslationService service = new TranslationService(provider);

        assertThatThrownBy(() -> service.fromCanonical(ENGLISH_ANSWER,
                new LanguageMetadata(Language.TA, Language.TA, Language.EN), "token-loss"))
                .isInstanceOf(TranslationException.class)
                .extracting("code")
                .isEqualTo("TRANSLATION_UNAVAILABLE");
        verify(provider).invalidate(any(), eq(Language.EN), eq(Language.TA));
    }

    @Test
    void retriesIntegrityFailureOnceAfterEvictingCachedTranslation() {
        TranslationProvider provider = mock(TranslationProvider.class);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(provider.translate(any(), eq(Language.EN), eq(Language.TA))).thenAnswer(invocation ->
                calls.getAndIncrement() == 0 ? "missing protected tokens" : invocation.getArgument(0));
        when(provider.providerName()).thenReturn("gemini-test");
        TranslationService service = new TranslationService(provider);

        String result = service.fromCanonical(ENGLISH_ANSWER,
                new LanguageMetadata(Language.TA, Language.TA, Language.EN), "retry-success");

        assertThat(result).isEqualTo(ENGLISH_ANSWER);
        assertThat(calls).hasValue(2);
        verify(provider).invalidate(any(), eq(Language.EN), eq(Language.TA));
    }

    @Test
    void failsClosedForDuplicatedModifiedAndUnknownTokensButAllowsGrammarSafeReordering() {
        TranslationProtection.ProtectedText protectedText = TranslationProtection.protect(
                "Section 3(p) cites [Source: IND-PAT-ACT-1970].");
        String first = protectedText.tokens().keySet().stream().findFirst().orElseThrow();
        String second = protectedText.tokens().keySet().stream().skip(1).findFirst().orElseThrow();

        assertMalformed(() -> TranslationProtection.restore(protectedText.text() + first, protectedText));
        assertMalformed(() -> TranslationProtection.restore(protectedText.text().replace(first, "[[IPSAKTI_TOKEN_9999]]"), protectedText));
        assertMalformed(() -> TranslationProtection.restore(protectedText.text() + " [[IPSAKTI_TOKEN_9999]]", protectedText));
        assertThat(TranslationProtection.restore(second + " " + first, protectedText))
                .contains("Section 3(p)", "[Source: IND-PAT-ACT-1970]");
    }

    @Test
    void rejectsReservedTokenInjectionBeforeProviderCall() {
        TranslationService service = new TranslationService(echoingProvider());
        assertMalformed(() -> service.toCanonical("Explain [[IPSAKTI_TOKEN_0001]]", Language.HI, "injection"));
    }

    @Test
    void queryTranslationUsesSelectedSourceLanguageForAllSupportedIndicScripts() {
        Map<Language, String> queries = Map.of(
                Language.HI, "भारत में पेटेंट क्या है?",
                Language.TA, "இந்தியாவில் காப்புரிமை என்றால் என்ன?",
                Language.TE, "భారతదేశంలో పేటెంట్ అంటే ఏమిటి?",
                Language.KN, "ಭಾರತದಲ್ಲಿ ಪೇಟೆಂಟ್ ಎಂದರೇನು?",
                Language.ML, "ഇന്ത്യയിൽ പേറ്റന്റ് എന്താണ്?");
        for (Map.Entry<Language, String> entry : queries.entrySet()) {
            TranslationProvider provider = mock(TranslationProvider.class);
            when(provider.translate(any(), eq(entry.getKey()), eq(Language.EN))).thenReturn("What is a patent in India?");
            when(provider.providerName()).thenReturn("gemini-test");
            TranslationService service = new TranslationService(provider);

            assertThat(service.toCanonical(entry.getValue(), entry.getKey(), "query-" + entry.getKey()).canonicalText())
                    .isEqualTo("What is a patent in India?");
        }
    }

    @Test
    void cacheKeySeparatesLanguagesTextAndPromptVersion() {
        String hi = GeminiTranslationProvider.cacheKey(Language.EN, Language.HI, "What is a patent?");
        String ta = GeminiTranslationProvider.cacheKey(Language.EN, Language.TA, "What is a patent?");
        String changedText = GeminiTranslationProvider.cacheKey(Language.EN, Language.HI, "What is a trademark?");

        assertThat(hi).startsWith(GeminiTranslationProvider.TRANSLATION_PROMPT_VERSION + ":en->hi:");
        assertThat(hi).isNotEqualTo(ta).isNotEqualTo(changedText);
    }

    private TranslationProvider echoingProvider() {
        TranslationProvider provider = mock(TranslationProvider.class);
        when(provider.translate(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(provider.providerName()).thenReturn("gemini-test");
        return provider;
    }

    private void assertMalformed(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(TranslationException.class)
                .extracting("code")
                .isEqualTo("TRANSLATION_MALFORMED_RESPONSE");
    }
}
