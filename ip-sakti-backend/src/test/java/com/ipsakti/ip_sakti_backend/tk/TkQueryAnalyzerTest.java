package com.ipsakti.ip_sakti_backend.tk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalysis;
import com.ipsakti.ip_sakti_backend.tk.analysis.TkQueryAnalyzer;
import org.junit.jupiter.api.Test;

class TkQueryAnalyzerTest {

    private final TkQueryAnalyzer analyzer = new TkQueryAnalyzer();

    @Test
    void extractsTraditionalKnowledgeSignalsWithoutInventingEntities() {
        TkQueryAnalysis analysis = analyzer.analyze(
                "A turmeric and neem herbal extract based on Ayurvedic traditional use in India prepared as a decoction."
        );

        assertThat(analysis.ingredients()).contains("turmeric", "neem", "herb", "extract");
        assertThat(analysis.traditionalUseTerms()).contains("ayurvedic", "traditional use");
        assertThat(analysis.preparationMethods()).contains("decoction", "extract");
        assertThat(analysis.geographicIndicators()).contains("india");
        assertThat(analysis.biologicalResources()).contains("plant_or_botanical_resource");
    }

    @Test
    void sparseIndustrialDescriptionDoesNotManufactureTkSignals() {
        TkQueryAnalysis analysis = analyzer.analyze("A mechanical hinge with a metal spring and locking pin.");

        assertThat(analysis.ingredients()).isEmpty();
        assertThat(analysis.traditionalUseTerms()).isEmpty();
        assertThat(analysis.preparationMethods()).isEmpty();
        assertThat(analysis.tkIndicators()).isEmpty();
    }
}
