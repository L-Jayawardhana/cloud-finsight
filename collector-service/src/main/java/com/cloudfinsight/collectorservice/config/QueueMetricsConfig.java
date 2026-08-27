package com.cloudfinsight.collectorservice.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Exposes RabbitMQ queue depth as a Prometheus gauge (Epic 5 acceptance criterion:
 * "Queue depth is exposed as a Prometheus metric on both services"). Each gauge read
 * queries the broker live via RabbitAdmin.getQueueProperties at scrape time, rather than
 * polling on a schedule -- Micrometer gauges are pull-based, so this only costs a round
 * trip when Prometheus actually scrapes /actuator/prometheus.
 *
 * Tracks both the live queue and the DLQ: DLQ depth doubles as a direct signal that
 * messages are landing there rather than being silently lost, which is exactly what the
 * epic's DLQ acceptance criterion is trying to verify.
 */
@Configuration
public class QueueMetricsConfig {

    public QueueMetricsConfig(AmqpAdmin rabbitAdmin, MeterRegistry meterRegistry) {
        registerQueueDepthGauge(rabbitAdmin, meterRegistry, RabbitMQConfig.RECOMMENDATIONS_QUEUE);
        registerQueueDepthGauge(rabbitAdmin, meterRegistry, RabbitMQConfig.DLQ_QUEUE);
    }

    private void registerQueueDepthGauge(AmqpAdmin rabbitAdmin, MeterRegistry meterRegistry, String queueName) {
        Gauge.builder("rabbitmq.queue.messages", rabbitAdmin, admin -> currentDepth(admin, queueName))
            .description("Current message count in the queue, as reported by the broker")
            .tag("queue", queueName)
            .register(meterRegistry);
    }

    private double currentDepth(AmqpAdmin rabbitAdmin, String queueName) {
        Properties props = rabbitAdmin.getQueueProperties(queueName);
        if (props == null) {
            return 0;
        }
        Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number number ? number.doubleValue() : 0;
    }
}
