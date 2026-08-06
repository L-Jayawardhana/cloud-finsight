package com.cloudfinsight.apiservice.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pre-computed recommendation data injected verbatim into the LLM prompt.
 * Every numeric field here comes straight from the database — the LLM is
 * never asked to calculate or re-derive any of these figures.
 */
public record RecommendationPromptData(
        String vmName,
        String currentSku,
        String candidateSku,
        BigDecimal monthlySaving,
        BigDecimal savingPercent,
        String confidenceLevel,
        List<String> prosList,
        List<String> consList
) {
}
