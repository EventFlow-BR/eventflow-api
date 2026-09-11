package br.com.eventflow.shared.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE =
            "eventflow.events";

    public static final String REGISTRATION_EVENTS_QUEUE =
            "eventflow.registration.events";

    public static final String REGISTRATION_ROUTING_PATTERN =
            "registration.*";

    @Bean
    TopicExchange eventflowEventsExchange() {
        return new TopicExchange(
                EVENTS_EXCHANGE
        );
    }

    @Bean
    Queue registrationEventsQueue() {
        return new Queue(
                REGISTRATION_EVENTS_QUEUE,
                true
        );
    }

    @Bean
    Binding registrationEventsBinding(
            Queue registrationEventsQueue,
            TopicExchange eventflowEventsExchange
    ) {
        return BindingBuilder
                .bind(registrationEventsQueue)
                .to(eventflowEventsExchange)
                .with(REGISTRATION_ROUTING_PATTERN);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        RabbitTemplate rabbitTemplate =
                new RabbitTemplate(connectionFactory);

        rabbitTemplate.setMessageConverter(
                rabbitMessageConverter
        );

        return rabbitTemplate;
    }
}