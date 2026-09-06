package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationDetailDto(
    Long id,
    Long vmId,
    String vmName,
    String currentSku,
    String currentGenerationTag,
    Integer currentVcpuCount,
    BigDecimal currentMemoryGb,
    BigDecimal currentMonthlyPrice,
    String recommendationType,
    String confidenceLevel,
    BigDecimal confidenceScore,
    Integer dataCoverageDays,
    BigDecimal estimatedMonthlySavings,
    String status,
    String summary,
    OffsetDateTime createdAt,
    List<RecommendationCandidateDto> candidates
) {}
