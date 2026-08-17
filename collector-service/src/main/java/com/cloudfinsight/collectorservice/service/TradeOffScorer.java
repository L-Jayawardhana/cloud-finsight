package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.cache.PricingCacheService;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.ScoredCandidate;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradeOffScorer {

    private static final double CURRENT_GEN_RELIABILITY = 1.0;
    private static final double OLDER_GEN_RELIABILITY = 0.85;
    private static final double UNSUPPORTED_PENALTY = 0.5;

    @Value("${collector.scoring.cost-weight}")
    private double costWeight;

    @Value("${collector.scoring.reliability-weight}")
    private double reliabilityWeight;

    @Value("${collector.scoring.performance-weight}")
    private double performanceWeight;

    @Value("${collector.pricing.region}")
    private String region;

    private final PricingCacheService pricingCacheService;

    public List<ScoredCandidate> score(String currentArmSkuName, List<CandidateSku> candidates,
                                        UtilisationSummary summary) {
        String confidenceLevel = confidenceLevel(summary);

        Optional<RetailPricingRecord> currentPricing = pricingCacheService.get(currentArmSkuName, region);
        if (currentPricing.isEmpty()) {
            return List.of();
        }

        List<ScoredCandidate> scored = new ArrayList<>();
        for (CandidateSku candidate : candidates) {
            Optional<RetailPricingRecord> candidatePricing =
                pricingCacheService.get(candidate.sku().getArmSkuName(), region);
            if (candidatePricing.isEmpty()) {
                continue;
            }

            double costScore = costScore(currentPricing.get(), candidatePricing.get());
            double reliabilityScore = reliabilityScore(candidate);
            double performanceScore = performanceScore(candidate);

            double composite = costScore * costWeight
                + reliabilityScore * reliabilityWeight
                + performanceScore * performanceWeight;

            scored.add(new ScoredCandidate(candidate, costScore, reliabilityScore, performanceScore,
                composite, confidenceLevel));
        }

        scored.sort((a, b) -> Double.compare(b.compositeScore(), a.compositeScore()));
        return scored;
    }

    double costScore(RetailPricingRecord current, RetailPricingRecord candidate) {
        double currentPrice = current.retailPrice().doubleValue();
        double candidatePrice = candidate.retailPrice().doubleValue();
        if (candidatePrice <= 0) {
            return 0.0;
        }
        return currentPrice / candidatePrice;
    }

    double reliabilityScore(CandidateSku candidate) {
        double base = "OLDER_SUPPORTED".equals(candidate.sku().getGeneration())
            ? OLDER_GEN_RELIABILITY
            : CURRENT_GEN_RELIABILITY;

        if (!"SUPPORTED".equals(candidate.sku().getSupportStatus())) {
            base *= UNSUPPORTED_PENALTY;
        }
        return base;
    }

    double performanceScore(CandidateSku candidate) {
        double minHeadroom = Math.min(candidate.cpuHeadroomPercent(), candidate.memoryHeadroomPercent());
        return Math.max(0.0, Math.min(1.0, minHeadroom / 100.0));
    }

    private String confidenceLevel(UtilisationSummary summary) {
        long days = summary.daysOfDataAvailable();
        if (days >= 14) {
            return ScoredCandidate.HIGH;
        }
        if (days >= 7) {
            return ScoredCandidate.MEDIUM;
        }
        return ScoredCandidate.LOW;
    }
}
