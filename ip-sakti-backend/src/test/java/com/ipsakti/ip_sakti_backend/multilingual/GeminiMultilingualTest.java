package com.ipsakti.ip_sakti_backend.multilingual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import org.junit.jupiter.api.Test;

class GeminiMultilingualTest {

    @Test
    void languageRegistrySupportsSixLanguages() {
        assertThat(Language.values()).hasSize(6);
        assertThat(Language.fromJson("en")).isEqualTo(Language.EN);
        assertThat(Language.fromJson("hi")).isEqualTo(Language.HI);
        assertThat(Language.fromJson("ta")).isEqualTo(Language.TA);
        assertThat(Language.fromJson("te")).isEqualTo(Language.TE);
        assertThat(Language.fromJson("kn")).isEqualTo(Language.KN);
        assertThat(Language.fromJson("ml")).isEqualTo(Language.ML);
        assertThat(Language.fromJson("EN")).isEqualTo(Language.EN);
        assertThat(Language.fromJson(null)).isEqualTo(Language.EN);
        assertThat(Language.fromJson("")).isEqualTo(Language.EN);
    }

    @Test
    void languageFromJsonThrowsForUnsupported() {
        assertThatThrownBy(() -> Language.fromJson("fr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Language.fromJson("es")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void languageNativeNamesPresent() {
        assertThat(Language.EN.getNativeName()).isEqualTo("English");
        assertThat(Language.HI.getNativeName()).isEqualTo("हिन्दी");
        assertThat(Language.TA.getNativeName()).isEqualTo("தமிழ்");
        assertThat(Language.TE.getNativeName()).isEqualTo("తెలుగు");
        assertThat(Language.KN.getNativeName()).isEqualTo("ಕನ್ನಡ");
        assertThat(Language.ML.getNativeName()).isEqualTo("മലയാളം");
    }

    @Test
    void detectSupportsAllSixScripts() {
        TranslationService service = new TranslationService(mock(TranslationProvider.class));
        assertThat(service.detect("What is a patent?")).isEqualTo(Language.EN);
        assertThat(service.detect("पेटेंट क्या है?")).isEqualTo(Language.HI);
        assertThat(service.detect("காப்புரிமை என்றால் என்ன?")).isEqualTo(Language.TA);
        assertThat(service.detect("పేటెంట్ అంటే ఏమిటి?")).isEqualTo(Language.TE);
        assertThat(service.detect("ಪೇಟೆಂಟ್ ಎಂದರೇನು?")).isEqualTo(Language.KN);
        assertThat(service.detect("പേറ്റന്റ് എന്താണ്?")).isEqualTo(Language.ML);
    }

    @Test
    void englishPassthroughNoProviderCall() {
        TranslationProvider provider = mock(TranslationProvider.class);
        TranslationService service = new TranslationService(provider);
        TranslatedText result = service.toCanonical("What is Section 3(p)?", Language.EN, "req-1");
        assertThat(result.canonicalText()).isEqualTo("What is Section 3(p)?");
        verifyNoInteractions(provider);
        LanguageMetadata meta = new LanguageMetadata(Language.EN, Language.EN, Language.EN);
        String answer = service.fromCanonical("Section 3(p) excludes traditional knowledge.", meta, "req-1");
        assertThat(answer).isEqualTo("Section 3(p) excludes traditional knowledge.");
    }

    @Test
    void queryTranslationHindiToEnglishViaGemini() {
        TranslationProvider provider = mock(TranslationProvider.class);
        when(provider.translate("पेटेंट क्या है?", Language.HI, Language.EN)).thenReturn("What is a patent?");
        when(provider.providerName()).thenReturn("gemini-2.0-flash");
        when(provider.isConfigured()).thenReturn(true);
        TranslationService service = new TranslationService(provider);
        TranslatedText result = service.toCanonical("पेटेंट क्या है?", Language.HI, "req-hi");
        assertThat(result.canonicalText()).isEqualTo("What is a patent?");
        assertThat(result.metadata().requestedLanguage()).isEqualTo(Language.HI);
        assertThat(result.metadata().processingLanguage()).isEqualTo(Language.EN);
        verify(provider).translate("पेटेंट क्या है?", Language.HI, Language.EN);
    }

    @Test
    void queryTranslationAllIndicLanguages() {
        for (Language lang : new Language[]{Language.HI, Language.TA, Language.TE, Language.KN, Language.ML}) {
            TranslationProvider provider = mock(TranslationProvider.class);
            when(provider.translate(any(), eq(lang), eq(Language.EN))).thenReturn("English translation");
            when(provider.providerName()).thenReturn("gemini");
            when(provider.isConfigured()).thenReturn(true);
            TranslationService service = new TranslationService(provider);
            TranslatedText result = service.toCanonical("sample", lang, "req");
            assertThat(result.canonicalText()).isEqualTo("English translation");
        }
    }

    @Test
    void answerTranslationPreservesLegalIdentifiers() {
        TranslationProvider provider = mock(TranslationProvider.class);
        when(provider.translate("Section 3(p) excludes traditional knowledge under Patents Act, 1970.", Language.EN, Language.HI))
                .thenReturn("धारा 3(p) पेटेंट अधिनियम, 1970 के तहत पारंपरिक ज्ञान को बाहर करती है।");
        when(provider.providerName()).thenReturn("gemini");
        when(provider.isConfigured()).thenReturn(true);
        TranslationService service = new TranslationService(provider);
        LanguageMetadata meta = new LanguageMetadata(Language.HI, Language.HI, Language.EN);
        String translated = service.fromCanonical("Section 3(p) excludes traditional knowledge under Patents Act, 1970.", meta, "req");
        assertThat(translated).contains("3(p)");
        assertThat(translated).isNotBlank();
    }

    @Test
    void translationProviderInterfaceNotCalledForSameLanguage() {
        TranslationProvider provider = mock(TranslationProvider.class);
        when(provider.providerName()).thenReturn("gemini");
        TranslationService service = new TranslationService(provider);
        TranslatedText result = service.toCanonical("Hello", Language.EN, "req");
        assertThat(result.canonicalText()).isEqualTo("Hello");
        verifyNoInteractions(provider);
    }

    @Test
    void legalTerminologyPreservedInTranslation() {
        String[] legalTerms = {"Section 3(p)", "Section 3(e)", "Rule 13", "Patents Act, 1970", "Trade Marks Act, 1999", "GRATK", "ABS", "GI", "WIPO", "TKDL"};
        for (String term : legalTerms) {
            TranslationProvider provider = mock(TranslationProvider.class);
            when(provider.translate(term, Language.HI, Language.EN)).thenReturn(term);
            when(provider.providerName()).thenReturn("gemini");
            when(provider.isConfigured()).thenReturn(true);
            TranslationService service = new TranslationService(provider);
            TranslatedText result = service.toCanonical(term, Language.HI, "req");
            assertThat(result.canonicalText()).isEqualTo(term);
        }
    }
}
