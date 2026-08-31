package com.ipsakti.ip_sakti_backend.regulatory;

import com.ipsakti.ip_sakti_backend.question.model.Jurisdiction;
import com.ipsakti.ip_sakti_backend.regulatory.engine.AbsAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.GratkAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.RegulatoryJurisdictionRouter;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3eAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.engine.Section3pAnalysisService;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisRequest;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryAnalysisResponse;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryEngineResult;
import com.ipsakti.ip_sakti_backend.regulatory.model.RegulatoryStatus;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RegulatoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryAnalysisService.class);

    private final RegulatoryJurisdictionRouter jurisdictionRouter;
    private final Section3pAnalysisService section3p;
    private final Section3eAnalysisService section3e;
    private final AbsAnalysisService abs;
    private final GratkAnalysisService gratk;

    public RegulatoryAnalysisService(RegulatoryJurisdictionRouter jurisdictionRouter, Section3pAnalysisService section3p,
                                     Section3eAnalysisService section3e, AbsAnalysisService abs, GratkAnalysisService gratk) {
        this.jurisdictionRouter = jurisdictionRouter;
        this.section3p = section3p;
        this.section3e = section3e;
        this.abs = abs;
        this.gratk = gratk;
    }

    public RegulatoryAnalysisResponse analyze(RegulatoryAnalysisRequest request) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        Jurisdiction jurisdiction = jurisdictionRouter.resolve(request);
        if (jurisdictionRouter.ambiguous(jurisdiction)) {
            return new RegulatoryAnalysisResponse(
                    Jurisdiction.AUTO,
                    RegulatoryStatus.INSUFFICIENT_EVIDENCE,
                    List.of(),
                    0.2,
                    true,
                    List.of("Which jurisdiction are you seeking guidance for: India or international?"),
                    "Jurisdiction could not be safely inferred, so regulatory analysis was not run."
            );
        }

        List<RegulatoryEngineResult> engines = List.of(
                section3p.analyze(request, jurisdiction),
                section3e.analyze(request, jurisdiction),
                abs.analyze(request, jurisdiction),
                gratk.analyze(request, jurisdiction)
        );
        boolean needsClarification = engines.stream().anyMatch(result ->
                result.status() == RegulatoryStatus.INSUFFICIENT_EVIDENCE
                        || result.reason().toLowerCase().contains("conflicting")
                        || result.reason().toLowerCase().contains("incomplete")
                        || result.considerations().stream()
                        .anyMatch(consideration -> consideration.toLowerCase().contains("incomplete")));
        RegulatoryStatus overall = overallStatus(engines);
        double confidence = Math.round(engines.stream().mapToDouble(RegulatoryEngineResult::confidence).average().orElse(0.0) * 10000.0) / 10000.0;
        RegulatoryAnalysisResponse response = new RegulatoryAnalysisResponse(
                jurisdiction,
                overall,
                engines,
                confidence,
                needsClarification,
                needsClarification ? List.of("Please clarify any missing or conflicting traditional-knowledge, biological-resource, origin, or technical-effect facts.") : List.of(),
                "This is an evidence-backed decision-support summary, not final legal advice."
        );
        log.info("regulatory_analysis_ready requestId={} jurisdiction={} overallStatus={} confidence={} engineCount={} latencyMs={}",
                requestId, jurisdiction, overall, confidence, engines.size(), Duration.ofNanos(System.nanoTime() - started).toMillis());
        return response;
    }

    private RegulatoryStatus overallStatus(List<RegulatoryEngineResult> engines) {
        if (engines.stream().anyMatch(result -> result.status() == RegulatoryStatus.REVIEW_RECOMMENDED)) {
            return RegulatoryStatus.REVIEW_RECOMMENDED;
        }
        if (engines.stream().anyMatch(result -> result.status() == RegulatoryStatus.POTENTIALLY_APPLICABLE)) {
            return RegulatoryStatus.POTENTIALLY_APPLICABLE;
        }
        return engines.stream()
                .map(RegulatoryEngineResult::status)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(RegulatoryStatus.INSUFFICIENT_EVIDENCE);
    }
}
