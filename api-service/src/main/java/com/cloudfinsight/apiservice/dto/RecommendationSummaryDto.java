package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecommendationSummaryDto(
    Long id,
    Long vmId,
    String vmName,
    String recommendationType,
    String confidenceLevel,
    BigDecimal estimatedMonthlySavings,
    String status,
    OffsetDateTime createdAt
) {}
