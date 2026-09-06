package com.cloudfinsight.collectorservice.client;

import com.cloudfinsight.collectorservice.client.dto.RetailPricesResponse;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AzureRetailPricesClient {

    private static final String BASE_URL = "https://prices.azure.com/api/retail/prices";

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules(); // registers JavaTimeModule for OffsetDateTime parsing

    public AzureRetailPricesClient(MeterRegistry meterRegistry) {
        this.restClient = RestClient.builder().build();
        this.meterRegistry = meterRegistry;
    }

    @Retryable(
        retryFor = { org.springframework.web.client.RestClientException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public Optional<RetailPricingRecord> fetchOnDemandLinuxPrice(String armSkuName, String region) {
        meterRegistry.counter("collector.azure.api.calls.total", "client", "pricing", "endpoint", "retail-prices").increment();
        try {
            String filter = AzureRetailPricesFilterBuilder.build(armSkuName, region);
            String encodedFilter = URLEncoder.encode(filter, StandardCharsets.UTF_8).replace("+", "%20");
            String url = BASE_URL + "?$filter=" + encodedFilter;
            log.info("Fetching retail pricing for SKU {} in region {}", armSkuName, region);

            List<RetailPricingRecord> allRecords = fetchAllPages(url);

            return allRecords.stream()
                .filter(r -> "Virtual Machines".equals(r.serviceName()))
                .filter(r -> !r.meterName().contains("Spot"))
                .filter(r -> !r.meterName().contains("Low Priority"))
                .filter(r -> r.productName() == null || !r.productName().contains("Windows"))
                .findFirst();
        } catch (RuntimeException ex) {
            meterRegistry.counter("collector.errors.total", "source", "azure_pricing").increment();
            throw ex;
        }
    }

    private List<RetailPricingRecord> fetchAllPages(String initialUrl) {
        List<RetailPricingRecord> results = new ArrayList<>();
        String nextUrl = initialUrl;

        while (nextUrl != null) {
            String rawJson = restClient.get()
                .uri(java.net.URI.create(nextUrl))
                .retrieve()
                .body(String.class);

            RetailPricesResponse response = parseResponse(rawJson);
            results.addAll(response.items());
            nextUrl = response.nextPageLink();
        }

        return results;
}

    private RetailPricesResponse parseResponse(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, RetailPricesResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Azure Retail Prices response", e);
        }
    }
}