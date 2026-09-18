package br.com.eventflow.registration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class RegistrationMessageProcessor {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RegistrationMessageProcessor.class
            );

    private final ProcessedMessageRepository processedMessageRepository;
    private final RegistrationMessageHandler registrationMessageHandler;

    public RegistrationMessageProcessor(
            ProcessedMessageRepository processedMessageRepository,
            RegistrationMessageHandler registrationMessageHandler
    ) {
        this.processedMessageRepository =
                processedMessageRepository;

        this.registrationMessageHandler =
                registrationMessageHandler;
    }

    @Transactional
    public void process(
            RegistrationMessage message
    ) {
        OffsetDateTime processedAt =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        int inserted =
                processedMessageRepository.tryInsert(
                        message.messageId(),
                        processedAt
                );

        if (inserted == 0) {
            logger.info(
                    "Ignoring duplicate registration message: messageId={}, eventType={}",
                    message.messageId(),
                    message.eventType()
            );

            return;
        }

        registrationMessageHandler.handle(message);
    }
}