package com.cloudfinsight.apiservice.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * api-service's own copy of the wire format published by collector-service on
 * recommendations.queue (Task 5.2). Deserialized directly via JsonMapper rather than
 * Spring AMQP's type-header mechanism, since the message's __TypeId__ header points at
 * collector-service's class, not this one (see RecommendationConsumer).
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
