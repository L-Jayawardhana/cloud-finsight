package com.cloudfinsight.apiservice.config;

import com.cloudfinsight.apiservice.dto.CostSummaryDto;
import com.cloudfinsight.apiservice.dto.VmSummaryDto;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(JsonMapper jsonMapper) {
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .prefixCacheNameWith("api-service:cache:")
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

        JavaType vmListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, VmSummaryDto.class);
        RedisCacheConfiguration vmSummariesConfig = baseConfig.serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new JacksonTypedRedisSerializer<>(jsonMapper, vmListType)));

        JavaType costSummaryType = jsonMapper.constructType(CostSummaryDto.class);
        RedisCacheConfiguration costSummaryConfig = baseConfig.serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new JacksonTypedRedisSerializer<>(jsonMapper, costSummaryType)));

        return builder -> builder
            .cacheDefaults(baseConfig)
            .withCacheConfiguration("vm-summaries", vmSummariesConfig)
            .withCacheConfiguration("cost-summary", costSummaryConfig);
    }
}
