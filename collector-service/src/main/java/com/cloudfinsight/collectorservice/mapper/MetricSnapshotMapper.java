package com.cloudfinsight.collectorservice.mapper;

import com.cloudfinsight.collectorservice.client.AzureMonitorClient.RawMetricPoint;
import com.cloudfinsight.collectorservice.entity.MetricSnapshot;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetricSnapshotMapper {

    private static final java.util.Map<String, String> UNIT_BY_METRIC = java.util.Map.of(
        "Percentage CPU", "Percent",
        "Network In Total", "Bytes",
        "Network Out Total", "Bytes",
        "Disk Read Bytes", "Bytes",
        "Disk Write Bytes", "Bytes"
    );

    public List<MetricSnapshot> toEntities(VirtualMachine vm, List<RawMetricPoint> rawPoints) {
        return rawPoints.stream()
            .map(point -> toEntity(vm, point))
            .toList();
    }

    public MetricSnapshot toEntity(VirtualMachine vm, RawMetricPoint point) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setVirtualMachine(vm);
        snapshot.setMetricName(toSnakeCase(point.metricName()));
        snapshot.setMetricValue(point.value());
        snapshot.setUnit(UNIT_BY_METRIC.getOrDefault(point.metricName(), "Unknown"));
        snapshot.setCollectedAt(point.timestamp());
        return snapshot;
    }

    private String toSnakeCase(String azureMetricName) {
        return azureMetricName.toLowerCase().replace(" ", "_");
    }
}