package com.cloudfinsight.apiservice.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Closes Epic 8's AC: "A missing or expired LLM API key returns a descriptive
 * 503 error, not a 500." Shared across /explain (Task 8.2) and /chat (Task 8.3)
 * since both go through LlmClient and can hit either failure mode.
 */
@RestControllerAdvice
public class LlmExceptionHandler {

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(LlmUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "LLM provider is currently unavailable: " + ex.getMessage()));
    }

    @ExceptionHandler(LlmRateLimitException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(LlmRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "LLM provider is temporarily rate-limited, please retry shortly: " + ex.getMessage()));
    }
}
