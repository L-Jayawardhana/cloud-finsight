package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.MetricSnapshot;
import com.cloudfinsight.collectorservice.model.MetricStats;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import com.cloudfinsight.collectorservice.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisationAggregator {

    @Value("${collector.analysis.rolling-window-days:14}")
    private int rollingWindowDays;

    private final MetricSnapshotRepository metricSnapshotRepository;

    public UtilisationSummary summarise(Long vmId) {
        OffsetDateTime windowEnd = OffsetDateTime.now();
        OffsetDateTime windowStart = windowEnd.minusDays(rollingWindowDays);

        List<MetricSnapshot> snapshots =
            metricSnapshotRepository.findByVirtualMachineIdAndCollectedAtBetween(vmId, windowStart, windowEnd);

        if (snapshots.isEmpty()) {
            return new UtilisationSummary(vmId, Map.of(), 0, null, null);
        }

        Map<String, List<MetricSnapshot>> byMetricName = snapshots.stream()
            .collect(Collectors.groupingBy(MetricSnapshot::getMetricName));

        Map<String, MetricStats> statsByMetricName = new HashMap<>();
        for (Map.Entry<String, List<MetricSnapshot>> entry : byMetricName.entrySet()) {
            statsByMetricName.put(entry.getKey(), computeStats(entry.getValue()));
        }

        OffsetDateTime earliest = snapshots.stream()
            .map(MetricSnapshot::getCollectedAt)
            .min(Comparator.naturalOrder())
            .orElse(null);

        OffsetDateTime latest = snapshots.stream()
            .map(MetricSnapshot::getCollectedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);

        return new UtilisationSummary(vmId, statsByMetricName, snapshots.size(), earliest, latest);
    }

    private MetricStats computeStats(List<MetricSnapshot> snapshots) {
        List<Double> sorted = new ArrayList<>(
            snapshots.stream().map(MetricSnapshot::getMetricValue).toList()
        );
        sorted.sort(Comparator.naturalOrder());

        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);
        double max = sorted.get(sorted.size() - 1);

        return new MetricStats(p50, p95, p99, max);
    }

    private double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
