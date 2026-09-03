package com.ipsakti.ip_sakti_backend.tk.analysis;

import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapClassification;
import com.ipsakti.ip_sakti_backend.tk.model.TkOverlapType;
import java.util.List;

public record TkAssessment(
        TkOverlapClassification classification,
        double confidence,
        List<TkOverlapType> overlapTypes,
        boolean abstained,
        String explanation,
        String recommendation
) {
}
