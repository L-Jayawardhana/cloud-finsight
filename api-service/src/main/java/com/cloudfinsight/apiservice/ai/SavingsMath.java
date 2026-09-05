package com.cloudfinsight.apiservice.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared by RecommendationService (Task 8.2) and ChatService (Task 8.3) so the
 *  savingPercent = savings / (savings + candidateCost) * 100 calculation exists
 *  in exactly one place. */
public final class SavingsMath {

    private SavingsMath() {
    }

    public static BigDecimal computeSavingPercent(BigDecimal monthlySavings, BigDecimal candidateMonthlyCost) {
        if (monthlySavings == null || candidateMonthlyCost == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal currentCost = monthlySavings.add(candidateMonthlyCost);
        if (currentCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return monthlySavings
            .divide(currentCost, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    }
}
