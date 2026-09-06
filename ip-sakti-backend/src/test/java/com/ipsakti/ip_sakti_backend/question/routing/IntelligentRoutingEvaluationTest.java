package com.ipsakti.ip_sakti_backend.question.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.question.model.Language;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntelligentRoutingEvaluationTest {
    private final QueryRouter router = new DefaultQueryRouter();

    @Test
    void evaluatesLockedRoutingCorpus() throws Exception {
        List<Case> cases = loadCases();
        assertThat(cases).hasSizeGreaterThanOrEqualTo(100);
        List<String> failures = new ArrayList<>();
        int ragTruePositive = 0;
        int ragPredicted = 0;
        int ragExpected = 0;

        for (Case item : cases) {
            QueryRoute actual = router.route(item.query(), Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route();
            if (actual == QueryRoute.DOMAIN_RAG) ragPredicted++;
            if (item.expected() == QueryRoute.DOMAIN_RAG) {
                ragExpected++;
                if (actual == QueryRoute.DOMAIN_RAG) ragTruePositive++;
            }
            if (actual != item.expected()) failures.add(item.query() + " expected=" + item.expected() + " actual=" + actual);
        }

        double ragPrecision = ragPredicted == 0 ? 0 : (double) ragTruePositive / ragPredicted;
        double ragRecall = ragExpected == 0 ? 0 : (double) ragTruePositive / ragExpected;
        assertThat(ragPrecision).as("RAG precision; failures=%s", failures).isGreaterThanOrEqualTo(.98);
        assertThat(ragRecall).as("RAG recall; failures=%s", failures).isGreaterThanOrEqualTo(.98);
        assertThat(failures).isEmpty();
    }

    @Test
    void inheritsRagForDependentFollowUpButNotForStandaloneGeneralQuestion() {
        RoutingContext patentContext = new RoutingContext(QueryRoute.DOMAIN_RAG, QueryDomain.PATENT);
        RoutingDecision followUp = router.route("Explain it simply", Language.EN, Jurisdiction.AUTO, patentContext);
        RoutingDecision fresh = router.route("Explain recursion", Language.EN, Jurisdiction.AUTO, RoutingContext.empty());
        assertThat(followUp.route()).isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(followUp.domain()).isEqualTo(QueryDomain.PATENT);
        assertThat(fresh.route()).isEqualTo(QueryRoute.GENERAL);
    }

    @Test
    void routingIsLanguageAgnosticAfterCanonicalTranslation() {
        for (Language language : Language.values()) {
            assertThat(router.route("Hi", language, Jurisdiction.AUTO, RoutingContext.empty()).route())
                    .as("general route for %s", language).isEqualTo(QueryRoute.GENERAL);
            assertThat(router.route("What is Section 3(p) under Indian law?", language, Jurisdiction.INDIA, RoutingContext.empty()).route())
                    .as("RAG route for %s", language).isEqualTo(QueryRoute.DOMAIN_RAG);
        }
    }

    @Test
    void evaluatesUserPromptRequiredMatrix() {
        // ===== GENERAL (7 cases) =====
        assertThat(router.route("Hi", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Hi").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("Hello", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Hello").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("How are you?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("How are you?").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("What can you do?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What can you do?").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("What is Python?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is Python?").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("What is 2 + 2?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is 2 + 2?").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("Tell me a joke", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Tell me a joke").isEqualTo(QueryRoute.GENERAL);

        // ===== DOMAIN_RAG (17 cases) =====
        assertThat(router.route("What is intellectual property?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("intellectual property").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is IP?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is IP?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is a patent?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is a patent?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is a trademark?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is a trademark?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is a GI?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is a GI?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is traditional knowledge?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("traditional knowledge").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is TKDL?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is TKDL?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is ABS?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is ABS?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is GRATK?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("What is GRATK?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is biodiversity?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("biodiversity").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What are IP rules in India?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("IP rules in India").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("I want to know about IP rules in India", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("I want to know about IP rules in India").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What are Indian patent rules?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Indian patent rules").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is Section 3(p)?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Section 3(p)").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What is Section 377?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Section 377").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("How long does a patent last?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("How long does a patent last?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(router.route("What does the Patents Act say?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Patents Act").isEqualTo(QueryRoute.DOMAIN_RAG);

        // ===== FOLLOW-UP (3 cases) =====
        // 1. Patent follow-up
        RoutingDecision patentDecision = router.route("What is a patent?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty());
        assertThat(patentDecision.route()).isEqualTo(QueryRoute.DOMAIN_RAG);
        RoutingContext patentContext = new RoutingContext(patentDecision.route(), patentDecision.domain());
        RoutingDecision followUp1 = router.route("How long does it last?", Language.EN, Jurisdiction.AUTO, patentContext);
        assertThat(followUp1.route()).as("Patent follow-up: How long does it last?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(followUp1.domain()).isEqualTo(QueryDomain.PATENT);

        // 2. Traditional knowledge follow-up
        RoutingDecision tkDecision = router.route("What is traditional knowledge?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty());
        assertThat(tkDecision.route()).isEqualTo(QueryRoute.DOMAIN_RAG);
        RoutingContext tkContext = new RoutingContext(tkDecision.route(), tkDecision.domain());
        RoutingDecision followUp2 = router.route("What about TKDL?", Language.EN, Jurisdiction.AUTO, tkContext);
        assertThat(followUp2.route()).as("TK follow-up: What about TKDL?").isEqualTo(QueryRoute.DOMAIN_RAG);

        // 3. Trademark follow-up
        RoutingDecision tmDecision = router.route("What is a trademark?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty());
        assertThat(tmDecision.route()).isEqualTo(QueryRoute.DOMAIN_RAG);
        RoutingContext tmContext = new RoutingContext(tmDecision.route(), tmDecision.domain());
        RoutingDecision followUp3 = router.route("How long does it last?", Language.EN, Jurisdiction.AUTO, tmContext);
        assertThat(followUp3.route()).as("Trademark follow-up: How long does it last?").isEqualTo(QueryRoute.DOMAIN_RAG);
        assertThat(followUp3.domain()).isEqualTo(QueryDomain.TRADEMARK);

        // ===== NEGATIVE / FALSE-POSITIVE PROTECTION =====
        assertThat(router.route("What are Python coding best practices?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Python coding").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("What is Java?", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Java").isEqualTo(QueryRoute.GENERAL);
        assertThat(router.route("Explain recursion", Language.EN, Jurisdiction.AUTO, RoutingContext.empty()).route()).as("Recursion").isEqualTo(QueryRoute.GENERAL);
    }

    private List<Case> loadCases() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/intelligent-routing-evaluation.csv"), StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> {
                int comma = line.indexOf(',');
                return new Case(QueryRoute.valueOf(line.substring(0, comma)), line.substring(comma + 1));
            }).toList();
        }
    }

    private record Case(QueryRoute expected, String query) {}
}
