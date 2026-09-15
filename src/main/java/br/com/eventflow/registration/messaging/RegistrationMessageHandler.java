package br.com.eventflow.registration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMessageHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RegistrationMessageHandler.class
            );

    public void handle(
            RegistrationMessage message
    ) {
        if (message.eventType() == null
                || message.eventType().isBlank()) {

            logger.warn(
                    "Consumed registration event with invalid event type: registrationId={}",
                    message.registrationId()
            );

            return;
        }

        switch (message.eventType()) {

            case "registration.confirmed" ->
                    logger.info(
                            "Consumed registration confirmed event: registrationId={}, eventId={}, participantId={}",
                            message.registrationId(),
                            message.eventId(),
                            message.participantId()
                    );

            case "registration.cancelled" ->
                    logger.info(
                            "Consumed registration cancelled event: registrationId={}, eventId={}, participantId={}",
                            message.registrationId(),
                            message.eventId(),
                            message.participantId()
                    );

            case "registration.expired" ->
                    logger.info(
                            "Consumed registration expired event: registrationId={}, eventId={}, participantId={}",
                            message.registrationId(),
                            message.eventId(),
                            message.participantId()
                    );

            default ->
                    logger.warn(
                            "Consumed unsupported registration event type: eventType={}, registrationId={}",
                            message.eventType(),
                            message.registrationId()
                    );
        }
    }
}