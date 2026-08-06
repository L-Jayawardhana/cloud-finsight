package com.cloudfinsight.apiservice.ai;

/** Thrown when the LLM provider rejects the API key (missing/expired/invalid). Not retryable. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
