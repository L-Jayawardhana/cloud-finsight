package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationSummaryDto(
    Long id,
    Long vmId,
    String vmName,
    String currentSku,
    String candidateSku,
    String generationTag,
    String recommendationType,
    String confidenceLevel,
    BigDecimal confidenceScore,
    BigDecimal estimatedMonthlySavings,
    BigDecimal savingPercent,
    String status,
    OffsetDateTime createdAt,
    List<String> pros,
    List<String> cons
) {}
