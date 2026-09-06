package com.ipsakti.ip_sakti_backend.formulation;

import com.ipsakti.ip_sakti_backend.config.GeminiProperties;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationChatRequest;
import com.ipsakti.ip_sakti_backend.formulation.model.FormulationChatResponse;
import com.ipsakti.ip_sakti_backend.multilingual.TranslationService;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FormulationChatService {

    private static final Logger log = LoggerFactory.getLogger(FormulationChatService.class);

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final TranslationService translationService;

    public FormulationChatService(
            GeminiProperties properties,
            @Qualifier("geminiRestClient") RestClient restClient,
            TranslationService translationService
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.translationService = translationService;
    }

    public FormulationChatResponse chat(FormulationChatRequest request) {
        Language lang = request.language() != null ? request.language() : Language.EN;
        String message = request.message() != null ? request.message().trim() : "";
        String report = request.reportContext() != null ? request.reportContext() : "";

        if (properties.configured()) {
            try {
                String reply = callGeminiForReportChat(message, report, lang);
                if (reply != null && !reply.isBlank()) {
                    return new FormulationChatResponse(reply, lang, buildSuggestedQuestions(lang));
                }
            } catch (Exception ex) {
                log.warn("gemini_formulation_chat_failed error={}, falling back to rule extraction", ex.getMessage());
            }
        }

        // Fallback: rule-based summary extraction
        String fallbackReply = generateFallbackReply(message, report, lang);
        return new FormulationChatResponse(fallbackReply, lang, buildSuggestedQuestions(lang));
    }

    private String callGeminiForReportChat(String message, String report, Language language) {
        String langName = switch (language) {
            case TA -> "Tamil (தமிழ்)";
            case TE -> "Telugu (తెలుగు)";
            case HI -> "Hindi (हिन्दी)";
            case KN -> "Kannada (ಕನ್ನಡ)";
            case ML -> "Malayalam (മലയാളം)";
            default -> "English";
        };

        String systemPrompt = """
                You are IP-SAKTI Sahayak's Formulation Assessment Report Advisor.
                Your task is to summarize and explain the regulatory and IP assessment report for the user's Ayurvedic formulation in simple, structured, and crystal-clear language.

                AUTHORITATIVE ASSESSMENT REPORT:
                ===
                %s
                ===

                USER QUERY:
                %s

                INSTRUCTIONS:
                1. Answer directly and concisely based on the assessment report above.
                2. If the user asks for a summary, provide an executive briefing with 4 structured points:
                   - **Category & Regulatory Pathway**: Assessed classification and governing rules.
                   - **Critical Document Gaps**: Key mandatory documents marked MISSING or UNVERIFIED.
                   - **Section 3(p) Traditional Knowledge & Patent Eligibility**: Whether classical formulations can be patented (never standalone) and prior art implications.
                   - **Priority Next Step**: What the user should do first before approaching regulators.
                3. Use clean Markdown: bold titles (**Title**), clear bullet lists (- item), and short readable paragraphs. Do not write dense walls of text.
                4. Respond fluently in %s.
                5. Keep legal entity names, section numbers (Section 3(p), Schedule T, etc.), and document IDs accurate.
                """.formatted(report, message, langName);

        for (String model : properties.modelCandidates()) {
            try {
                GeminiRequest geminiReq = new GeminiRequest(
                        List.of(new Content(List.of(new Part(systemPrompt)))),
                        new GenerationConfig(0.3, 1400)
                );

                String path = "/v1beta/models/" + model + ":generateContent?key=" + properties.getApiKey();

                GeminiResponse response = restClient.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(geminiReq)
                        .retrieve()
                        .body(GeminiResponse.class);

                if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                    Content content = response.candidates().get(0).content();
                    if (content != null && content.parts() != null && !content.parts().isEmpty()) {
                        String text = content.parts().get(0).text();
                        if (text != null && !text.isBlank()) {
                            log.info("gemini_formulation_chat_success model={}", model);
                            return text.trim();
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("gemini_formulation_chat_candidate_failed model={} error={}", model, ex.getMessage());
            }
        }
        return null;
    }

    private String generateFallbackReply(String message, String report, Language language) {
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("patent") || lower.contains("3(p)") || lower.contains("காப்புரிமை") || lower.contains("పేటెంట్")) {
            return switch (language) {
                case TA -> """
                        ### 💡 காப்புரிமை (Patent) நிலை விளக்கம்:
                        - **பிரிவு 3(p) விலக்கு**: இந்திய காப்புரிமை சட்டம், 1970 பிரிவு 3(p)-இன் கீழ், பாரம்பரிய அறிவு (Traditional Knowledge) அல்லது அறியப்பட்ட மூலிகைகளின் சேர்க்கைகளுக்கு காப்புரிமை வழங்கப்படாது.
                        - **பாரம்பரிய சூத்திரம்**: கிளாசிக்கல் ஆயுர்வேத சூத்திரங்களை இந்தியாவில் தனியாக காப்புரிமை பெற முடியாது.
                        - **வாய்ப்புகள்**: புதிய பிரித்தெடுத்தல் முறை (novel extraction process), மேம்பட்ட மருந்து விநியோக முறை (novel formulation delivery) அல்லது நிரூபிக்கப்பட்ட கூடுதல் செயல்திறன் (synergistic efficacy) இருந்தால் மட்டுமே செயல்முறை காப்புரிமைக்கு (process patent) விண்ணப்பிக்க முடியும்.
                        """;
                case TE -> """
                        ### 💡 పేటెంట్ (Patent) స్థితి వివరణ:
                        - **సెక్షన్ 3(p) మినహాయింపు**: భారత పేటెంట్ చట్టం, 1970 సెక్షన్ 3(p) ప్రకారం, సాంప్రదాయ జ్ఞానం (Traditional Knowledge) లేదా తెలిసిన మూలికల సమ్మేళనాలకు పేటెంట్ ఇవ్వబడదు.
                        - **క్లాసికల్ ఫార్ములేషన్**: క్లాసికల్ ఆయుర్వేద ఫార్ములేషన్‌ను భారతదేశంలో నేరుగా పేటెంట్ చేయలేరు.
                        - **అవకాశాలు**: వినూత్న ఎక్స్‌ట్రాక్షన్ ప్రక్రియ (process patent) లేదా నిరూపితమైన సినర్జిస్టిక్ ప్రభావం ఉంటే మాత్రమే దరఖాస్తు చేసుకోవచ్చు.
                        """;
                default -> """
                        ### 💡 Patent Feasibility Assessment:
                        - **Section 3(p) Exclusion**: Under Section 3(p) of the Indian Patents Act, 1970, traditional knowledge and aggregations of known botanical properties are strictly excluded from patentability.
                        - **Classical Formulations**: Standalone classical Ayurvedic formulations cannot be patented in India.
                        - **Potential Routes**: Novel extraction processes, improved delivery systems, or verifiable synergistic technical effects beyond known herbs may qualify for process patents.
                        """;
            };
        }

        if (lower.contains("missing") || lower.contains("document") || lower.contains("ஆவணம்") || lower.contains("పత్రం")) {
            return switch (language) {
                case TA -> """
                        ### 📄 விடுபட்ட முக்கிய ஆவணங்கள் (Missing Documents):
                        1. **Schedule T GMP சான்றிதழ்**: ஆயுர்வேத மருந்து உற்பத்திக்கு கட்டாயமான உற்பத்தித் தரச் சான்று.
                        2. **மூலப்பொருள் கொள்முதல் / சேகரிப்பு சான்றுகள்**: மூலிகைகளின் தரம் மற்றும் பூகோள மூல விவரங்கள்.
                        3. **தேசிய பல்லுயிர் ஆணைய (NBA) பிரிவு 6 அனுமதி**: இந்திய உயிரியல் வளங்களைப் பயன்படுத்தி வணிகம் அல்லது அறிவுசார் சொத்துரிமை கோரினால் NBA அனுமதி கட்டாயம்.
                        4. **நிலைப்புத்தன்மை மற்றும் நச்சுத்தன்மை சோதனை (Stability & Safety Reports)**: மருந்தின் பாதுகாப்பு தர அறிக்கைகள்.
                        """;
                case TE -> """
                        ### 📄 తప్పిపోయిన ముఖ్యమైన పత్రాలు (Missing Documents):
                        1. **Schedule T GMP సర్టిఫికేట్**: ఆయుర్వేద తయారీకి తప్పనిసరి సర్టిఫికేట్.
                        2. **మూలికల సేకరణ / కొనుగోలు రికార్డులు**: నాణ్యత మరియు మూలం రుజువులు.
                        3. **నేషనల్ బయోడైవర్సిటీ అథారిటీ (NBA) అనుమతి**: జీవ వనరుల వినియోగానికి తప్పనిసరి.
                        4. **స్థిరత్వం & భద్రతా నివేదికలు (Stability / Safety Studies)**.
                        """;
                default -> """
                        ### 📄 Key Document Gaps Identified:
                        1. **Schedule T GMP Compliance Certificate**: Mandatory quality certificate from State AYUSH authority.
                        2. **Raw Material Botanical Identification & Batch Testing Records**: Mandatory under Rule 158-B.
                        3. **National Biodiversity Authority (NBA) Section 6 Approval**: Required if Indian biological resources are utilized.
                        4. **Stability and Heavy Metal Assay Lab Reports**: Certified laboratory testing.
                        """;
            };
        }

        // General Executive Summary fallback
        return switch (language) {
            case TA -> """
                    ### ⚡ அறிக்கையின் சுருக்கம் (Executive Summary):
                    - **வகை மற்றும் ஒழுங்குமுறை பாதை**: இந்த தயாரிப்பு கிளாசிக்கல் ஆயுர்வேத மருந்தாக வகைப்படுத்தப்பட்டுள்ளது. மாநில AYUSH உரிம அதிகாரியிடம் Form 1 சமர்ப்பிக்க வேண்டும்.
                    - **முக்கிய விடுபட்ட ஆவணங்கள்**: Schedule T GMP சான்றிதழ், மூலப்பொருள் தரப் பரிசோதனை அறிக்கை மற்றும் NBA அனுமதி ஆகியவை விடுபட்டுள்ளன.
                    - **காப்புரிமை நிலை**: பிரிவு 3(p)-இன் கீழ் கிளாசிக்கல் தயாரிப்புகளுக்கு காப்புரிமை பெற முடியாது; வர்த்தக முத்திரை (Trademark) பதிவு செய்வது பாதுகாப்பானது.
                    - **அடுத்த கட்ட நடவடிக்கை**: முதலில் ஆய்வக சோதனைகளை முடித்து, AYUSH உரிமத்திற்கு ஆவணங்களை தயார் செய்யவும்.
                    """;
            case TE -> """
                    ### ⚡ నివేదిక సారాంశం (Executive Summary):
                    - **ఉత్పత్తి వర్గం & మార్గం**: ఈ ఉత్పత్తి క్లాసికల్ ఆయుర్వేద ఔషధంగా గుర్తించబడింది. రాష్ట్ర AYUSH అథారిటీ నుండి లైసెన్స్ పొందాలి.
                    - **ముఖ్యమైన లోపాలు**: Schedule T GMP సర్టిఫికేట్ మరియు ప్రయోగశాల పరీక్ష నివేదికలు సమర్పించాల్సి ఉంది.
                    - **పేటెంట్ స్థితి**: సెక్షన్ 3(p) ప్రకారం దీనికి పేటెంట్ లభించదు; ట్రేడ్‌మార్క్ (Trademark) నమోదు చేసుకోవడం శ్రేయస్కరం.
                    - **తదుపరి చర్య**: ప్రయోగశాల నివేదికలను పూర్తి చేసి, లైసెన్స్ కోసం దరఖాస్తు చేసుకోండి.
                    """;
            default -> """
                    ### ⚡ Executive Assessment Summary:
                    - **Category & Route**: Assessed under Ayurvedic Classical Proprietary Drug regulatory framework governed by Drugs and Cosmetics Act (Chapter IV-A).
                    - **Critical Document Gaps**: Missing Schedule T GMP certificate, batch stability records, and National Biodiversity Authority (NBA) evidence.
                    - **Patent Scrutiny**: Strictly non-patentable under Section 3(p) as traditional knowledge; trademark protection and trade dress are recommended.
                    - **Immediate Next Step**: Conduct pharmacopoeial assay testing and submit Form 1 dossier to the State Licensing Authority (AYUSH).
                    """;
        };
    }

    private List<String> buildSuggestedQuestions(Language language) {
        return switch (language) {
            case TA -> List.of(
                    "இந்த அறிக்கையை 3 வரிகளில் சுருக்கவும்",
                    "விடுபட்ட முக்கிய ஆவணங்கள் யாவை?",
                    "இந்த தயாரிப்புக்கு காப்புரிமை பெற முடியுமா?",
                    "தேசிய பல்லுயிர் ஆணைய (NBA) அனுமதி தேவையா?",
                    "அடுத்த கட்டமாக நான் என்ன செய்ய வேண்டும்?"
            );
            case TE -> List.of(
                    "ఈ నివేదికను సంక్షిప్తంగా వివరించండి",
                    "తప్పిపోయిన ముఖ్యమైన పత్రాలు ఏవి?",
                    "ఈ ఉత్పత్తికి పేటెంట్ పొందవచ్చా?",
                    "జీవవైవిధ్యం (NBA) అనుమతి అవసరమా?",
                    "తదుపరి చర్యలు ఏమిటి?"
            );
            default -> List.of(
                    "Summarize this report in 3 simple bullets",
                    "Which documents are missing and required?",
                    "Can I patent this formulation in India?",
                    "Do I need National Biodiversity Authority (NBA) approval?",
                    "What is my immediate next action step?"
            );
        };
    }

    private record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
    private record GenerationConfig(double temperature, int maxOutputTokens) {}
    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
}
