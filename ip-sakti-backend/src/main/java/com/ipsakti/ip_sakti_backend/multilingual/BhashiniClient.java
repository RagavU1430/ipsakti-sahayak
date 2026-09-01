package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.question.model.Language;

public interface BhashiniClient {

    String translate(String text, Language sourceLanguage, Language targetLanguage);
}
