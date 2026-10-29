package br.com.eventflow.registration;

import br.com.eventflow.registration.dto.RegistrationResponse;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegistrationController.class)
@Import(GlobalExceptionHandler.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void shouldCreateReservationUsingAuthenticatedParticipantId()
            throws Exception {

        RegistrationResponse response =
                new RegistrationResponse(
                        500L,
                        100L,
                        20L,
                        RegistrationStatus.PENDING,
                        OffsetDateTime.parse(
                                "2026-09-08T15:15:00-03:00"
                        ),
                        null,
                        null
                );

        when(registrationService.createReservation(
                100L,
                20L
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/events/100/registrations")
                                .principal(
                                        participantAuthentication()
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id").value(500)
                )
                .andExpect(
                        jsonPath("$.eventId").value(100)
                )
                .andExpect(
                        jsonPath("$.participantId").value(20)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDING")
                )
                .andExpect(
                        jsonPath("$.reservationExpiresAt")
                                .exists()
                );

        verify(registrationService)
                .createReservation(
                        100L,
                        20L
                );
    }

    @Test
    void shouldReturnConflictWhenReservationCannotBeCreated()
            throws Exception {

        when(registrationService.createReservation(
                100L,
                20L
        )).thenThrow(
                new ConflictException(
                        "Event has no available capacity"
                )
        );

        mockMvc.perform(
                        post("/api/events/100/registrations")
                                .principal(
                                        participantAuthentication()
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status").value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Event has no available capacity"
                                )
                );
    }

    private UsernamePasswordAuthenticationToken
    participantAuthentication() {

        return new UsernamePasswordAuthenticationToken(
                20L,
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_PARTICIPANT"
                        )
                )
        );
    }
}