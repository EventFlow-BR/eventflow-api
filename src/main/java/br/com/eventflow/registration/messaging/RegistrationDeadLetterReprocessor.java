package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationDeadLetterReprocessor {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RegistrationDeadLetterReprocessor.class
            );

    private final RabbitTemplate rabbitTemplate;

    public RegistrationDeadLetterReprocessor(
            @Qualifier("registrationReplayRabbitTemplate")
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate =
                rabbitTemplate;
    }

    @Transactional(
            transactionManager = "rabbitTransactionManager"
    )
    public boolean reprocessNext() {
        Object payload =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ
                );

        if (payload == null) {
            logger.info(
                    "No registration message available in dead-letter queue"
            );

            return false;
        }

        RegistrationMessage message =
                (RegistrationMessage) payload;

        String routingKey =
                resolveRoutingKey(message);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                routingKey,
                message
        );

        logger.info(
                "Reprocessed registration dead-letter message: messageId={}, eventType={}",
                message.messageId(),
                message.eventType()
        );

        return true;
    }

    private String resolveRoutingKey(
            RegistrationMessage message
    ) {
        return switch (message.eventType()) {
            case "registration.confirmed",
                 "registration.cancelled",
                 "registration.expired" ->
                    message.eventType();

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported registration event type: "
                                    + message.eventType()
                    );
        };
    }
}