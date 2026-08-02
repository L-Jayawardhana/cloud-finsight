package com.cloudfinsight.collectorservice.service;

import com.cloudfinsight.collectorservice.entity.MetricSnapshot;
import com.cloudfinsight.collectorservice.model.MetricStats;
import com.cloudfinsight.collectorservice.model.UtilisationSummary;
import com.cloudfinsight.collectorservice.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UtilisationAggregatorTest {

    private MetricSnapshotRepository repository;
    private UtilisationAggregator aggregator;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MetricSnapshotRepository.class);
        aggregator = new UtilisationAggregator(repository);
    }

    @Test
    void summarise_noData_returnsEmptySummaryWithoutError() {
        when(repository.findByVirtualMachineIdAndCollectedAtBetween(eq(1L), any(), any()))
            .thenReturn(List.of());

        UtilisationSummary summary = aggregator.summarise(1L);

        assertThat(summary.hasData()).isFalse();
        assertThat(summary.dataPointCount()).isEqualTo(0);
        assertThat(summary.earliestTimestamp()).isNull();
        assertThat(summary.latestTimestamp()).isNull();
        assertThat(summary.statsByMetricName()).isEmpty();
    }

    @Test
    void summarise_singleDataPoint_p50p95p99AllEqualThatValue() {
        when(repository.findByVirtualMachineIdAndCollectedAtBetween(eq(1L), any(), any()))
            .thenReturn(List.of(snapshot("percentage_cpu", 42.0)));

        UtilisationSummary summary = aggregator.summarise(1L);

        assertThat(summary.hasData()).isTrue();
        assertThat(summary.dataPointCount()).isEqualTo(1);

        MetricStats stats = summary.get("percentage_cpu").orElseThrow();
        assertThat(stats.p50()).isEqualTo(42.0);
        assertThat(stats.p95()).isEqualTo(42.0);
        assertThat(stats.p99()).isEqualTo(42.0);
        assertThat(stats.max()).isEqualTo(42.0);
    }

    @Test
    void summarise_knownDataset_computesCorrectP95() {
        // 1 to 100, evenly spaced integer values - a known, verifiable dataset.
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            snapshots.add(snapshot("percentage_cpu", (double) i));
        }

        when(repository.findByVirtualMachineIdAndCollectedAtBetween(eq(1L), any(), any()))
            .thenReturn(snapshots);

        UtilisationSummary summary = aggregator.summarise(1L);
        MetricStats stats = summary.get("percentage_cpu").orElseThrow();

        // Nearest-rank percentile of 1..100: p95 -> index ceil(0.95*100)-1 = 94 -> value 95.0
        assertThat(stats.p95()).isEqualTo(95.0);
        assertThat(stats.p99()).isEqualTo(99.0);
        assertThat(stats.p50()).isEqualTo(50.0);
        assertThat(stats.max()).isEqualTo(100.0);
        assertThat(summary.dataPointCount()).isEqualTo(100);
    }

    @Test
    void summarise_largeSparseDataset_handlesGapsCorrectly() {
        // Sparse: only 5 points scattered across the window, with large value gaps.
        List<MetricSnapshot> snapshots = List.of(
            snapshot("percentage_cpu", 5.0),
            snapshot("percentage_cpu", 10.0),
            snapshot("percentage_cpu", 15.0),
            snapshot("percentage_cpu", 90.0),
            snapshot("percentage_cpu", 95.0)
        );

        when(repository.findByVirtualMachineIdAndCollectedAtBetween(eq(1L), any(), any()))
            .thenReturn(snapshots);

        UtilisationSummary summary = aggregator.summarise(1L);
        MetricStats stats = summary.get("percentage_cpu").orElseThrow();

        assertThat(summary.dataPointCount()).isEqualTo(5);
        assertThat(stats.max()).isEqualTo(95.0);
        // Sorted: [5, 10, 15, 90, 95] - p95 -> index ceil(0.95*5)-1 = 4 -> value 95.0
        assertThat(stats.p95()).isEqualTo(95.0);
    }

    @Test
    void summarise_multipleMetricNames_computesIndependentStatsPerMetric() {
        List<MetricSnapshot> snapshots = List.of(
            snapshot("percentage_cpu", 20.0),
            snapshot("percentage_cpu", 40.0),
            snapshot("memory_percentage", 60.0),
            snapshot("memory_percentage", 80.0)
        );

        when(repository.findByVirtualMachineIdAndCollectedAtBetween(eq(1L), any(), any()))
            .thenReturn(snapshots);

        UtilisationSummary summary = aggregator.summarise(1L);

        assertThat(summary.get("percentage_cpu")).isPresent();
        assertThat(summary.get("memory_percentage")).isPresent();
        assertThat(summary.get("percentage_cpu").get().max()).isEqualTo(40.0);
        assertThat(summary.get("memory_percentage").get().max()).isEqualTo(80.0);
    }

    private MetricSnapshot snapshot(String metricName, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setMetricName(metricName);
        s.setMetricValue(value);
        s.setCollectedAt(OffsetDateTime.now());
        return s;
    }
}
