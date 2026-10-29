package br.com.eventflow.registration;

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

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RegistrationCancellationController.class
)
@Import(GlobalExceptionHandler.class)
class RegistrationCancellationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationCancellationService cancellationService;

    @Test
    void shouldCancelRegistrationUsingAuthenticatedParticipantId()
            throws Exception {

        mockMvc.perform(
                        post("/api/registrations/100/cancel")
                                .principal(
                                        participantAuthentication()
                                )
                )
                .andExpect(status().isNoContent());

        verify(cancellationService)
                .cancelRegistration(
                        100L,
                        20L
                );
    }

    @Test
    void shouldReturnConflictWhenRegistrationCannotBeCancelled()
            throws Exception {

        doThrow(
                new ConflictException(
                        "Registration cannot be cancelled in its current status"
                )
        ).when(cancellationService)
                .cancelRegistration(
                        100L,
                        20L
                );

        mockMvc.perform(
                        post("/api/registrations/100/cancel")
                                .principal(
                                        participantAuthentication()
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Registration cannot be cancelled in its current status"
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