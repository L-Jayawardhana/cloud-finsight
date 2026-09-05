package com.cloudfinsight.apiservice.ai;

/** Thrown when the LLM provider signals a rate limit (HTTP 429 / RESOURCE_EXHAUSTED). Retryable. */
public class LlmRateLimitException extends RuntimeException {
    public LlmRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
