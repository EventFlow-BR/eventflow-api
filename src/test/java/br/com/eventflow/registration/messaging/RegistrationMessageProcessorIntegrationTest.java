package br.com.eventflow.registration.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationMessageProcessorIntegrationTest {

    @Autowired
    private RegistrationMessageProcessor processor;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @MockitoBean
    private RegistrationMessageHandler registrationMessageHandler;

    @BeforeEach
    void setUp() {
        processedMessageRepository.deleteAll();
    }

    @Test
    void shouldProcessDuplicateMessageOnlyOnce() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        processor.process(message);
        processor.process(message);

        verify(
                registrationMessageHandler,
                times(1)
        ).handle(message);

        assertTrue(
                processedMessageRepository.existsById(
                        messageId
                )
        );
    }

    @Test
    void shouldRollbackProcessedMessageWhenHandlingFailsAndAllowRetry() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        doThrow(
                new RuntimeException(
                        "Simulated processing failure"
                )
        )
                .doNothing()
                .when(registrationMessageHandler)
                .handle(message);

        assertThrows(
                RuntimeException.class,
                () -> processor.process(message)
        );

        assertFalse(
                processedMessageRepository.existsById(
                        messageId
                )
        );

        processor.process(message);

        verify(
                registrationMessageHandler,
                times(2)
        ).handle(message);

        assertTrue(
                processedMessageRepository.existsById(
                        messageId
                )
        );
    }

    @Test
    void shouldProcessDifferentMessagesIndependently() {
        UUID firstMessageId =
                UUID.randomUUID();

        UUID secondMessageId =
                UUID.randomUUID();

        RegistrationMessage firstMessage =
                message(firstMessageId);

        RegistrationMessage secondMessage =
                message(secondMessageId);

        processor.process(firstMessage);
        processor.process(secondMessage);

        verify(registrationMessageHandler)
                .handle(firstMessage);

        verify(registrationMessageHandler)
                .handle(secondMessage);

        assertTrue(
                processedMessageRepository.existsById(
                        firstMessageId
                )
        );

        assertTrue(
                processedMessageRepository.existsById(
                        secondMessageId
                )
        );
    }

    @Test
    void shouldProcessConcurrentDuplicateMessageOnlyOnce() throws Exception {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        try {
            Future<?> firstExecution =
                    executorService.submit(() -> {
                        await(startLatch);
                        processor.process(message);
                    });

            Future<?> secondExecution =
                    executorService.submit(() -> {
                        await(startLatch);
                        processor.process(message);
                    });

            startLatch.countDown();

            firstExecution.get();
            secondExecution.get();

            verify(
                    registrationMessageHandler,
                    times(1)
            ).handle(message);

            assertTrue(
                    processedMessageRepository.existsById(
                            messageId
                    )
            );

            assertEquals(
                    1,
                    processedMessageRepository.count()
            );

        } finally {
            executorService.shutdownNow();
        }
    }

    private RegistrationMessage message(
            UUID messageId
    ) {
        return new RegistrationMessage(
                messageId,
                100L,
                200L,
                20L,
                "registration.confirmed",
                OffsetDateTime.now()
        );
    }

    private void await(
            CountDownLatch latch
    ) {
        try {
            latch.await();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }
}