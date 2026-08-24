package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.MetricStats;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CandidateGeneratorTest {

    private SkuCatalogueService skuCatalogueService;
    private CandidateGenerator generator;

    @BeforeEach
    void setUp() {
        skuCatalogueService = Mockito.mock(SkuCatalogueService.class);
        generator = new CandidateGenerator(skuCatalogueService);
    }

    @Test
    void lowUtilisation_returnsDownsizeAndCrossGeneration() {
        VmSkuCatalogueEntry current = sku("Standard_D2s_v5", "CURRENT", 2, "8.00");
        VmSkuCatalogueEntry smaller = sku("Standard_B1ms", "CURRENT", 1, "2.00");
        VmSkuCatalogueEntry olderSameSize = sku("Standard_D2s_v3", "OLDER_SUPPORTED", 2, "8.00");

        when(skuCatalogueService.findBySkuName("Standard_D2s_v5")).thenReturn(Optional.of(current));
        when(skuCatalogueService.findCandidates(2, 8.0)).thenReturn(List.of(current, smaller, olderSameSize));

        UtilisationSummary summary = summaryWith(15.0, 20.0);

        List<CandidateSku> candidates = generator.generateCandidates("Standard_D2s_v5", summary);

        assertThat(candidates).extracting(CandidateSku::recommendationType)
            .contains(CandidateSku.DOWNSIZE, CandidateSku.CROSS_GENERATION);
    }

    @Test
    void highUtilisation_returnsUpsize() {
        VmSkuCatalogueEntry current = sku("Standard_D2s_v5", "CURRENT", 2, "8.00");
        VmSkuCatalogueEntry larger = sku("Standard_D4s_v5", "CURRENT", 4, "16.00");

        when(skuCatalogueService.findBySkuName("Standard_D2s_v5")).thenReturn(Optional.of(current));
        when(skuCatalogueService.findCandidates(2, 8.0)).thenReturn(List.of(current, larger));

        UtilisationSummary summary = summaryWith(92.0, 92.0);

        List<CandidateSku> candidates = generator.generateCandidates("Standard_D2s_v5", summary);

        assertThat(candidates).extracting(CandidateSku::recommendationType)
            .contains(CandidateSku.UPSIZE);
    }

    @Test
    void excludesCandidatesBelowHeadroomThreshold() {
        VmSkuCatalogueEntry current = sku("Standard_D2s_v5", "CURRENT", 2, "8.00");
        // Same size candidate at high utilisation offers ~8% headroom (100-92), below the 20% minimum.
        VmSkuCatalogueEntry sameSize = sku("Standard_D2s_v3", "OLDER_SUPPORTED", 2, "8.00");

        when(skuCatalogueService.findBySkuName("Standard_D2s_v5")).thenReturn(Optional.of(current));
        when(skuCatalogueService.findCandidates(2, 8.0)).thenReturn(List.of(current, sameSize));

        UtilisationSummary summary = summaryWith(92.0, 92.0);

        List<CandidateSku> candidates = generator.generateCandidates("Standard_D2s_v5", summary);

        assertThat(candidates).isEmpty();
    }

    @Test
    void unknownCurrentSku_returnsEmptyList() {
        when(skuCatalogueService.findBySkuName("Unknown_Sku")).thenReturn(Optional.empty());

        List<CandidateSku> candidates = generator.generateCandidates("Unknown_Sku", summaryWith(50.0, 50.0));

        assertThat(candidates).isEmpty();
    }

    private UtilisationSummary summaryWith(double cpuP95, double memP95) {
        return new UtilisationSummary(
            1L,
            Map.of(
                "percentage_cpu", new MetricStats(cpuP95, cpuP95, cpuP95, cpuP95),
                "memory_percentage", new MetricStats(memP95, memP95, memP95, memP95)
            ),
            10,
            OffsetDateTime.now().minusDays(14),
            OffsetDateTime.now()
        );
    }

    private VmSkuCatalogueEntry sku(String name, String generation, int vcpu, String memoryGb) {
        VmSkuCatalogueEntry e = new VmSkuCatalogueEntry();
        e.setArmSkuName(name);
        e.setVmFamily("test");
        e.setGeneration(generation);
        e.setVcpuCount(vcpu);
        e.setMemoryGb(new BigDecimal(memoryGb));
        e.setSupportStatus("SUPPORTED");
        return e;
    }
}
