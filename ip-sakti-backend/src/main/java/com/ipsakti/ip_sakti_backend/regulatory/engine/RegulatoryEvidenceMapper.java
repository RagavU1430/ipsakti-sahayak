package com.ipsakti.ip_sakti_backend.regulatory.engine;

import com.ipsakti.ip_sakti_backend.question.model.QuestionCitation;
import com.ipsakti.ip_sakti_backend.question.model.QuestionSource;
import com.ipsakti.ip_sakti_backend.rag.dto.RagCitation;
import com.ipsakti.ip_sakti_backend.rag.dto.RagSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryEvidenceMapper {

    public List<QuestionCitation> citations(List<RagCitation> citations) {
        return citations.stream()
                .map(citation -> new QuestionCitation(
                        citation.document(),
                        citation.documentId(),
                        citation.page(),
                        citation.section(),
                        citation.authority(),
                        citation.sourceUrl(),
                        citation.chunkId()
                ))
                .toList();
    }

    public List<QuestionSource> sources(List<RagSource> sources) {
        return sources.stream()
                .map(source -> new QuestionSource(source.documentId(), source.score()))
                .toList();
    }
}
