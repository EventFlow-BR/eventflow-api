package br.com.eventflow.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationExpirationSchedulerTest {

    @Mock
    private RegistrationExpirationService expirationService;

    private RegistrationExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new RegistrationExpirationScheduler(
                        expirationService
                );
    }

    @Test
    void shouldDelegateExpirationProcessingToService() {
        scheduler.expirePendingRegistration();

        verify(expirationService)
                .expirePendingRegistrations();
    }
}