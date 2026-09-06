package com.ipsakti.ip_sakti_backend.multilingual;

import com.ipsakti.ip_sakti_backend.exception.TranslationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps authoritative content out of the generative translation input.  This is deliberately
 * provider-independent so every endpoint using TranslationService follows the same policy.
 */
final class TranslationProtection {

    private static final Logger log = LoggerFactory.getLogger(TranslationProtection.class);

    private static final Pattern IMMUTABLE = Pattern.compile(
            "(?i)\\[Source:\\s*[^\\]\\r\\n]+\\]"
                    + "|https?://[^\\s)>]+"
                    + "|\\b(?:Section|Rule|Article|Regulation|Schedule)\\s+\\d+(?:\\([A-Za-z0-9]+\\))?"
                    + "|\\b(?:Patents Act|Trade Marks Act|Biological Diversity Act)(?:,?\\s*\\d{4})?"
                    + "|\\b(?:Patent|Trademark|Copyright|Design|Geographical Indication|Traditional Knowledge|Access and Benefit Sharing|Biological Diversity Act|National Biodiversity Authority|Ministry of AYUSH|IP India|WIPO|FSSAI|CDSCO|TKDL|GRATK|NBA|ABS|GI|PCT|Act|Rule|Schedule)\\b"
                    + "|\\b[A-Z][A-Z0-9]{2,}(?:-[A-Z0-9]+){1,}\\b"
                    + "|\\b\\d+(?:[.,]\\d+)?%"
                    + "|\\b\\d{1,4}(?:[-/]\\d{1,2}){1,2}\\b",
            Pattern.MULTILINE);
    private static final Pattern MARKDOWN = Pattern.compile("(?m)^(?:#{1,6}\\s+|[-*+]\\s+|\\d+\\.\\s+)|`[^`\\r\\n]+`|\\*\\*|__");
    private static final Pattern TOKEN = Pattern.compile("\\[\\[IPSAKTI_TOKEN_(\\d{4})\\]\\]");

    private TranslationProtection() {}

    static ProtectedText protect(String text) {
        List<Span> spans = new ArrayList<>();
        collect(spans, IMMUTABLE.matcher(text));
        collect(spans, MARKDOWN.matcher(text));
        spans.sort((left, right) -> Integer.compare(left.start(), right.start()));

        StringBuilder output = new StringBuilder();
        Map<String, String> tokens = new LinkedHashMap<>();
        int cursor = 0;
        int index = 1;
        for (Span span : spans) {
            if (span.start() < cursor) {
                continue;
            }
            output.append(text, cursor, span.start());
            String token = String.format("[[IPSAKTI_TOKEN_%04d]]", index++);
            tokens.put(token, span.value());
            output.append(token);
            cursor = span.end();
        }
        output.append(text.substring(cursor));
        return new ProtectedText(output.toString(), tokens);
    }

    static String restore(String translated, ProtectedText protectedText) {
        List<String> returnedTokens = new ArrayList<>();
        Matcher returnedMatcher = TOKEN.matcher(translated);
        while (returnedMatcher.find()) {
            returnedTokens.add(returnedMatcher.group());
        }
        List<String> expectedTokens = new ArrayList<>(protectedText.tokens().keySet());
        boolean exactIdentityAndCount = returnedTokens.size() == expectedTokens.size()
                && new HashSet<>(returnedTokens).size() == returnedTokens.size()
                && new HashSet<>(returnedTokens).equals(new HashSet<>(expectedTokens));
        if (!exactIdentityAndCount) {
            log.warn("translation_token_mismatch expectedCount={} returnedCount={} exactIdentityAndCount=false expectedTokens={} returnedTokens={}",
                    expectedTokens.size(), returnedTokens.size(), expectedTokens, returnedTokens);
            throw TranslationException.malformedResponse();
        }

        String result = translated;
        for (Map.Entry<String, String> entry : protectedText.tokens().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        if (TOKEN.matcher(result).find()) {
            throw TranslationException.malformedResponse();
        }
        return result;
    }

    static boolean containsReservedToken(String text) {
        return text != null && TOKEN.matcher(text).find();
    }

    private static void collect(List<Span> spans, Matcher matcher) {
        while (matcher.find()) {
            spans.add(new Span(matcher.start(), matcher.end(), matcher.group()));
        }
    }

    record ProtectedText(String text, Map<String, String> tokens) {}
    private record Span(int start, int end, String value) {}
}
