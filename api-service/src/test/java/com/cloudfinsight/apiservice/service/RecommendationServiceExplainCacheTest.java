package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.ai.LlmClient;
import com.cloudfinsight.apiservice.dto.ExplanationResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies Task 8.2's caching AC: a Redis hit returns cached:true and makes
 * NO call to LlmClient at all. Uses a real Redis connection (this project's
 * established pattern of real infra over mocks for integration-level
 * behaviour) but a mocked LlmClient, since the whole point is to prove the
 * LLM path is never reached on a cache hit.
 */
@SpringBootTest
class RecommendationServiceExplainCacheTest {

    private static final Long FAKE_RECOMMENDATION_ID = 999999L;
    private static final String CACHE_KEY = "explain:" + FAKE_RECOMMENDATION_ID;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private LlmClient llmClient;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(CACHE_KEY);
    }

    @Test
    void cacheHit_returnsCachedExplanation_andNeverCallsLlmClient() {
        redisTemplate.opsForValue().set(CACHE_KEY, "A pre-cached explanation.", Duration.ofHours(1));

        Optional<ExplanationResponseDto> result = recommendationService.explainRecommendation(FAKE_RECOMMENDATION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().explanation()).isEqualTo("A pre-cached explanation.");
        assertThat(result.get().cached()).isTrue();

        verifyNoInteractions(llmClient);
    }
}
