package com.cloudfinsight.collectorservice.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "collector.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisScheduler {

    @Scheduled(fixedDelayString = "${collector.analysis.interval-ms}")
    public void runAnalysis() {
        log.info("AnalysisScheduler fired — recommendation engine pipeline not yet implemented (Epic 4)");
    }
}
