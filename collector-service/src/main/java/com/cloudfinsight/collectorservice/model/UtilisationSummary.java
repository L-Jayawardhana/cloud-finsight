package com.cloudfinsight.collectorservice.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public record UtilisationSummary(
    Long virtualMachineId,
    Map<String, MetricStats> statsByMetricName,
    int dataPointCount,
    OffsetDateTime earliestTimestamp,
    OffsetDateTime latestTimestamp
) {
    public Optional<MetricStats> get(String metricName) {
        return Optional.ofNullable(statsByMetricName.get(metricName));
    }

    public boolean hasData() {
        return dataPointCount > 0;
    }

    public long daysOfDataAvailable() {
        if (earliestTimestamp == null || latestTimestamp == null) {
            return 0;
        }
        return java.time.Duration.between(earliestTimestamp, latestTimestamp).toDays();
    }
}
