package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;

public record CostSummaryDto(
    BigDecimal totalMonthlySpend,
    BigDecimal totalPotentialSaving,
    long vmCount,
    long recommendationCount
) {}