package com.cloudfinsight.collectorservice.cache;

import com.cloudfinsight.collectorservice.client.dto.RetailPricingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PricingCacheService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "pricing:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Optional<RetailPricingRecord> get(String armSkuName, String region) {
        String key = buildKey(armSkuName, region);
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            log.info("Cache hit for pricing key {}", key);
            try {
                return Optional.of(objectMapper.readValue(cached, RetailPricingRecord.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize cached pricing for {}, treating as cache miss", key);
                return Optional.empty();
            }
        }

        log.info("Cache miss for pricing key {}", key);
        return Optional.empty();
    }

    public void put(String armSkuName, String region, RetailPricingRecord record) {
        String key = buildKey(armSkuName, region);
        try {
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache pricing for {}: {}", key, e.getMessage());
        }
    }

    private String buildKey(String armSkuName, String region) {
        return KEY_PREFIX + armSkuName + ":" + region;
    }
}
