package com.cloudfinsight.collectorservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter collectionCycleSuccessCounter(MeterRegistry registry) {
        return Counter.builder("collector.cycle.success")
            .description("Number of successful Azure Monitor/Pricing collection cycles")
            .register(registry);
    }

    @Bean
    public Counter collectionCycleFailureCounter(MeterRegistry registry) {
        return Counter.builder("collector.cycle.failure")
            .description("Number of failed Azure Monitor/Pricing collection cycles")
            .register(registry);
    }

    @Bean
    public AtomicLong lastCollectionCycleTimestamp(MeterRegistry registry) {
        AtomicLong timestamp = new AtomicLong(0);
        registry.gauge("collector.cycle.last_run_timestamp", timestamp);
        return timestamp;
    }
}
