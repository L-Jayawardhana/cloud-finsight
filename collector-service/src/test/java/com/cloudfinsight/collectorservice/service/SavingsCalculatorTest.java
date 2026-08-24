package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.cache.PricingCacheService;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.entity.VmSkuCatalogueEntry;
import com.cloudfinsight.collectorservice.model.CandidateSavings;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.SavingsEstimate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SavingsCalculatorTest {

    private PricingCacheService pricingCacheService;
    private SavingsCalculator calculator;

    @BeforeEach
    void setUp() throws Exception {
        pricingCacheService = Mockito.mock(PricingCacheService.class);
        calculator = new SavingsCalculator(pricingCacheService);

        var field = SavingsCalculator.class.getDeclaredField("region");
        field.setAccessible(true);
        field.set(calculator, "southeastasia");
    }

    @Test
    void calculate_singleResize_computesMonthlySavingAndPercent() {
        // 100/month = ~0.1370/hr, 60/month = ~0.0822/hr, saving = 40/month = 40%
        RetailPricingRecord current = pricing("Standard_D2s_v5", 100.0 / 730.0);
        RetailPricingRecord candidate = pricing("Standard_B1ms", 60.0 / 730.0);

        SavingsEstimate estimate = calculator.calculate(current, candidate);

        assertThat(estimate.monthlySaving()).isCloseTo(40.0, within(0.01));
        assertThat(estimate.savingPercent()).isCloseTo(40.0, within(0.1));
    }

    @Test
    void calculate_dualInstanceFeasible_matchesAcceptanceCriterion() {
        // The exact scenario from Task 4.4's acceptance criteria: 100/month current,
        // 40/month older-gen equivalent -> twoInstanceFeasible=true, saving=20/month.
        RetailPricingRecord current = pricing("Standard_D2s_v5", 100.0 / 730.0);
        RetailPricingRecord olderGen = pricing("Standard_D2s_v3", 40.0 / 730.0);

        SavingsEstimate estimate = calculator.calculate(current, olderGen);

        assertThat(estimate.twoInstanceFeasible()).isTrue();
        assertThat(estimate.twoInstanceMonthlyCost()).isCloseTo(80.0, within(0.01));
        assertThat(estimate.twoInstanceMonthlySaving()).isCloseTo(20.0, within(0.01));
    }

    @Test
    void calculate_dualInstanceNotFeasible_whenTwoCandidatesCostMoreThanCurrent() {
        // Current 100/month, candidate 60/month -> 2x candidate = 120/month, exceeds current.
        RetailPricingRecord current = pricing("Standard_D2s_v5", 100.0 / 730.0);
        RetailPricingRecord candidate = pricing("Standard_B1ms", 60.0 / 730.0);

        SavingsEstimate estimate = calculator.calculate(current, candidate);

        assertThat(estimate.twoInstanceFeasible()).isFalse();
    }

    @Test
    void calculate_upsize_producesNegativeSaving() {
        RetailPricingRecord current = pricing("Standard_D2s_v5", 100.0 / 730.0);
        RetailPricingRecord larger = pricing("Standard_D4s_v5", 200.0 / 730.0);

        SavingsEstimate estimate = calculator.calculate(current, larger);

        assertThat(estimate.monthlySaving()).isCloseTo(-100.0, within(0.01));
    }

    @Test
    void calculateSavings_missingCurrentPricing_returnsEmptyList() {
        when(pricingCacheService.get("Standard_D2s_v5", "southeastasia")).thenReturn(Optional.empty());

        List<CandidateSavings> results = calculator.calculateSavings("Standard_D2s_v5", List.of());

        assertThat(results).isEmpty();
    }

    @Test
    void calculateSavings_skipsCandidatesWithMissingPricing() {
        VmSkuCatalogueEntry candidateSku = sku("Standard_B1ms");
        CandidateSku candidate = new CandidateSku(candidateSku, CandidateSku.DOWNSIZE, 50.0, 50.0);

        when(pricingCacheService.get("Standard_D2s_v5", "southeastasia"))
            .thenReturn(Optional.of(pricing("Standard_D2s_v5", 100.0 / 730.0)));
        when(pricingCacheService.get("Standard_B1ms", "southeastasia")).thenReturn(Optional.empty());

        List<CandidateSavings> results = calculator.calculateSavings("Standard_D2s_v5", List.of(candidate));

        assertThat(results).isEmpty();
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }

    private RetailPricingRecord pricing(String skuName, double hourlyPrice) {
        return new RetailPricingRecord(skuName, "southeastasia", BigDecimal.valueOf(hourlyPrice), "USD",
            "product", "meter", "Virtual Machines", "Consumption", OffsetDateTime.now());
    }

    private VmSkuCatalogueEntry sku(String name) {
        VmSkuCatalogueEntry e = new VmSkuCatalogueEntry();
        e.setArmSkuName(name);
        e.setVmFamily("test");
        e.setGeneration("CURRENT");
        e.setVcpuCount(1);
        e.setMemoryGb(new BigDecimal("2.00"));
        e.setSupportStatus("SUPPORTED");
        return e;
    }
}
