package com.cloudfinsight.apiservice.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** No real LLM API call is made anywhere in this test — ChatClient is fully mocked. */
class LlmClientTest {

    private ChatClient chatClient;
    private LlmClient llmClient;

    private static final RecommendationPromptData SAMPLE_DATA = new RecommendationPromptData(
            "vm-current-gen-d2sv4",
            "Standard_D2s_v4",
            "Standard_B2s",
            new BigDecimal("49.06"),
            new BigDecimal("18.5"),
            "LOW",
            List.of("Lower cost", "Sufficient headroom"),
            List.of("Older generation")
    );

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        llmClient = new LlmClient(builder, new SimpleMeterRegistry());
    }

    @Test
    void constructedPromptContainsExactSavingsFigureFromDatabase_notAModelInventedValue() {
        String prompt = llmClient.buildUserPrompt(SAMPLE_DATA);

        assertThat(prompt).contains("49.06");
        assertThat(prompt).contains("18.5");
        assertThat(prompt).contains("vm-current-gen-d2sv4");
        assertThat(prompt).contains("Standard_D2s_v4");
        assertThat(prompt).contains("Standard_B2s");
        assertThat(prompt).contains("LOW");
        assertThat(prompt).contains("Lower cost");
        assertThat(prompt).contains("Older generation");
    }

    @Test
    void explainRecommendation_returnsChatClientContent_withNoRealApiCall() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content()
        ).thenReturn("This VM can be safely downsized, saving 49.06 per month.");

        String result = llmClient.explainRecommendation(SAMPLE_DATA);

        assertThat(result).isEqualTo("This VM can be safely downsized, saving 49.06 per month.");
    }
}
