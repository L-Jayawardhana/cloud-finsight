package com.cloudfinsight.collectorservice.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureMonitorClientRetryTest {

    @Test
    void retryTemplate_retriesConfiguredNumberOfTimesOnTransientFailure() {
        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(10) // fast for tests — real config uses exponential 2s/4s/8s
            .retryOn(java.net.SocketTimeoutException.class)
            .build();

        int[] attemptCount = {0};

        assertThatThrownBy(() ->
            retryTemplate.execute(context -> {
                attemptCount[0]++;
                throw new java.net.SocketTimeoutException("simulated Azure API timeout");
            })
        ).isInstanceOf(java.net.SocketTimeoutException.class);

        assertThat(attemptCount[0]).isEqualTo(3);
    }
}