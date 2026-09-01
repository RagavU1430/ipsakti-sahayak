package com.ipsakti.ip_sakti_backend.multilingual;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipsakti.ip_sakti_backend.question.model.Language;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TranslationServiceTest {

    @Test
    void detectsEnglishTamilAndHindiScripts() {
        TranslationService service = new TranslationService(Mockito.mock(BhashiniClient.class));

        assertThat(service.detect("What is a patent?")).isEqualTo(Language.EN);
        assertThat(service.detect("காப்புரிமை")).isEqualTo(Language.TA);
        assertThat(service.detect("पेटेंट")).isEqualTo(Language.HI);
    }

    @Test
    void englishInputPassesThroughWithoutBhashini() {
        BhashiniClient client = Mockito.mock(BhashiniClient.class);
        TranslationService service = new TranslationService(client);

        TranslatedText translated = service.toCanonical("What is a patent?", Language.EN, "request-1");

        assertThat(translated.canonicalText()).isEqualTo("What is a patent?");
        assertThat(translated.metadata().requestedLanguage()).isEqualTo(Language.EN);
        Mockito.verifyNoInteractions(client);
    }
}
