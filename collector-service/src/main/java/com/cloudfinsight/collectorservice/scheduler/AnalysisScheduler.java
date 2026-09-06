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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the actual recommendation-engine cycle (candidate generation -> scoring ->
 * savings -> publish) per VM. Distinct from {@link MetricCollectionScheduler} (which
 * only gathers raw utilisation snapshots) - this is "the engine" the Task 9.1
 * collector.cycle.duration.seconds / recommendation.engine.last.run.seconds.ago
 * metrics are named after.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "collector.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisScheduler {

    private final VirtualMachineRepository virtualMachineRepository;
    private final UtilisationAggregator utilisationAggregator;
    private final CandidateGenerator candidateGenerator;
    private final TradeOffScorer tradeOffScorer;
    private final SavingsCalculator savingsCalculator;
    private final RecommendationPackager recommendationPackager;
    private final RecommendationPublisher recommendationPublisher;
    private final MeterRegistry meterRegistry;
    private final Timer cycleDurationTimer;
    private final AtomicLong lastRunEpochSecond = new AtomicLong(0);

    public AnalysisScheduler(VirtualMachineRepository virtualMachineRepository,
                              UtilisationAggregator utilisationAggregator,
                              CandidateGenerator candidateGenerator,
                              TradeOffScorer tradeOffScorer,
                              SavingsCalculator savingsCalculator,
                              RecommendationPackager recommendationPackager,
                              RecommendationPublisher recommendationPublisher,
                              MeterRegistry meterRegistry) {
        this.virtualMachineRepository = virtualMachineRepository;
        this.utilisationAggregator = utilisationAggregator;
        this.candidateGenerator = candidateGenerator;
        this.tradeOffScorer = tradeOffScorer;
        this.savingsCalculator = savingsCalculator;
        this.recommendationPackager = recommendationPackager;
        this.recommendationPublisher = recommendationPublisher;
        this.meterRegistry = meterRegistry;
        this.cycleDurationTimer = Timer.builder("collector.cycle.duration.seconds")
            .description("Duration of a full recommendation analysis cycle across all VMs")
            .tag("cycle", "analysis")
            .register(meterRegistry);
        // Pull-based gauge (like QueueMetricsConfig): computed at scrape time from the
        // last-run timestamp, rather than pushed on a schedule.
        Gauge.builder("recommendation.engine.last.run.seconds.ago", lastRunEpochSecond,
                ts -> ts.get() == 0 ? -1 : Instant.now().getEpochSecond() - ts.get())
            .description("Seconds since the recommendation engine last completed a full cycle "
                + "(-1 if it has never run)")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${collector.analysis.interval-ms}")
    public void runAnalysis() {
        cycleDurationTimer.record(this::runAnalysisCycle);
    }

    private void runAnalysisCycle() {
        List<VirtualMachine> vms = virtualMachineRepository.findAll();
        log.info("Starting recommendation analysis cycle for {} registered VM(s)", vms.size());

        for (VirtualMachine vm : vms) {
            try {
                analyseVm(vm);
            } catch (Exception ex) {
                log.warn("Recommendation analysis failed for VM {}: {}", vm.getName(), ex.getMessage());
                meterRegistry.counter("collector.errors.total", "source", "analysis").increment();
            }
        }

        lastRunEpochSecond.set(Instant.now().getEpochSecond());
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