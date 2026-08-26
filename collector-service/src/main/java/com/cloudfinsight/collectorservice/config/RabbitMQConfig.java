package com.cloudfinsight.collectorservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {

    public static final String RECOMMENDATIONS_EXCHANGE = "cost-platform.recommendations";
    public static final String RECOMMENDATIONS_QUEUE = "recommendations.queue";
    public static final String RECOMMENDATIONS_ROUTING_KEY = "recommendation.created";

    public static final String DLQ_EXCHANGE = "cost-platform.dlq";
    public static final String DLQ_QUEUE = "recommendations.dlq";
    public static final String DLQ_ROUTING_KEY = "recommendation.dead";

    private static final long MESSAGE_TTL_MS = Duration.ofHours(24).toMillis();

    // --- Dead-letter side ---

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    // --- Main side ---

    @Bean
    public DirectExchange recommendationsExchange() {
        return new DirectExchange(RECOMMENDATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue recommendationsQueue() {
        return QueueBuilder.durable(RECOMMENDATIONS_QUEUE)
            .deadLetterExchange(DLQ_EXCHANGE)
            .deadLetterRoutingKey(DLQ_ROUTING_KEY)
            .ttl((int) MESSAGE_TTL_MS)
            .build();
    }

    @Bean
    public Binding recommendationsBinding(Queue recommendationsQueue, DirectExchange recommendationsExchange) {
        return BindingBuilder.bind(recommendationsQueue).to(recommendationsExchange).with(RECOMMENDATIONS_ROUTING_KEY);
    }

    // --- Publisher support (Task 5.2) ---

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          JacksonJsonMessageConverter jacksonJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonJsonMessageConverter);
        return template;
    }
}
