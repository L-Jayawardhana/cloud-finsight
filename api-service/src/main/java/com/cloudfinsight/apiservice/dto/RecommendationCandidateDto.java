package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;

public record RecommendationCandidateDto(
    Long id,
    String candidateSku,
    String generationTag,
    BigDecimal estimatedMonthlyCost,
    BigDecimal reliabilityScore,
    BigDecimal performanceScore,
    String pros,
    String cons,
    boolean selected
) {}
