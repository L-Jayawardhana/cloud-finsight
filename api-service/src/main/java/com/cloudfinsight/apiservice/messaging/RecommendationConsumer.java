package com.cloudfinsight.apiservice.messaging;

import com.cloudfinsight.apiservice.config.RabbitMQConfig;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.entity.RecommendationCandidate;
import com.cloudfinsight.apiservice.entity.VirtualMachine;
import com.cloudfinsight.apiservice.model.RecommendationMessage;
import com.cloudfinsight.apiservice.repository.RecommendationCandidateRepository;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import com.cloudfinsight.apiservice.repository.VirtualMachineRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Consumes RecommendationMessage events published by collector-service (Task 5.2) and
 * upserts them into this service's own view of the recommendations table. Since both
 * services currently share one database, this is a deliberately simple idempotent
 * upsert-by-recommendationId (Option B from the Task 5.3 design discussion): the row
 * usually already exists (collector-service inserts it synchronously before publishing),
 * so this mostly re-applies the same values, while being safe against RabbitMQ's
 * at-least-once redelivery and forward-compatible if the databases ever separate.
 */
@Slf4j
@Component
public class RecommendationConsumer {

    private final VirtualMachineRepository virtualMachineRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCandidateRepository recommendationCandidateRepository;
    private final JsonMapper jsonMapper;
    private final Counter consumedCounter;
    private final Counter dlqCounter;

    public RecommendationConsumer(VirtualMachineRepository virtualMachineRepository,
                                   RecommendationRepository recommendationRepository,
                                   RecommendationCandidateRepository recommendationCandidateRepository,
                                   JsonMapper jsonMapper,
                                   MeterRegistry meterRegistry) {
        this.virtualMachineRepository = virtualMachineRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationCandidateRepository = recommendationCandidateRepository;
        this.jsonMapper = jsonMapper;
        this.consumedCounter = Counter.builder("recommendations.consumed.total")
            .description("Recommendation messages successfully consumed and upserted")
            .register(meterRegistry);
        this.dlqCounter = Counter.builder("recommendations.dlq.total")
            .description("Recommendation messages routed to the DLQ due to deserialization failure")
            .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitMQConfig.RECOMMENDATIONS_QUEUE)
    public void handle(Message message) {
        RecommendationMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), RecommendationMessage.class);
        } catch (Exception ex) {
            dlqCounter.increment();
            log.error("Failed to deserialize recommendation message; routing to DLQ. Raw body: {}",
                new String(message.getBody(), StandardCharsets.UTF_8), ex);
            throw new AmqpRejectAndDontRequeueException("Deserialization failed", ex);
        }

        upsert(payload);
        consumedCounter.increment();
        log.info("Consumed recommendation {} for VM {}", payload.recommendationId(), payload.vmId());
    }

    private void upsert(RecommendationMessage payload) {
        Optional<VirtualMachine> vm = virtualMachineRepository.findById(payload.vmId());
        if (vm.isEmpty()) {
            dlqCounter.increment();
            log.error("VM {} referenced by recommendation {} not found; routing to DLQ",
                payload.vmId(), payload.recommendationId());
            throw new AmqpRejectAndDontRequeueException("Referenced VM not found: " + payload.vmId());
        }

        Recommendation recommendation = recommendationRepository.findById(payload.recommendationId())
            .orElseGet(Recommendation::new);
        recommendation.setId(payload.recommendationId());
        recommendation.setVirtualMachine(vm.get());
        recommendation.setStatus(payload.status());
        recommendation.setRecommendationType(payload.recommendationType());
        recommendation.setConfidenceLevel(payload.confidenceLevel());
        recommendation.setConfidenceScore(payload.confidenceScore());
        recommendation.setSummary(payload.summary());
        recommendation.setEstimatedMonthlySavings(payload.estimatedMonthlySavings());
        Recommendation saved = recommendationRepository.save(recommendation);

        RecommendationCandidate candidate = recommendationCandidateRepository
            .findByRecommendationIdAndSelectedTrue(saved.getId())
            .orElseGet(RecommendationCandidate::new);
        candidate.setRecommendation(saved);
        candidate.setCandidateSku(payload.candidateSku());
        candidate.setGenerationTag(payload.candidateGenerationTag());
        candidate.setEstimatedMonthlyCost(payload.candidateEstimatedMonthlyCost());
        candidate.setPros(payload.candidatePros());
        candidate.setCons(payload.candidateCons());
        candidate.setSelected(true);
        recommendationCandidateRepository.save(candidate);
    }
}
