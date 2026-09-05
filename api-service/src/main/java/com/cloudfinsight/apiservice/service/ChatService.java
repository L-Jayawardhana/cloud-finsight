package com.cloudfinsight.apiservice.service;

import com.cloudfinsight.apiservice.ai.ChatTurn;
import com.cloudfinsight.apiservice.ai.LlmClient;
import com.cloudfinsight.apiservice.ai.SavingsMath;
import com.cloudfinsight.apiservice.dto.ChatResponseDto;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import com.cloudfinsight.apiservice.entity.VirtualMachine;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String PENDING_STATUS = "PENDING";
    private static final String HISTORY_KEY_PREFIX = "chat:";
    private static final Duration HISTORY_TTL = Duration.ofHours(24);
    private static final int MAX_TURNS = 5;
    private static final int MAX_STORED_ENTRIES = MAX_TURNS * 2; // 5 user+assistant exchanges

    private final VirtualMachineRepository virtualMachineRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;
    private final StringRedisTemplate redisTemplate;
    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;

    /**
     * @return empty if vmId doesn't exist at all (controller maps to 404).
     *         If the VM exists but has no recommendation yet, this still
     *         returns a real answer - the LLM is told so explicitly rather
     *         than the endpoint failing (Task 8.3's "handle gracefully" AC).
     */
    public Optional<ChatResponseDto> chat(Long vmId, String message) {
        Optional<VirtualMachine> vmOpt = virtualMachineRepository.findById(vmId);
        if (vmOpt.isEmpty()) {
            return Optional.empty();
        }

        String context = buildRecommendationContext(vmOpt.get(), vmId);

        String historyKey = HISTORY_KEY_PREFIX + vmId;
        List<ChatTurn> history = loadHistory(historyKey);

        String reply = llmClient.chat(context, history, message);

        history.add(new ChatTurn("user", message));
        history.add(new ChatTurn("assistant", reply));
        if (history.size() > MAX_STORED_ENTRIES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_STORED_ENTRIES, history.size()));
        }
        saveHistory(historyKey, history);

        return Optional.of(new ChatResponseDto(reply));
    }

    /**
     * Package-visible so the unit test can assert the injected recommendation
     * data (specifically the p95 utilisation figures the AC requires) is
     * actually present, without needing a Spring context or a real LLM call.
     */
    String buildRecommendationContext(VirtualMachine vm, Long vmId) {
        Long latestId = recommendationRepository
            .findLatestIdByVirtualMachineIdAndStatus(vmId, PENDING_STATUS)
            .orElse(null);

        if (latestId == null) {
            return """
                VM name: %s
                Current SKU: %s

                No recommendation has been generated for this VM yet. Be honest about
                this - do not invent utilisation statistics, savings figures, or a
                recommendation that does not exist.
                """.formatted(vm.getName(), vm.getCurrentSku());
        }

        Recommendation r = recommendationRepository.findByIdWithVirtualMachine(latestId)
            .orElseThrow(() -> new IllegalStateException(
                "Recommendation " + latestId + " vanished between lookup and fetch"));

        RecommendationCandidate selected = recommendationCandidateRepository
            .findByRecommendationIdAndSelectedTrue(latestId)
            .orElseThrow(() -> new IllegalStateException(
                "Recommendation " + latestId + " has no selected candidate - data integrity issue"));

        return """
            VM name: %s
            Current SKU: %s
            Observed p95 CPU utilisation: %s%%
            Observed p95 memory utilisation: %s%%
            Recommended candidate SKU: %s
            Estimated monthly saving: %s (%s%%)
            Confidence level: %s
            Pros: %s
            Cons: %s

            Answer follow-up questions using ONLY these figures. Never invent or
            recalculate numbers not given here. If asked why the VM was flagged,
            reference the specific p95 utilisation percentages above.
            """.formatted(
                vm.getName(),
                vm.getCurrentSku(),
                vm.getP95CpuPercent(),
                vm.getP95MemPercent(),
                selected.getCandidateSku(),
                r.getEstimatedMonthlySavings(),
                SavingsMath.computeSavingPercent(r.getEstimatedMonthlySavings(), selected.getEstimatedMonthlyCost()),
                r.getConfidenceLevel(),
                selected.getPros(),
                selected.getCons()
            );
    }

    private List<ChatTurn> loadHistory(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        JavaType listType = jsonMapper.getTypeFactory().constructCollectionType(List.class, ChatTurn.class);
        List<ChatTurn> history = jsonMapper.readValue(json, listType);
        return new ArrayList<>(history);
    }

    private void saveHistory(String key, List<ChatTurn> history) {
        String json = jsonMapper.writeValueAsString(history);
        redisTemplate.opsForValue().set(key, json, HISTORY_TTL);
    }
}
