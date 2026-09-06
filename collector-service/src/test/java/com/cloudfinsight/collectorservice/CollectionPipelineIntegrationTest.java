package com.cloudfinsight.collectorservice;

import com.cloudfinsight.collectorservice.client.AzureMonitorClient;
import com.cloudfinsight.collectorservice.entity.PricingSnapshot;
import com.cloudfinsight.collectorservice.entity.VirtualMachine;
import com.cloudfinsight.collectorservice.mapper.MetricSnapshotMapper;
import com.cloudfinsight.collectorservice.messaging.RecommendationPublisher;
import com.cloudfinsight.collectorservice.repository.MetricSnapshotRepository;
import com.cloudfinsight.collectorservice.repository.PricingSnapshotRepository;
import com.cloudfinsight.collectorservice.repository.RecommendationRepository;
import com.cloudfinsight.collectorservice.repository.VirtualMachineRepository;
import com.cloudfinsight.collectorservice.scheduler.AnalysisScheduler;
import com.cloudfinsight.collectorservice.scheduler.MetricCollectionScheduler;
import com.cloudfinsight.collectorservice.service.CandidateGenerator;
import com.cloudfinsight.collectorservice.service.RecommendationPackager;
import com.cloudfinsight.collectorservice.service.SavingsCalculator;
import com.cloudfinsight.collectorservice.service.TradeOffScorer;
import com.cloudfinsight.collectorservice.service.UtilisationAggregator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Task 10.1: real end-to-end test of the collection -> analysis -> publish pipeline against
 * real PostgreSQL and RabbitMQ (Testcontainers). Flyway migrations are api-service's own
 * migration files, copied into src/test/resources/db/migration - collector-service has no
 * schema of its own (flyway.enabled=false in its main config; it shares api-service's DB in
 * every other environment), so an isolated test needs its own copy to build a real schema.
 *
 * The Azure Monitor Query SDK client (queryMetrics) does its own auth/HTTP internally and
 * isn't a clean WireMock seam without also faking token acquisition, so AzureMonitorClient
 * itself is mocked here at the Java boundary rather than wire-mocking raw HTTP - real
 * Postgres and real RabbitMQ are what this test actually exercises end-to-end.
 *
 * The two @ConditionalOnProperty scheduler beans (gated on collector.scheduling.enabled,
 * set false here to stop them firing on their own timers) are constructed manually from
 * autowired real collaborators, so this test controls exactly when each cycle runs.
 */
@SpringBootTest
@Testcontainers
class CollectionPipelineIntegrationTest {

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
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);

        registry.add("collector.scheduling.enabled", () -> "false");
    }

    private static final String VM_NAME = "vm-current-gen-d2sv4";
    private static final String CURRENT_SKU = "Standard_D2s_v4";
    private static final String CANDIDATE_SKU = "Standard_B2s";

    @Autowired
    private VirtualMachineRepository virtualMachineRepository;
    @Autowired
    private MetricSnapshotRepository metricSnapshotRepository;
    @Autowired
    private PricingSnapshotRepository pricingSnapshotRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;
    @Autowired
    private UtilisationAggregator utilisationAggregator;
    @Autowired
    private CandidateGenerator candidateGenerator;
    @Autowired
    private TradeOffScorer tradeOffScorer;
    @Autowired
    private SavingsCalculator savingsCalculator;
    @Autowired
    private RecommendationPackager recommendationPackager;
    @Autowired
    private RecommendationPublisher recommendationPublisher;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private VirtualMachine vm;

    @BeforeEach
    void setUp() {
        vm = virtualMachineRepository.findAll().stream()
            .filter(v -> VM_NAME.equals(v.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(VM_NAME + " missing from seeded fixture data"));

        seedPricing(CURRENT_SKU, new BigDecimal("0.12"));
        seedPricing(CANDIDATE_SKU, new BigDecimal("0.0528"));
    }

    private void seedPricing(String armSkuName, BigDecimal hourlyPrice) {
        PricingSnapshot snapshot = new PricingSnapshot();
        snapshot.setArmSkuName(armSkuName);
        snapshot.setRegion("southeastasia");
        snapshot.setOsType("Linux");
        snapshot.setRetailPrice(hourlyPrice);
        snapshot.setCurrency("USD");
        snapshot.setCollectedAt(OffsetDateTime.now());
        pricingSnapshotRepository.save(snapshot);
    }

    @Test
    void fullCollectionCycle_persistsSnapshots_thenAnalysisPublishesRecommendation() {
        AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
        when(azureMonitorClient.queryMetrics(vm.getAzureResourceId(), Duration.ofHours(1)))
            .thenReturn(List.of(
                new AzureMonitorClient.RawMetricPoint("Percentage CPU", 5.0, OffsetDateTime.now().minusMinutes(10)),
                new AzureMonitorClient.RawMetricPoint("Percentage CPU", 6.0, OffsetDateTime.now().minusMinutes(5))
            ));
        when(azureMonitorClient.queryMemoryUsagePercent(vm.getAzureResourceId()))
            .thenReturn(Optional.of(
                new AzureMonitorClient.RawMetricPoint("Memory Percentage", 8.0, OffsetDateTime.now())));

        Counter successCounter = meterRegistry.counter("test.collection.success");
        Counter failureCounter = meterRegistry.counter("test.collection.failure");
        MetricCollectionScheduler metricCollectionScheduler = new MetricCollectionScheduler(
            virtualMachineRepository, metricSnapshotRepository, azureMonitorClient, metricSnapshotMapper,
            successCounter, failureCounter, new AtomicLong(0), meterRegistry);

        metricCollectionScheduler.collectMetrics();

        List<com.cloudfinsight.collectorservice.entity.MetricSnapshot> snapshots =
            metricSnapshotRepository.findByVirtualMachineIdAndCollectedAtBetween(
                vm.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now().plusMinutes(1));
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots).extracting("metricName")
            .containsExactlyInAnyOrder("percentage_cpu", "percentage_cpu", "memory_percentage");

        AnalysisScheduler analysisScheduler = new AnalysisScheduler(
            virtualMachineRepository, utilisationAggregator, candidateGenerator, tradeOffScorer,
            savingsCalculator, recommendationPackager, recommendationPublisher, meterRegistry);

        analysisScheduler.runAnalysis();

        assertThat(recommendationRepository.findAll())
            .anyMatch(r -> r.getVirtualMachine().getId().equals(vm.getId())
                && "DOWNSIZE".equals(r.getRecommendationType()));

        Message message = rabbitTemplate.receive("recommendations.queue", 5000);
        assertThat(message).as("a recommendation message should have been published to the queue").isNotNull();
    }
}
