package com.alexeisoki.vibeboot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class RabbitMqConfig {

    public static final String DEPLOYMENT_EXCHANGE = "deployment.exchange";
    public static final String DEPLOYMENT_REQUESTED_QUEUE = "deployment.requested.queue";
    public static final String DEPLOYMENT_REQUESTED_ROUTING_KEY = "deployment.requested";

    @Bean
    public DirectExchange deploymentExchange() {
        return new DirectExchange(DEPLOYMENT_EXCHANGE);
    }

    @Bean
    public Queue deploymentRequestedQueue() {
        return QueueBuilder.durable(DEPLOYMENT_REQUESTED_QUEUE).build();
    }

    @Bean
    public Binding deploymentRequestedBinding() {
        return BindingBuilder
                .bind(deploymentRequestedQueue())
                .to(deploymentExchange())
                .with(DEPLOYMENT_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    @Profile("!test")
    public ApplicationRunner rabbitMqTopologyInitializer(RabbitAdmin rabbitAdmin) {
        return args -> rabbitAdmin.initialize();
    }
}
