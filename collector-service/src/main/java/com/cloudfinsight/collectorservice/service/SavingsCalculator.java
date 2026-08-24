package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.cache.PricingCacheService;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.model.CandidateSavings;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.SavingsEstimate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavingsCalculator {

    private static final double HOURS_PER_MONTH = 730.0;

    @Value("${collector.pricing.region}")
    private String region;

    private final PricingCacheService pricingCacheService;

    public List<CandidateSavings> calculateSavings(String currentArmSkuName, List<CandidateSku> candidates) {
        Optional<RetailPricingRecord> currentPricing = pricingCacheService.get(currentArmSkuName, region);
        if (currentPricing.isEmpty()) {
            return List.of();
        }

        List<CandidateSavings> results = new ArrayList<>();
        for (CandidateSku candidate : candidates) {
            Optional<RetailPricingRecord> candidatePricing =
                pricingCacheService.get(candidate.sku().getArmSkuName(), region);
            if (candidatePricing.isEmpty()) {
                continue;
            }

            SavingsEstimate estimate = calculate(currentPricing.get(), candidatePricing.get());
            results.add(new CandidateSavings(candidate, estimate));
        }

        return results;
    }

    SavingsEstimate calculate(RetailPricingRecord current, RetailPricingRecord candidate) {
        double currentMonthly = current.retailPrice().doubleValue() * HOURS_PER_MONTH;
        double candidateMonthly = candidate.retailPrice().doubleValue() * HOURS_PER_MONTH;

        double monthlySaving = currentMonthly - candidateMonthly;
        double savingPercent = currentMonthly > 0 ? (monthlySaving / currentMonthly) * 100.0 : 0.0;

        double twoInstanceMonthlyCost = 2 * candidateMonthly;
        boolean twoInstanceFeasible = twoInstanceMonthlyCost <= currentMonthly;
        double twoInstanceMonthlySaving = currentMonthly - twoInstanceMonthlyCost;

        return new SavingsEstimate(
            current.currencyCode(),
            currentMonthly,
            candidateMonthly,
            monthlySaving,
            savingPercent,
            twoInstanceFeasible,
            twoInstanceMonthlyCost,
            twoInstanceMonthlySaving
        );
    }
}
