package com.cloudfinsight.collectorservice.scheduler;

import com.cloudfinsight.collectorservice.cache.PricingCacheService;
import com.cloudfinsight.collectorservice.client.AzureRetailPricesClient;
import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.cloudfinsight.collectorservice.entity.PricingSnapshot;
import com.cloudfinsight.collectorservice.mapper.PricingSnapshotMapper;
import com.cloudfinsight.collectorservice.repository.PricingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collector.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PricingCollectionScheduler {

    // Interim source until Task 3.3's SkuCatalogueService replaces this list.
    @Value("#{'${collector.pricing.skus}'.split(',')}")
    private List<String> configuredSkus;

    @Value("${collector.pricing.region}")
    private String region;

    private final AzureRetailPricesClient azureRetailPricesClient;
    private final PricingCacheService pricingCacheService;
    private final PricingSnapshotMapper mapper;
    private final PricingSnapshotRepository pricingSnapshotRepository;

    @Scheduled(fixedDelayString = "${collector.pricing.interval-ms}")
    public void collectPricing() {
        log.info("Starting pricing collection cycle for {} configured SKU(s)", configuredSkus.size());

        for (String sku : configuredSkus) {
            String armSkuName = sku.trim();
            try {
                Optional<RetailPricingRecord> cached = pricingCacheService.get(armSkuName, region);
                RetailPricingRecord record = cached.orElseGet(() -> {
                    Optional<RetailPricingRecord> fetched =
                        azureRetailPricesClient.fetchOnDemandLinuxPrice(armSkuName, region);
                    fetched.ifPresent(r -> pricingCacheService.put(armSkuName, region, r));
                    return fetched.orElse(null);
                });

                if (record != null) {
                    PricingSnapshot snapshot = mapper.toEntity(record);
                    pricingSnapshotRepository.save(snapshot);
                    log.info("Persisted pricing snapshot for SKU {}: {} {}",
                        armSkuName, record.retailPrice(), record.currencyCode());
                } else {
                    log.warn("No pricing found for SKU {} in region {}", armSkuName, region);
                }
            } catch (Exception ex) {
                log.warn("Pricing collection failed for SKU {}: {}", armSkuName, ex.getMessage());
            }
        }

        log.info("Pricing collection cycle complete");
    }
}