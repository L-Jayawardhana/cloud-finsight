package com.cloudfinsight.collectorservice.scheduler;

import com.cloudfinsight.collectorservice.client.AzureMonitorClient;
import com.cloudfinsight.collectorservice.entity.MetricSnapshot;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.mapper.MetricSnapshotMapper;
import com.cloudfinsight.collectorservice.repository.MetricSnapshotRepository;
import com.cloudfinsight.collectorservice.repository.VirtualMachineRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collector.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MetricCollectionScheduler {

    private final VirtualMachineRepository virtualMachineRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final AzureMonitorClient azureMonitorClient;
    private final MetricSnapshotMapper mapper;
    private final Counter collectionCycleSuccessCounter;
    private final Counter collectionCycleFailureCounter;
    private final AtomicLong lastCollectionCycleTimestamp;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${collector.interval-ms}")
    public void collectMetrics() {
        List<VirtualMachine> vms = virtualMachineRepository.findAll();
        log.info("Starting metric collection cycle for {} registered VM(s)", vms.size());

        for (VirtualMachine vm : vms) {
            try {
                List<AzureMonitorClient.RawMetricPoint> rawPoints = new ArrayList<>(
                    azureMonitorClient.queryMetrics(vm.getAzureResourceId(), Duration.ofHours(1))
                );

                try {
                    Optional<AzureMonitorClient.RawMetricPoint> memoryPoint =
                        azureMonitorClient.queryMemoryUsagePercent(vm.getAzureResourceId());
                    memoryPoint.ifPresent(rawPoints::add);
                } catch (Exception memEx) {
                    log.warn("Memory usage query failed for VM {}: {}", vm.getName(), memEx.getMessage());
                    meterRegistry.counter("collector.errors.total", "source", "azure_monitor").increment();
                }

                List<MetricSnapshot> snapshots = mapper.toEntities(vm, rawPoints);
                metricSnapshotRepository.saveAll(snapshots);

                log.info("Persisted {} metric snapshots for VM {}", snapshots.size(), vm.getName());
                collectionCycleSuccessCounter.increment();
            } catch (Exception ex) {
                log.warn("Metric collection failed for VM {}: {}", vm.getName(), ex.getMessage());
                collectionCycleFailureCounter.increment();
                meterRegistry.counter("collector.errors.total", "source", "azure_monitor").increment();
            }
        }

        lastCollectionCycleTimestamp.set(Instant.now().toEpochMilli());
        log.info("Metric collection cycle complete");
    }
}
