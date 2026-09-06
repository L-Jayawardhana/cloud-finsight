package com.cloudfinsight.collectorservice.client;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.MetricResult;
import com.azure.monitor.query.models.MetricsQueryOptions;
import com.azure.monitor.query.models.MetricsQueryResult;
import com.azure.monitor.query.models.QueryTimeInterval;
import com.cloudfinsight.collectorservice.client.dto.PrometheusQueryResponse;
import com.cloudfinsight.collectorservice.client.dto.PrometheusResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private static final String PROMETHEUS_SCOPE = "https://prometheus.monitor.azure.com/.default";
    private static final String MEMORY_METRIC_NAME = "system.memory.usage";

    @Value("${collector.azure-monitor.prometheus-query-endpoint}")
    private String prometheusQueryEndpoint;

    private final TokenCredential credential;
    private final MetricsQueryClient client;
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureMonitorClient(MeterRegistry meterRegistry) {
        this.credential = new DefaultAzureCredentialBuilder().build();
        this.client = new MetricsQueryClientBuilder()
            .credential(credential)
            .buildClient();
        this.restClient = RestClient.builder().build();
        this.meterRegistry = meterRegistry;
    }

    @Retryable(
        retryFor = { com.azure.core.exception.HttpResponseException.class, java.net.SocketTimeoutException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public List<RawMetricPoint> queryMetrics(String azureResourceId, Duration lookback) {
        log.info("Querying Azure Monitor for resource {} (lookback={})", azureResourceId, lookback);
        meterRegistry.counter("collector.azure.api.calls.total", "client", "monitor", "endpoint", "metrics").increment();

        try {
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
        } catch (RuntimeException ex) {
            meterRegistry.counter("collector.errors.total", "source", "azure_monitor").increment();
            throw ex;
        }
    }

    @Retryable(
        retryFor = { org.springframework.web.client.RestClientException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public Optional<RawMetricPoint> queryMemoryUsagePercent(String azureResourceId) {
        log.info("Querying Prometheus workspace for memory usage on resource {}", azureResourceId);
        meterRegistry.counter("collector.azure.api.calls.total", "client", "monitor", "endpoint", "memory").increment();

        try {
            String token = credential.getToken(
                new TokenRequestContext().addScopes(PROMETHEUS_SCOPE)
            ).block().getToken();

            String query = "{__name__=\"" + MEMORY_METRIC_NAME + "\"}";
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
            String url = prometheusQueryEndpoint + "/api/v1/query?query=" + encodedQuery;

            String rawJson = restClient.get()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

            PrometheusQueryResponse response = parseResponse(rawJson);

            double used = 0;
            double total = 0;

            for (PrometheusResult result : response.data().result()) {
                String resourceId = result.metric().get("microsoft.resourceid");
                if (resourceId == null || !resourceId.equalsIgnoreCase(azureResourceId)) {
                    continue;
                }
                String state = result.metric().get("state");
                double value = Double.parseDouble(String.valueOf(result.value().get(1)));
                total += value;
                if ("used".equals(state)) {
                    used = value;
                }
            }

            if (total == 0) {
                log.warn("No memory usage data found for resource {}", azureResourceId);
                return Optional.empty();
            }

            double percentUsed = (used / total) * 100.0;
            log.info("Memory usage for resource {}: {}%", azureResourceId, percentUsed);

            return Optional.of(new RawMetricPoint("Memory Percentage", percentUsed, OffsetDateTime.now()));
        } catch (RuntimeException ex) {
            meterRegistry.counter("collector.errors.total", "source", "azure_monitor").increment();
            throw ex;
        }
    }

    private PrometheusQueryResponse parseResponse(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, PrometheusQueryResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Prometheus query response", e);
        }
    }

    public record RawMetricPoint(String metricName, Double value, OffsetDateTime timestamp) {}
}