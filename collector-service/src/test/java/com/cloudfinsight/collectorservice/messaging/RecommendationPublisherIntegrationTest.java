package com.cloudfinsight.collectorservice.messaging;

import com.cloudfinsight.collectorservice.config.RabbitMQConfig;
import com.cloudfinsight.collectorservice.entity.Recommendation;
import com.cloudfinsight.collectorservice.entity.RecommendationCandidate;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.model.RecommendationMessage;
import com.cloudfinsight.collectorservice.repository.RecommendationCandidateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Live integration test for Task 5.2 subtask 5: proves a packaged recommendation actually
 * lands on the real {@code cost-platform.recommendations} exchange / {@code recommendations.queue}
 * against a real broker (TestContainers), rather than mocking RabbitTemplate. Topology is declared
 * imperatively via RabbitAdmin, referencing the same constants as {@link RabbitMQConfig} so it
 * can't silently drift from the production topology.
 */
@Testcontainers
class RecommendationPublisherIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer =
        new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private RecommendationCandidateRepository recommendationCandidateRepository;
    private MeterRegistry meterRegistry;
    private RecommendationPublisher publisher;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(
            rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort());
        connectionFactory.setUsername(rabbitMQContainer.getAdminUsername());
        connectionFactory.setPassword(rabbitMQContainer.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        DirectExchange exchange = new DirectExchange(RabbitMQConfig.RECOMMENDATIONS_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(RabbitMQConfig.RECOMMENDATIONS_QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(RabbitMQConfig.RECOMMENDATIONS_ROUTING_KEY);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new JacksonJsonMessageConverter(new JsonMapper(), "com.cloudfinsight.collectorservice.model"));

        recommendationCandidateRepository = mock(RecommendationCandidateRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new RecommendationPublisher(rabbitTemplate, recommendationCandidateRepository, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void publish_deliversMessageToQueue_andRecordsConfirmAck() {
        Recommendation recommendation = recommendation(9L, 1L, "DOWNSIZE", "LOW",
            new BigDecimal("1.56"), new BigDecimal("49.06"), "Test summary");
        RecommendationCandidate candidate = candidate("Standard_B2s", "OLDER_SUPPORTED",
            new BigDecimal("38.54"), "Some pros", "Some cons");

        when(recommendationCandidateRepository.findByRecommendationIdAndSelectedTrue(9L))
            .thenReturn(Optional.of(candidate));

        publisher.publish(recommendation);

        RecommendationMessage received = (RecommendationMessage) rabbitTemplate.receiveAndConvert(
            RabbitMQConfig.RECOMMENDATIONS_QUEUE, 5000);

        assertThat(received).isNotNull();
        assertThat(received.recommendationId()).isEqualTo(9L);
        assertThat(received.vmId()).isEqualTo(1L);
        assertThat(received.recommendationType()).isEqualTo("DOWNSIZE");
        assertThat(received.confidenceLevel()).isEqualTo("LOW");
        assertThat(received.candidateSku()).isEqualTo("Standard_B2s");
        assertThat(received.candidateGenerationTag()).isEqualTo("OLDER_SUPPORTED");
        assertThat(received.estimatedMonthlySavings()).isEqualByComparingTo("49.06");

        awaitConfirm();
        assertThat(meterRegistry.counter("recommendations.published.total", "result", "success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("recommendations.published.total", "result", "failure").count()).isEqualTo(0.0);
    }

    @Test
    void publish_noSelectedCandidate_doesNotPublish() {
        Recommendation recommendation = recommendation(10L, 1L, "DOWNSIZE", "LOW",
            new BigDecimal("1.0"), new BigDecimal("10.0"), "No candidate case");

        when(recommendationCandidateRepository.findByRecommendationIdAndSelectedTrue(10L))
            .thenReturn(Optional.empty());

        publisher.publish(recommendation);

        Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfig.RECOMMENDATIONS_QUEUE, 1000);
        assertThat(received).isNull();
    }

    /**
     * Publisher confirms arrive asynchronously on a separate callback thread; give it a moment
     * before asserting on the counters rather than racing the confirm callback.
     */
    private void awaitConfirm() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Recommendation recommendation(Long id, Long vmId, String type, String confidenceLevel,
                                           BigDecimal confidenceScore, BigDecimal savings, String summary) {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(vmId);

        Recommendation recommendation = new Recommendation();
        recommendation.setId(id);
        recommendation.setVirtualMachine(vm);
        recommendation.setRecommendationType(type);
        recommendation.setConfidenceLevel(confidenceLevel);
        recommendation.setConfidenceScore(confidenceScore);
        recommendation.setEstimatedMonthlySavings(savings);
        recommendation.setSummary(summary);
        recommendation.setCreatedAt(OffsetDateTime.now());
        recommendation.setUpdatedAt(OffsetDateTime.now());
        return recommendation;
    }

    private RecommendationCandidate candidate(String sku, String generationTag,
                                               BigDecimal monthlyCost, String pros, String cons) {
        RecommendationCandidate candidate = new RecommendationCandidate();
        candidate.setCandidateSku(sku);
        candidate.setGenerationTag(generationTag);
        candidate.setEstimatedMonthlyCost(monthlyCost);
        candidate.setPros(pros);
        candidate.setCons(cons);
        candidate.setSelected(true);
        return candidate;
    }
}
