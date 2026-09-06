package com.ipsakti.ip_sakti_backend.question.general;

public interface GeneralLlmProvider {
    String answer(String canonicalQuestion);
    boolean isConfigured();
    String providerName();
}
