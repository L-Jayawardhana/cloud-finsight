package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.Recommendation;
import com.cloudfinsight.collectorservice.entity.RecommendationCandidate;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.model.CandidateSavings;
import com.cloudfinsight.collectorservice.model.ScoredCandidate;
import com.cloudfinsight.collectorservice.model.SavingsEstimate;
import com.cloudfinsight.collectorservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.collectorservice.repository.RecommendationRepository;
import com.cloudfinsight.collectorservice.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationPackager {

    private final VirtualMachineRepository virtualMachineRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;

    public Optional<Recommendation> packageRecommendation(Long vmId, List<ScoredCandidate> scoredCandidates,
                                                            List<CandidateSavings> savingsList) {
        if (scoredCandidates.isEmpty()) {
            return Optional.empty();
        }

        Optional<VirtualMachine> vmOpt = virtualMachineRepository.findById(vmId);
        if (vmOpt.isEmpty()) {
            return Optional.empty();
        }

        ScoredCandidate top = scoredCandidates.get(0);
        Optional<SavingsEstimate> topSavings = findSavingsFor(top, savingsList);
        if (topSavings.isEmpty()) {
            return Optional.empty();
        }

        Recommendation recommendation = new Recommendation();
        recommendation.setVirtualMachine(vmOpt.get());
        recommendation.setRecommendationType(top.candidate().recommendationType());
        recommendation.setConfidenceLevel(top.confidenceLevel());
        recommendation.setConfidenceScore(BigDecimal.valueOf(top.compositeScore()));
        recommendation.setEstimatedMonthlySavings(BigDecimal.valueOf(topSavings.get().monthlySaving()));
        recommendation.setSummary(buildSummary(top));

        Recommendation saved = recommendationRepository.save(recommendation);

        recommendationCandidateRepository.save(
            toEntity(saved, top, topSavings.get(), true));

        if (scoredCandidates.size() > 1) {
            ScoredCandidate runnerUp = scoredCandidates.get(1);
            findSavingsFor(runnerUp, savingsList).ifPresent(ruSavings ->
                recommendationCandidateRepository.save(toEntity(saved, runnerUp, ruSavings, false)));
        }

        return Optional.of(saved);
    }

    private Optional<SavingsEstimate> findSavingsFor(ScoredCandidate scored, List<CandidateSavings> savingsList) {
        String skuName = scored.candidate().sku().getArmSkuName();
        return savingsList.stream()
            .filter(cs -> cs.candidate().sku().getArmSkuName().equals(skuName))
            .map(CandidateSavings::savings)
            .findFirst();
    }

    private RecommendationCandidate toEntity(Recommendation recommendation, ScoredCandidate scored,
                                              SavingsEstimate savings, boolean selected) {
        RecommendationCandidate entity = new RecommendationCandidate();
        entity.setRecommendation(recommendation);
        entity.setCandidateSku(scored.candidate().sku().getArmSkuName());
        entity.setGenerationTag(scored.candidate().sku().getGeneration());
        entity.setEstimatedMonthlyCost(BigDecimal.valueOf(savings.candidateMonthlyPrice()));
        entity.setReliabilityScore(BigDecimal.valueOf(scored.reliabilityScore()));
        entity.setPerformanceScore(BigDecimal.valueOf(scored.performanceScore()));
        entity.setPros(buildPros(scored, savings));
        entity.setCons(buildCons(scored, savings));
        entity.setSelected(selected);
        return entity;
    }

    private String buildSummary(ScoredCandidate top) {
        return String.format("%s to %s recommended based on %s confidence analysis",
            top.candidate().recommendationType(), top.candidate().sku().getArmSkuName(), top.confidenceLevel());
    }

    private String buildPros(ScoredCandidate scored, SavingsEstimate savings) {
        List<String> pros = new ArrayList<>();
        if (savings.monthlySaving() > 0) {
            pros.add(String.format("Estimated saving of %.2f %s/month (%.1f%%)",
                savings.monthlySaving(), savings.currencyCode(), savings.savingPercent()));
        }
        if ("CURRENT".equals(scored.candidate().sku().getGeneration())) {
            pros.add("Current generation SKU with full support");
        }
        if (scored.performanceScore() >= 0.5) {
            pros.add("Comfortable performance headroom above observed p95 usage");
        }
        if (savings.twoInstanceFeasible()) {
            pros.add(String.format("Two instances of this SKU cost less than one current instance "
                + "(saving %.2f %s/month)", savings.twoInstanceMonthlySaving(), savings.currencyCode()));
        }
        return String.join("\n", pros);
    }

    private String buildCons(ScoredCandidate scored, SavingsEstimate savings) {
        List<String> cons = new ArrayList<>();
        if ("OLDER_SUPPORTED".equals(scored.candidate().sku().getGeneration())) {
            cons.add("Older-generation hardware; reliability score reduced accordingly");
        }
        if (savings.monthlySaving() < 0) {
            cons.add(String.format("Increases monthly cost by %.2f %s",
                Math.abs(savings.monthlySaving()), savings.currencyCode()));
        }
        if (ScoredCandidate.LOW.equals(scored.confidenceLevel())) {
            cons.add("Recommendation based on limited historical data (LOW confidence)");
        }
        return String.join("\n", cons);
    }
}
