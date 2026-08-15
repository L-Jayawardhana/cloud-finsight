package com.cloudfinsight.collectorservice.client;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.MetricResult;
import com.azure.monitor.query.models.MetricsQueryOptions;
import com.azure.monitor.query.models.MetricsQueryResult;
import com.azure.monitor.query.models.QueryTimeInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AzureMonitorClient {

    private static final List<String> METRIC_NAMES = List.of(
        "Percentage CPU",
        "Network In Total",
        "Network Out Total",
        "Disk Read Bytes",
        "Disk Write Bytes"
    );

    private final MetricsQueryClient client;

    public AzureMonitorClient() {
        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        this.client = new MetricsQueryClientBuilder()
            .credential(credential)
            .buildClient();
    }

    @Retryable(
        retryFor = { com.azure.core.exception.HttpResponseException.class, java.net.SocketTimeoutException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public List<RawMetricPoint> queryMetrics(String azureResourceId, Duration lookback) {
        log.info("Querying Azure Monitor for resource {} (lookback={})", azureResourceId, lookback);

        Response<MetricsQueryResult> response = client.queryResourceWithResponse(
            azureResourceId,
            METRIC_NAMES,
            new MetricsQueryOptions()
                .setTimeInterval(new QueryTimeInterval(lookback)),
            Context.NONE
        );

        MetricsQueryResult result = response.getValue();
        List<RawMetricPoint> points = new ArrayList<>();

        for (MetricResult metric : result.getMetrics()) {
            String metricName = metric.getMetricName();
            metric.getTimeSeries().forEach(ts ->
                ts.getValues().forEach(v -> {
                    if (v.getAverage() != null) {
                        points.add(new RawMetricPoint(
                            metricName,
                            v.getAverage(),
                            v.getTimeStamp()
                        ));
                    }
                })
            );
        }

        log.info("Retrieved {} non-null data points for resource {}", points.size(), azureResourceId);
        return points;
    }

    public record RawMetricPoint(String metricName, Double value, OffsetDateTime timestamp) {}
}