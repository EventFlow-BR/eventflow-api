package br.com.eventflow.shared.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
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

    public static final String DEAD_LETTER_EXCHANGE =
            "eventflow.events.dlx";

    public static final String REGISTRATION_EVENTS_DLQ =
            "eventflow.registration.events.dlq";

    public static final String REGISTRATION_DEAD_LETTER_ROUTING_KEY =
            "registration.dead";

    @Bean
    TopicExchange eventflowEventsExchange() {
        return new TopicExchange(
                EVENTS_EXCHANGE
        );
    }

    @Bean
    Queue registrationEventsQueue() {
        return QueueBuilder
                .durable(
                        REGISTRATION_EVENTS_QUEUE
                )
                .deadLetterExchange(
                        DEAD_LETTER_EXCHANGE
                )
                .deadLetterRoutingKey(
                        REGISTRATION_DEAD_LETTER_ROUTING_KEY
                )
                .build();
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
        JacksonJsonMessageConverter converter =
                new JacksonJsonMessageConverter();

        DefaultJacksonJavaTypeMapper typeMapper =
                new DefaultJacksonJavaTypeMapper();

        typeMapper.setTrustedPackages(
                "br.com.eventflow.registration.messaging"
        );

        converter.setJavaTypeMapper(typeMapper);

        return converter;
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

    @Bean
    TopicExchange eventflowDeadLetterExchange() {
        return new TopicExchange(
                DEAD_LETTER_EXCHANGE
        );
    }

    @Bean
    Queue registrationEventsDeadLetterQueue() {
        return QueueBuilder
                .durable(
                        REGISTRATION_EVENTS_DLQ
                )
                .build();
    }

    @Bean
    Binding registrationEventsDeadLetterBinding(
            Queue registrationEventsDeadLetterQueue,
            TopicExchange eventflowDeadLetterExchange
    ) {
        return BindingBuilder
                .bind(
                        registrationEventsDeadLetterQueue
                )
                .to(
                        eventflowDeadLetterExchange
                )
                .with(
                        REGISTRATION_DEAD_LETTER_ROUTING_KEY
                );
    }
}