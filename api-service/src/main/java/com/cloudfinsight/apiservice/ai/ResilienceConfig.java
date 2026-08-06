package com.cloudfinsight.apiservice.ai;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Enables Spring Framework 7's native @Retryable/@ConcurrencyLimit processing.
 * No spring-retry dependency needed as of Boot 4.1 / Spring Framework 7 —
 * that project is archived and superseded by this.
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
