package com.cloudfinsight.collectorservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RECOMMENDATIONS_EXCHANGE = "finsight.recommendations.exchange";
    public static final String RECOMMENDATIONS_QUEUE = "finsight.recommendations.queue";
    public static final String RECOMMENDATIONS_ROUTING_KEY = "recommendation.created";

    @Bean
    public TopicExchange recommendationsExchange() {
        return new TopicExchange(RECOMMENDATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue recommendationsQueue() {
        return new Queue(RECOMMENDATIONS_QUEUE, true);
    }

    @Bean
    public Binding recommendationsBinding(Queue recommendationsQueue, TopicExchange recommendationsExchange) {
        return BindingBuilder
                .bind(recommendationsQueue)
                .to(recommendationsExchange)
                .with(RECOMMENDATIONS_ROUTING_KEY);
    }
}
