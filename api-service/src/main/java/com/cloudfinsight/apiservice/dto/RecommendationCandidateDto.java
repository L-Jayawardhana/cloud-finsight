package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;

public record RecommendationCandidateDto(
    Long id,
    String candidateSku,
    String generationTag,
    Integer vcpuCount,
    BigDecimal memoryGb,
    BigDecimal estimatedMonthlyCost,
    BigDecimal reliabilityScore,
    BigDecimal performanceScore,
    String pros,
    String cons,
    boolean selected,
    Boolean twoInstanceFeasible,
    BigDecimal twoInstanceMonthlyCost,
    BigDecimal twoInstanceMonthlySaving
) {}
