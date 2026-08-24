package com.cloudfinsight.collectorservice.model;

public record SavingsEstimate(
    String currencyCode,
    double currentMonthlyPrice,
    double candidateMonthlyPrice,
    double monthlySaving,
    double savingPercent,
    boolean twoInstanceFeasible,
    double twoInstanceMonthlyCost,
    double twoInstanceMonthlySaving
) {}
