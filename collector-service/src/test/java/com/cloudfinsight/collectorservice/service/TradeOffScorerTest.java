package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.cache.PricingCacheService;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.MetricStats;
import com.cloudfinsight.collectorservice.model.ScoredCandidate;
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

class TradeOffScorerTest {

    private PricingCacheService pricingCacheService;
    private TradeOffScorer scorer;

    @BeforeEach
    void setUp() throws Exception {
        pricingCacheService = Mockito.mock(PricingCacheService.class);
        scorer = new TradeOffScorer(pricingCacheService);

        setField("costWeight", 0.5);
        setField("reliabilityWeight", 0.3);
        setField("performanceWeight", 0.2);
        setField("region", "southeastasia");
    }

    private void setField(String name, Object value) throws Exception {
        var field = TradeOffScorer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(scorer, value);
    }

    @Test
    void costScore_cheaperCandidate_scoresAboveOne() {
        RetailPricingRecord current = pricing("Standard_D2s_v5", "0.20");
        RetailPricingRecord candidate = pricing("Standard_B1ms", "0.10");

        double score = scorer.costScore(current, candidate);

        assertThat(score).isEqualTo(2.0);
    }

    @Test
    void costScore_moreExpensiveCandidate_scoresBelowOne() {
        RetailPricingRecord current = pricing("Standard_D2s_v5", "0.20");
        RetailPricingRecord candidate = pricing("Standard_D4s_v5", "0.40");

        double score = scorer.costScore(current, candidate);

        assertThat(score).isEqualTo(0.5);
    }

    @Test
    void reliabilityScore_currentGeneration_scoresOne() {
        CandidateSku candidate = candidate(sku("Standard_D2s_v5", "CURRENT", "SUPPORTED"), CandidateSku.UPSIZE);

        assertThat(scorer.reliabilityScore(candidate)).isEqualTo(1.0);
    }

    @Test
    void reliabilityScore_olderGeneration_scoresPointEightFive() {
        CandidateSku candidate = candidate(sku("Standard_D2s_v3", "OLDER_SUPPORTED", "SUPPORTED"), CandidateSku.CROSS_GENERATION);

        assertThat(scorer.reliabilityScore(candidate)).isEqualTo(0.85);
    }

    @Test
    void reliabilityScore_unsupportedStatus_appliesPenalty() {
        CandidateSku candidate = candidate(sku("Standard_D2s_v3", "OLDER_SUPPORTED", "DEPRECATED"), CandidateSku.CROSS_GENERATION);

        assertThat(scorer.reliabilityScore(candidate)).isEqualTo(0.425); // 0.85 * 0.5
    }

    @Test
    void performanceScore_usesMinimumOfCpuAndMemoryHeadroom() {
        CandidateSku candidate = new CandidateSku(
            sku("Standard_D4s_v5", "CURRENT", "SUPPORTED"), CandidateSku.UPSIZE, 60.0, 30.0);

        assertThat(scorer.performanceScore(candidate)).isEqualTo(0.30);
    }

    @Test
    void score_ordersCheaperSameGenerationDownsizeAboveCrossGenerationWhenDataSparse() {
        UtilisationSummary sparseSummary = summaryWithDays(3); // LOW confidence

        CandidateSku cheapDownsize = new CandidateSku(
            sku("Standard_B1ms", "CURRENT", "SUPPORTED"), CandidateSku.DOWNSIZE, 60.0, 60.0);
        CandidateSku crossGen = new CandidateSku(
            sku("Standard_D2s_v3", "OLDER_SUPPORTED", "SUPPORTED"), CandidateSku.CROSS_GENERATION, 40.0, 40.0);

        when(pricingCacheService.get("Standard_D2s_v5", "southeastasia"))
            .thenReturn(Optional.of(pricing("Standard_D2s_v5", "0.20")));
        when(pricingCacheService.get("Standard_B1ms", "southeastasia"))
            .thenReturn(Optional.of(pricing("Standard_B1ms", "0.05"))); // much cheaper
        when(pricingCacheService.get("Standard_D2s_v3", "southeastasia"))
            .thenReturn(Optional.of(pricing("Standard_D2s_v3", "0.18"))); // barely cheaper

        List<ScoredCandidate> results = scorer.score(
            "Standard_D2s_v5", List.of(crossGen, cheapDownsize), sparseSummary);

        assertThat(results.get(0).candidate().recommendationType()).isEqualTo(CandidateSku.DOWNSIZE);
        assertThat(results.get(0).confidenceLevel()).isEqualTo(ScoredCandidate.LOW);
    }

    @Test
    void score_missingCurrentPricing_returnsEmptyList() {
        when(pricingCacheService.get("Standard_D2s_v5", "southeastasia")).thenReturn(Optional.empty());

        List<ScoredCandidate> results = scorer.score("Standard_D2s_v5", List.of(), summaryWithDays(14));

        assertThat(results).isEmpty();
    }

    private UtilisationSummary summaryWithDays(int days) {
        return new UtilisationSummary(1L, Map.of(), 10,
            OffsetDateTime.now().minusDays(days), OffsetDateTime.now());
    }

    private RetailPricingRecord pricing(String skuName, String price) {
        return new RetailPricingRecord(skuName, "southeastasia", new BigDecimal(price), "USD",
            "product", "meter", "Virtual Machines", "Consumption", OffsetDateTime.now());
    }

    private VmSkuCatalogueEntry sku(String name, String generation, String supportStatus) {
        VmSkuCatalogueEntry e = new VmSkuCatalogueEntry();
        e.setArmSkuName(name);
        e.setVmFamily("test");
        e.setGeneration(generation);
        e.setVcpuCount(2);
        e.setMemoryGb(new BigDecimal("8.00"));
        e.setSupportStatus(supportStatus);
        return e;
    }

    private CandidateSku candidate(VmSkuCatalogueEntry sku, String type) {
        return new CandidateSku(sku, type, 50.0, 50.0);
    }
}
