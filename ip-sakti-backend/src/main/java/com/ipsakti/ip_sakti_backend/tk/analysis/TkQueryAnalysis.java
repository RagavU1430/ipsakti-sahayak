package com.ipsakti.ip_sakti_backend.tk.analysis;

import java.util.List;

public record TkQueryAnalysis(
        List<String> ingredients,
        List<String> traditionalUseTerms,
        List<String> preparationMethods,
        List<String> geographicIndicators,
        List<String> tkIndicators,
        List<String> biologicalResources
) {

    public int signalCount() {
        return ingredients.size()
                + traditionalUseTerms.size()
                + preparationMethods.size()
                + geographicIndicators.size()
                + tkIndicators.size()
                + biologicalResources.size();
    }
}
