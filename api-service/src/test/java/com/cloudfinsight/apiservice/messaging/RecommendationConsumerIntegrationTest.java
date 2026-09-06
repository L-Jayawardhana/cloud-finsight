package com.cloudfinsight.apiservice.messaging;

import com.cloudfinsight.apiservice.config.RabbitMQConfig;
import com.cloudfinsight.apiservice.entity.Recommendation;
import com.cloudfinsight.apiservice.model.RecommendationMessage;
import com.cloudfinsight.apiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Task 10.1 consumer-side test: publishes a real RecommendationMessage onto a real
 * RabbitMQ queue (Testcontainers) and asserts RecommendationConsumer's @RabbitListener
 * upserts it into a real PostgreSQL recommendations table (Testcontainers, migrated by
 * api-service's own Flyway scripts - it owns this schema, unlike collector-service).
 * Consumption is asynchronous (a separate listener container thread), so the assertion
 * polls rather than checking immediately after publish.
 */
@SpringBootTest
@Testcontainers
class RecommendationConsumerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static final RabbitMQContainer rabbitMQContainer =
        new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RecommendationRepository recommendationRepository;

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void consumingRecommendationMessage_upsertsRecommendationsTable() {
        // vmId 1 is vm-current-gen-d2sv4, seeded by api-service's own V3 migration.
        RecommendationMessage payload = new RecommendationMessage(
            9001L, 1L, "PENDING", "DOWNSIZE", "LOW",
            new BigDecimal("1.56"), "Integration test recommendation",
            new BigDecimal("49.06"), "Standard_B2s", "OLDER_SUPPORTED",
            new BigDecimal("38.54"), "Test pro", "Test con",
            OffsetDateTime.now(), OffsetDateTime.now());

        byte[] body = jsonMapper.writeValueAsBytes(payload);
        Message message = MessageBuilder.withBody(body).build();

        rabbitTemplate.send(RabbitMQConfig.RECOMMENDATIONS_EXCHANGE, RabbitMQConfig.RECOMMENDATIONS_ROUTING_KEY, message);

        await().atMost(10, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Recommendation> saved = recommendationRepository.findById(9001L);
            assertThat(saved).isPresent();
            assertThat(saved.get().getRecommendationType()).isEqualTo("DOWNSIZE");
            assertThat(saved.get().getEstimatedMonthlySavings())
                .isEqualByComparingTo(new BigDecimal("49.06"));
            assertThat(saved.get().getCreatedAt()).isCloseTo(OffsetDateTime.now(), within(5, ChronoUnit.SECONDS));
        });
    }

    private static org.assertj.core.data.TemporalUnitWithinOffset within(long value, ChronoUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(value, unit);
    }
}
