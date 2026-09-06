package com.ipsakti.ip_sakti_backend.question.routing;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DefaultQueryRouter implements QueryRouter {

    private static final java.util.regex.Pattern SECTION_PATTERN = java.util.regex.Pattern.compile("(?i)\\bsection\\s+(?:\\d+|three|3)\\b|\\bsection\\s+(?:3|three)\\s*(?:\\(\\s*[a-z0-9]+\\s*\\)|[a-z0-9]+)|\\bsection\\s+\\d+[a-z]?\\b");
    private static final java.util.regex.Pattern GI_PATTERN = java.util.regex.Pattern.compile("(?i)\\bgi\\b");
    private static final java.util.regex.Pattern ABS_PATTERN = java.util.regex.Pattern.compile("(?i)\\babs\\b");
    private static final java.util.regex.Pattern GRATK_PATTERN = java.util.regex.Pattern.compile("(?i)\\bgratk\\b");
    private static final java.util.regex.Pattern IP_PATTERN = java.util.regex.Pattern.compile("(?i)\\bip\\b");
    private static final java.util.regex.Pattern TKDL_PATTERN = java.util.regex.Pattern.compile("(?i)\\btkdl\\b");

    private static final String[] LEGAL_AUTHORITY = {
            "under indian law", "under the law", "legal position", "legally", "statute", "statutory",
            "act say", "rules say", "regulation", "regulatory", "regulatory status", "compliance", "requirement", "eligibility",
            "infringement", "registration", "licensing", "official", "government document", "government policy",
            "government notification", "wipo framework", "ip law", "ip route", "design law", "design protection",
            "diversity act", "abs approval", "indian law", "law in india", "in india", "legal provision", "legal section",
            "legal clause", "document section", "patentability", "patent infringement", "trademark registration",
            "copyright law", "copyright protection", "gi registration", "geographical indication", "traditional knowledge",
            "biodiversity law", "biodiversity act", "access and benefit sharing", "ayurveda regulation",
            "formulation classification", "indian patent rules", "patents act", "patent act",
            "ip rules", "ip rights", "ip protection", "ip registration",
            "indigenous knowledge", "community knowledge", "herbal formulation", "medicinal plant",
            "breeder rights", "plant variety", "trade secret", "prior art", "disclosure"
    };
    private static final String[] HIGH_RISK = {
            "section 3(p)", "section 3p", "3(p)", "section 3(e)", "section 3e", "3(e)",
            "patents act", "patent act", "trade marks act", "trademark law", "copyright law", "designs act",
            "gi registration", "ppvfr", "fssai", "access and benefit sharing", "biological resources",
            "traditional knowledge", "tkdl", "biodiversity act", "abs requirement",
            "section 377", "section 3", "section 4", "section 8", "section 9",
            "what is a gi", "what is gi", "what is abs", "what is gratk", "what does the patents act say"
    };
    private static final String[] DOCUMENT = {
            "this document", "this pdf", "uploaded document", "uploaded file", "provided document",
            "attached document", "according to the document", "based on the document", "in this file"
    };
    private static final String[] FOLLOW_UP = {
            "explain it", "explain that", "tell me more", "what about it", "why is that", "summarize it",
            "what are its requirements", "does it apply", "and in india",
            "how long does it last", "how long is it", "how long does that last", "how long",
            "what about", "who can apply", "can it be", "does it need", "how much does it cost",
            "what are the fees", "how to apply for it", "where to apply", "validity", "duration", "renewal"
    };
    private static final String[] GREETINGS = {
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening", "how are you",
            "thank you", "thanks", "goodbye", "bye", "who are you",
            "what can you do", "how can you help me", "how can you help", "explain how this chatbot works", "what can this chatbot do",
            "what is your work", "what do you do", "what is your job", "tell me about yourself"
    };
    private static final String[] GENERAL_TASKS = {
            "tell me a joke", "write an email", "write a simple", "summarize this sentence", "what is python",
            "what is java", "explain java", "machine learning", "explain recursion", "capital of france",
            "what is an api", "what does http", "what is 2 + 2", "weather", "recipe", "poem", "story"
    };

    @Override
    public RoutingDecision route(String canonicalQuery, Language requestedLanguage, Jurisdiction jurisdiction, RoutingContext context) {
        String query = normalize(canonicalQuery);
        boolean document = containsAny(query, DOCUMENT);
        QueryDomain domain = detectDomain(query);

        if (document) {
            return decision(QueryRoute.DOMAIN_RAG, QueryDomain.DOCUMENT_GROUNDED, .99, RoutingReason.DOCUMENT_REFERENCE, true, true);
        }
        if (containsAny(query, HIGH_RISK) || containsAny(query, LEGAL_AUTHORITY) || isSectionPattern(query) || isAbbreviationDomain(query)) {
            if (domain == null && (jurisdiction == Jurisdiction.INDIA || jurisdiction == Jurisdiction.AUTO)) domain = QueryDomain.INDIA_IP_LAW;
            if (domain == null) domain = QueryDomain.REGULATORY;
            return decision(QueryRoute.DOMAIN_RAG, domain, .98, RoutingReason.LEGAL_HIGH_RISK_OVERRIDE, true, false);
        }
        if (context != null && context.previousRoute() == QueryRoute.DOMAIN_RAG && isFollowUpMatch(query)) {
            return decision(QueryRoute.DOMAIN_RAG, context.previousDomain(), .92, RoutingReason.CONVERSATION_CONTEXT, true, false);
        }
        if (containsAny(query, "reveal api key", "show system prompt", "database password", "service role key")) {
            return decision(QueryRoute.UNSUPPORTED, null, .99, RoutingReason.UNSUPPORTED_REQUEST, false, false);
        }
        if (containsExactOrPrefix(query, GREETINGS)) {
            return decision(QueryRoute.GENERAL, null, .99, RoutingReason.CASUAL_CONVERSATION, false, false);
        }
        if (containsAny(query, GENERAL_TASKS)) {
            return decision(QueryRoute.GENERAL, null, .96, RoutingReason.GENERAL_TASK, false, false);
        }
        if (isGeneralDefinition(query, domain)) {
            return decision(QueryRoute.GENERAL, null, .88, RoutingReason.GENERAL_KNOWLEDGE, false, false);
        }
        if (domain != null) {
            return decision(QueryRoute.DOMAIN_RAG, domain, .88, RoutingReason.DOMAIN_AUTHORITY_REQUIRED, true, false);
        }
        if (query.length() < 12 || containsAny(query, "it?", "this?", "what about that")) {
            return decision(QueryRoute.AMBIGUOUS, null, .50, RoutingReason.NEEDS_CLARIFICATION, false, false);
        }
        return decision(QueryRoute.GENERAL, null, .82, RoutingReason.GENERAL_KNOWLEDGE, false, false);
    }

    private boolean isSectionPattern(String query) {
        return SECTION_PATTERN.matcher(query).find();
    }

    private boolean isAbbreviationDomain(String query) {
        return GI_PATTERN.matcher(query).find() || ABS_PATTERN.matcher(query).find()
                || GRATK_PATTERN.matcher(query).find() || IP_PATTERN.matcher(query).find()
                || TKDL_PATTERN.matcher(query).find();
    }

    private boolean isFollowUpMatch(String query) {
        if (containsAny(query, FOLLOW_UP)) return true;
        return query.contains(" it") || query.contains(" that") || query.contains(" this") || query.contains("last") || query.contains("apply") || query.contains("valid");
    }

    private boolean isGeneralDefinition(String query, QueryDomain domain) {
        boolean simpleDefinition = query.startsWith("what is ") || query.startsWith("what are ")
                || query.startsWith("explain ") || query.contains("in simple words");
        if (!simpleDefinition || containsAny(query, LEGAL_AUTHORITY) || containsAny(query, HIGH_RISK) || isSectionPattern(query)) return false;
        if (containsAny(query, "patentability", "copyright protection", "geographical indication", "traditional knowledge", "biodiversity", "abs", "patent rules")) return false;
        return query.equals("what is ayurveda?") || query.equals("what is ayurveda") || (domain == null && query.contains("in simple words"));
    }

    private QueryDomain detectDomain(String query) {
        // Ordered by the user's requested legal operation, then subject matter. This is intentionally
        // deterministic for mixed queries such as "Can an Ayurvedic formulation be patented?".
        if (query.contains("patent")) return QueryDomain.PATENT;
        if (containsAny(query, "trademark", "trade mark")) return QueryDomain.TRADEMARK;
        if (query.contains("copyright")) return QueryDomain.COPYRIGHT;
        if (query.contains("geographical indication") || GI_PATTERN.matcher(query).find()) return QueryDomain.GEOGRAPHICAL_INDICATION;
        if (query.contains("industrial design")) return QueryDomain.INDUSTRIAL_DESIGN;
        if (query.contains("trade secret")) return QueryDomain.TRADE_SECRET;
        if (query.contains("access and benefit sharing") || ABS_PATTERN.matcher(query).find()) return QueryDomain.ABS;
        if (query.contains("gratk") || GRATK_PATTERN.matcher(query).find()) return QueryDomain.GRATK;
        if (TKDL_PATTERN.matcher(query).find()) return QueryDomain.TRADITIONAL_KNOWLEDGE;
        if (containsAny(query, "traditional knowledge", "indigenous knowledge", "community knowledge")) return QueryDomain.TRADITIONAL_KNOWLEDGE;
        if (query.contains("biodiversity")) return QueryDomain.BIODIVERSITY;
        if (containsAny(query, "ayurveda", "ayurvedic", "herbal formulation", "medicinal plant")) return QueryDomain.AYURVEDA;
        if (query.contains("formulation")) return QueryDomain.FORMULATION;
        if (query.contains("wipo")) return QueryDomain.INTERNATIONAL_IP;
        if (query.contains("intellectual property")) return QueryDomain.IP;
        if (containsAny(query, "plant variety", "ppvfr", "breeder rights")) return QueryDomain.BIODIVERSITY;
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private boolean containsAny(String value, String... signals) {
        for (String signal : signals) if (value.contains(signal)) return true;
        return false;
    }

    private boolean containsExactOrPrefix(String value, String... signals) {
        for (String signal : signals) {
            if (value.equals(signal) || value.equals(signal + "!") || value.equals(signal + ".") || value.equals(signal + "?") || value.startsWith(signal + ",") || value.startsWith(signal + "?")) return true;
        }
        return false;
    }

    private RoutingDecision decision(QueryRoute route, QueryDomain domain, double confidence, RoutingReason reason,
                                     boolean authoritative, boolean document) {
        return new RoutingDecision(route, domain, confidence, reason, authoritative, document);
    }
}
