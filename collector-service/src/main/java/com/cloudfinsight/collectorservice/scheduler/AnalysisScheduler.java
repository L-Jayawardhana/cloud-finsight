package com.cloudfinsight.collectorservice.scheduler;

import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.model.CandidateSavings;
import com.cloudfinsight.collectorservice.model.CandidateSku;
import com.cloudfinsight.collectorservice.model.ScoredCandidate;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import com.cloudfinsight.collectorservice.repository.VirtualMachineRepository;
import com.cloudfinsight.collectorservice.service.CandidateGenerator;
import com.cloudfinsight.collectorservice.service.RecommendationPackager;
import com.cloudfinsight.collectorservice.service.SavingsCalculator;
import com.cloudfinsight.collectorservice.service.TradeOffScorer;
import com.cloudfinsight.collectorservice.service.UtilisationAggregator;
import com.cloudfinsight.collectorservice.messaging.RecommendationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collector.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisScheduler {

    private final VirtualMachineRepository virtualMachineRepository;
    private final UtilisationAggregator utilisationAggregator;
    private final CandidateGenerator candidateGenerator;
    private final TradeOffScorer tradeOffScorer;
    private final SavingsCalculator savingsCalculator;
    private final RecommendationPackager recommendationPackager;
    private final RecommendationPublisher recommendationPublisher;

    @Scheduled(fixedDelayString = "${collector.analysis.interval-ms}")
    public void runAnalysis() {
        List<VirtualMachine> vms = virtualMachineRepository.findAll();
        log.info("Starting recommendation analysis cycle for {} registered VM(s)", vms.size());

        for (VirtualMachine vm : vms) {
            try {
                analyseVm(vm);
            } catch (Exception ex) {
                log.warn("Recommendation analysis failed for VM {}: {}", vm.getName(), ex.getMessage());
            }
        }

        log.info("Recommendation analysis cycle complete");
    }

    private void analyseVm(VirtualMachine vm) {
        UtilisationSummary summary = utilisationAggregator.summarise(vm.getId());
        if (!summary.hasData()) {
            log.info("No utilisation data yet for VM {}, skipping analysis", vm.getName());
            return;
        }

        persistUtilisationSnapshot(vm, summary);

        List<CandidateSku> candidates = candidateGenerator.generateCandidates(vm.getCurrentSku(), summary);
        if (candidates.isEmpty()) {
            log.info("No candidates cleared the headroom threshold for VM {}", vm.getName());
            return;
        }

        List<ScoredCandidate> scored = tradeOffScorer.score(vm.getCurrentSku(), candidates, summary);
        if (scored.isEmpty()) {
            log.info("No candidates could be scored for VM {} (missing pricing data)", vm.getName());
            return;
        }

        List<CandidateSavings> savings = savingsCalculator.calculateSavings(vm.getCurrentSku(), candidates);

        recommendationPackager.packageRecommendation(vm.getId(), scored, savings)
            .ifPresentOrElse(
                rec -> {
                    log.info("Persisted {} recommendation for VM {}: {} saving/month",
                        rec.getRecommendationType(), vm.getName(), rec.getEstimatedMonthlySavings());
                    recommendationPublisher.publish(rec);
                },
                () -> log.info("No recommendation persisted for VM {} (missing pricing for top candidate)",
                    vm.getName())
            );
    }

    private void persistUtilisationSnapshot(VirtualMachine vm, UtilisationSummary summary) {
        summary.get("percentage_cpu").ifPresent(stats ->
            vm.setP95CpuPercent(BigDecimal.valueOf(stats.p95())));
        summary.get("memory_percentage").ifPresent(stats ->
            vm.setP95MemPercent(BigDecimal.valueOf(stats.p95())));
        virtualMachineRepository.save(vm);
    }
}