package com.cloudfinsight.apiservice.dto;

import java.math.BigDecimal;

public record VmSummaryDto(
    Long id,
    String name,
    String sku,
    String region,
    BigDecimal currentMonthlyPrice,
    BigDecimal p95CpuPercent,
    BigDecimal p95MemPercent
) {}