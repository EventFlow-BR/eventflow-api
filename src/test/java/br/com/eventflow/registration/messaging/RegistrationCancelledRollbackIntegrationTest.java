package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.event.RegistrationCancelledEvent;
import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;


class RegistrationCancelledRollbackIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldNotPublishRegistrationCancelledMessageWhenTransactionRollsBack() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {
                    applicationEventPublisher.publishEvent(
                            new RegistrationCancelledEvent(
                                    100L,
                                    200L,
                                    20L,
                                    OffsetDateTime.now()
                            )
                    );

                    status.setRollbackOnly();
                }
        );

        Message receivedMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        1_000
                );

        assertNull(receivedMessage);
    }
}