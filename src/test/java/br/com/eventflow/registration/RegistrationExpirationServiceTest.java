package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationExpirationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    private RegistrationExpirationService expirationService;

    @BeforeEach
    void setUp() {
        expirationService =
                new RegistrationExpirationService(
                        registrationRepository
                );
    }

    @Test
    void shouldExpirePendingRegistrationsPastExpirationTime() {
        Registration first =
                expiredPendingRegistration();

        Registration second =
                expiredPendingRegistration();

        when(registrationRepository
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(
                        List.of(first, second)
                );

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                2,
                expiredCount
        );

        assertEquals(
                RegistrationStatus.EXPIRED,
                first.getStatus()
        );

        assertEquals(
                RegistrationStatus.EXPIRED,
                second.getStatus()
        );

        verify(registrationRepository)
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                );
    }

    @Test
    void shouldDoNothingWhenThereAreNoExpiredPendingRegistrations() {
        when(registrationRepository
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(List.of());

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                0,
                expiredCount
        );

        verify(registrationRepository)
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                );
    }

    private Registration expiredPendingRegistration() {
        User organizer = new User(
                "Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Participant",
                "participant@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        Event event = new Event(
                organizer,
                "Event",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("50.00")
        );

        return new Registration(
                event,
                participant,
                OffsetDateTime.now()
                        .minusMinutes(1)
        );
    }
}