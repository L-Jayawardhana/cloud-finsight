package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import com.cloudfinsight.apiservice.entity.VirtualMachine;
import com.cloudfinsight.apiservice.ai.LlmClient;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No Spring context, no real LLM call - pure unit test of context construction,
 * matching LlmClientTest's approach for Task 8.1. Verifies Task 8.3's AC
 * directly: the specific p95 utilisation figures actually appear in what gets
 * sent to the LLM, not just generic prose about "headroom".
 */
class ChatServiceContextTest {

    private static final Long VM_ID = 1L;
    private static final Long RECOMMENDATION_ID = 23L;

    private RecommendationRepository recommendationRepository;
    private RecommendationCandidateRepository recommendationCandidateRepository;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        VirtualMachineRepository virtualMachineRepository = mock(VirtualMachineRepository.class);
        recommendationRepository = mock(RecommendationRepository.class);
        recommendationCandidateRepository = mock(RecommendationCandidateRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LlmClient llmClient = mock(LlmClient.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();

        chatService = new ChatService(
            virtualMachineRepository,
            recommendationRepository,
            recommendationCandidateRepository,
            redisTemplate,
            llmClient,
            jsonMapper
        );
    }

    @Test
    void buildRecommendationContext_includesActualP95UtilisationFigures_notGenericText() {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(VM_ID);
        vm.setName("vm-current-gen-d2sv4");
        vm.setCurrentSku("Standard_D2s_v4");
        vm.setP95CpuPercent(new BigDecimal("18.40"));
        vm.setP95MemPercent(new BigDecimal("22.10"));

        Recommendation recommendation = new Recommendation();
        recommendation.setId(RECOMMENDATION_ID);
        recommendation.setVirtualMachine(vm);
        recommendation.setConfidenceLevel("LOW");
        recommendation.setEstimatedMonthlySavings(new BigDecimal("49.06"));

        RecommendationCandidate selected = new RecommendationCandidate();
        selected.setCandidateSku("Standard_B2s");
        selected.setEstimatedMonthlyCost(new BigDecimal("38.55"));
        selected.setPros("Lower cost\nSufficient headroom");
        selected.setCons("Older generation");
        selected.setSelected(true);

        when(recommendationRepository.findLatestIdByVirtualMachineIdAndStatus(VM_ID, "PENDING"))
            .thenReturn(Optional.of(RECOMMENDATION_ID));
        when(recommendationRepository.findByIdWithVirtualMachine(RECOMMENDATION_ID))
            .thenReturn(Optional.of(recommendation));
        when(recommendationCandidateRepository.findByRecommendationIdAndSelectedTrue(RECOMMENDATION_ID))
            .thenReturn(Optional.of(selected));

        String context = chatService.buildRecommendationContext(vm, VM_ID);

        assertThat(context).contains("18.40");
        assertThat(context).contains("22.10");
        assertThat(context).contains("vm-current-gen-d2sv4");
        assertThat(context).contains("Standard_D2s_v4");
        assertThat(context).contains("Standard_B2s");
        assertThat(context).contains("49.06");
        assertThat(context).contains("LOW");
    }

    @Test
    void buildRecommendationContext_whenNoRecommendationExists_saysSoHonestly() {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(2L);
        vm.setName("vm-no-recommendation-yet");
        vm.setCurrentSku("Standard_D2s_v4");

        when(recommendationRepository.findLatestIdByVirtualMachineIdAndStatus(2L, "PENDING"))
            .thenReturn(Optional.empty());

        String context = chatService.buildRecommendationContext(vm, 2L);

        assertThat(context).contains("vm-no-recommendation-yet");
        assertThat(context).contains("No recommendation has been generated");
    }
}
