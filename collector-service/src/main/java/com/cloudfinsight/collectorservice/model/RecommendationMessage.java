package com.cloudfinsight.collectorservice.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Wire format published to the {@code recommendations.queue} on
 * {@code cost-platform.recommendations} (Task 5.2). Consumed by api-service (Task 5.3),
 * which upserts by {@code vmId}.
 */
public record RecommendationMessage(
    Long recommendationId,
    Long vmId,
    String status,
    String recommendationType,
    String confidenceLevel,
    BigDecimal confidenceScore,
    String summary,
    BigDecimal estimatedMonthlySavings,
    String candidateSku,
    String candidateGenerationTag,
    BigDecimal candidateEstimatedMonthlyCost,
    String candidatePros,
    String candidateCons,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
