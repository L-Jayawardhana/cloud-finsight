package com.cloudfinsight.apiservice.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps the Spring AI ChatClient (Gemini, via spring-ai-starter-model-google-genai).
 * This class is an explanation layer ONLY — it never calculates savings figures,
 * it only ever passes through numbers already computed by the rule engine.
 */
@Service
public class LlmClient {

    private static final String SYSTEM_PROMPT = """
            You are an expert cloud cost advisor embedded in a cloud cost observability
            platform. Your sole job is to explain, in plain language, a recommendation that
            a separate deterministic rule engine has already fully calculated. You never
            calculate, estimate, invent, or adjust any savings figures, percentages, or
            prices yourself: every number in your explanation must be exactly the number
            given to you in the user message, with no arithmetic performed on it. Explain
            the reasoning behind the recommendation (utilisation headroom, generation,
            relative cost) in a factual, concise tone, and do not make claims not
            supported by the data provided to you.
            """;

    private static final String CHAT_SYSTEM_PROMPT = """
            You are a conversational cloud cost advisor embedded in a cloud cost
            observability platform, answering follow-up questions about a specific VM's
            recommendation. A separate deterministic rule engine has already computed all
            figures (utilisation percentages, savings, confidence level) — you explain and
            discuss them, you never calculate or invent new numbers. If the context below
            says no recommendation exists yet, say so honestly rather than fabricating one.
            When asked why a VM was flagged, reference the specific utilisation statistics
            given in the context, not generic reasoning.
            """;

    private final ChatClient chatClient;
    private final Counter llmCallSuccessCounter;
    private final Counter llmCallFailureCounter;

    public LlmClient(ChatClient.Builder chatClientBuilder, MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.llmCallSuccessCounter = Counter.builder("llm.calls.success").register(meterRegistry);
        this.llmCallFailureCounter = Counter.builder("llm.calls.failure").register(meterRegistry);
    }

    /**
     * 3 total attempts (1 initial + 2 retries), exponential backoff starting at 1s,
     * doubling each time, capped at 8s. Only retries on rate-limit errors — an
     * unavailable/invalid key is not retried, it's a caller-visible failure.
     */
    @Retryable(
            includes = LlmRateLimitException.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 8000
    )
    public String explainRecommendation(RecommendationPromptData data) {
        String userPrompt = buildUserPrompt(data);
        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            llmCallSuccessCounter.increment();
            return response;
        } catch (RuntimeException ex) {
            llmCallFailureCounter.increment();
            throw translate(ex);
        }
    }

    /**
     * Multi-turn chat (Task 8.3). recommendationContext is a dynamically-built
     * block of facts about the VM's current/most-recent recommendation (or a
     * "no recommendation yet" note); history is prior turns (oldest first),
     * capped by the caller (ChatService) to the last 5 exchanges.
     */
    @Retryable(
            includes = LlmRateLimitException.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 8000
    )
    public String chat(String recommendationContext, List<ChatTurn> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(CHAT_SYSTEM_PROMPT + "\n\n" + recommendationContext));

        for (ChatTurn turn : history) {
            if ("assistant".equals(turn.role())) {
                messages.add(new AssistantMessage(turn.content()));
            } else {
                messages.add(new UserMessage(turn.content()));
            }
        }
        messages.add(new UserMessage(userMessage));

        try {
            String response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
            llmCallSuccessCounter.increment();
            return response;
        } catch (RuntimeException ex) {
            llmCallFailureCounter.increment();
            throw translate(ex);
        }
    }

    /** Package-visible so the unit test can assert on the constructed prompt directly. */
    String buildUserPrompt(RecommendationPromptData data) {
        String pros = data.prosList() == null ? "" : data.prosList().stream()
                .map(p -> "- " + p)
                .collect(Collectors.joining("\n"));
        String cons = data.consList() == null ? "" : data.consList().stream()
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));

        return """
                VM name: %s
                Current SKU: %s
                Candidate SKU: %s
                Monthly saving: %s
                Saving percent: %s%%
                Confidence level: %s

                Pros:
                %s

                Cons:
                %s

                Explain this recommendation to the user in 2-3 short paragraphs. Use the
                exact monthly saving and saving percent figures given above verbatim —
                do not recalculate or restate them differently.
                """.formatted(
                data.vmName(),
                data.currentSku(),
                data.candidateSku(),
                data.monthlySaving(),
                data.savingPercent(),
                data.confidenceLevel(),
                pros,
                cons
        );
    }

    /**
     * Spring AI's GoogleGenAiChatModel wraps the real provider error in a generic
     * "Failed to generate content" RuntimeException — the diagnostic text we need
     * to classify on (rate-limit vs. auth) lives on the CAUSE, not on this
     * exception's own message. Must walk the full cause chain, not just the
     * top-level exception, or every real-world failure falls through unclassified.
     */
    private RuntimeException translate(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                if (message.contains("RESOURCE_EXHAUSTED") || message.contains("429")) {
                    return new LlmRateLimitException("LLM provider rate limit hit", ex);
                }
                if (message.contains("UNAUTHENTICATED") || message.contains("PERMISSION_DENIED")
                        || message.contains("API key not valid") || message.contains("401")
                        || message.contains("403")) {
                    return new LlmUnavailableException("LLM provider rejected the API key", ex);
                }
            }
            current = current.getCause();
        }
        return ex;
    }
}
