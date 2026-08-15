package com.cloudfinsight.collectorservice.mapper;

import com.cloudfinsight.collectorservice.client.AzureMonitorClient.RawMetricPoint;
import com.cloudfinsight.collectorservice.entity.MetricSnapshot;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSnapshotMapperTest {

    private final MetricSnapshotMapper mapper = new MetricSnapshotMapper();

    @Test
    void toEntity_mapsAzureMetricNameToSnakeCase() {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(1L);

        RawMetricPoint point = new RawMetricPoint(
            "Percentage CPU", 42.5, OffsetDateTime.now());

        MetricSnapshot snapshot = mapper.toEntity(vm, point);

        assertThat(snapshot.getMetricName()).isEqualTo("percentage_cpu");
        assertThat(snapshot.getMetricValue()).isEqualTo(42.5);
        assertThat(snapshot.getVirtualMachine()).isEqualTo(vm);
        assertThat(snapshot.getUnit()).isEqualTo("Percent");
    }

    @Test
    void toEntity_assignsUnknownUnitForUnrecognisedMetric() {
        VirtualMachine vm = new VirtualMachine();
        RawMetricPoint point = new RawMetricPoint(
            "Some New Metric", 10.0, OffsetDateTime.now());

        MetricSnapshot snapshot = mapper.toEntity(vm, point);

        assertThat(snapshot.getUnit()).isEqualTo("Unknown");
    }

    @Test
    void toEntities_mapsListOfPointsPreservingOrder() {
        VirtualMachine vm = new VirtualMachine();
        OffsetDateTime t1 = OffsetDateTime.now();
        OffsetDateTime t2 = t1.plusMinutes(1);

        List<RawMetricPoint> points = List.of(
            new RawMetricPoint("Percentage CPU", 10.0, t1),
            new RawMetricPoint("Network In Total", 2048.0, t2)
        );

        List<MetricSnapshot> snapshots = mapper.toEntities(vm, points);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getMetricName()).isEqualTo("percentage_cpu");
        assertThat(snapshots.get(1).getMetricName()).isEqualTo("network_in_total");
        assertThat(snapshots.get(1).getUnit()).isEqualTo("Bytes");
    }
}