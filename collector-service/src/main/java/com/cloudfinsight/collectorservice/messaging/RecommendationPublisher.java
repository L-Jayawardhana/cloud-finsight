package com.cloudfinsight.collectorservice.messaging;

import com.cloudfinsight.collectorservice.config.RabbitMQConfig;
import com.cloudfinsight.collectorservice.entity.Recommendation;
import com.cloudfinsight.collectorservice.entity.RecommendationCandidate;
import com.cloudfinsight.collectorservice.model.RecommendationMessage;
import com.cloudfinsight.collectorservice.repository.RecommendationCandidateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Publishes packaged recommendations onto the {@code cost-platform.recommendations} exchange
 * (Task 5.2). Publisher confirms are enabled via
 * {@code spring.rabbitmq.publisher-confirm-type: correlated}; NACKs are logged and counted
 * rather than retried here (the DLQ / TTL handle redelivery at the broker level).
 */
@Slf4j
@Component
public class RecommendationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RecommendationCandidateRepository recommendationCandidateRepository;
    private final MeterRegistry meterRegistry;

    public RecommendationPublisher(RabbitTemplate rabbitTemplate,
                                    RecommendationCandidateRepository recommendationCandidateRepository,
                                    MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.recommendationCandidateRepository = recommendationCandidateRepository;
        this.meterRegistry = meterRegistry;

        this.rabbitTemplate.setConfirmCallback(this::handleConfirm);
    }

    /**
     * Builds and publishes a {@link RecommendationMessage} for the given persisted recommendation.
     * Looks up the selected candidate explicitly rather than via {@code recommendation.getCandidates()},
     * since that association is lazy and this method runs outside the transaction that saved it.
     */
    public void publish(Recommendation recommendation) {
        Optional<RecommendationCandidate> selected =
            recommendationCandidateRepository.findByRecommendationIdAndSelectedTrue(recommendation.getId());

        if (selected.isEmpty()) {
            log.warn("No selected candidate found for recommendation {}; skipping publish", recommendation.getId());
            return;
        }

        RecommendationMessage message = toMessage(recommendation, selected.get());
        CorrelationData correlationData = new CorrelationData(String.valueOf(recommendation.getId()));

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.RECOMMENDATIONS_EXCHANGE,
            RabbitMQConfig.RECOMMENDATIONS_ROUTING_KEY,
            message,
            correlationData);

        log.info("Published recommendation {} (VM {}) to exchange {}",
            recommendation.getId(), recommendation.getVirtualMachine().getId(),
            RabbitMQConfig.RECOMMENDATIONS_EXCHANGE);
    }

    private void handleConfirm(CorrelationData correlationData, boolean ack, String cause) {
        String correlationId = correlationData != null ? correlationData.getId() : "unknown";
        if (ack) {
            meterRegistry.counter("recommendations.published.total", "result", "success").increment();
            log.debug("Recommendation message {} confirmed (ACK) by broker", correlationId);
        } else {
            meterRegistry.counter("recommendations.published.total", "result", "failure").increment();
            meterRegistry.counter("collector.errors.total", "source", "rabbitmq_publish").increment();
            log.error("Recommendation message {} NACKed by broker. Cause: {}", correlationId, cause);
        }
    }

    private RecommendationMessage toMessage(Recommendation recommendation, RecommendationCandidate candidate) {
        return new RecommendationMessage(
            recommendation.getId(),
            recommendation.getVirtualMachine().getId(),
            recommendation.getStatus(),
            recommendation.getRecommendationType(),
            recommendation.getConfidenceLevel(),
            recommendation.getConfidenceScore(),
            recommendation.getSummary(),
            recommendation.getEstimatedMonthlySavings(),
            candidate.getCandidateSku(),
            candidate.getGenerationTag(),
            candidate.getEstimatedMonthlyCost(),
            candidate.getPros(),
            candidate.getCons(),
            recommendation.getCreatedAt(),
            recommendation.getUpdatedAt()
        );
    }
}
