package br.com.eventflow.payment;

import br.com.eventflow.payment.dto.PaymentResponse;
import br.com.eventflow.payment.enums.PaymentStatus;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldProcessPaymentUsingAuthenticatedParticipantId()
            throws Exception {

        PaymentResponse response =
                new PaymentResponse(
                        500L,
                        100L,
                        new BigDecimal("50.00"),
                        PaymentStatus.APPROVED,
                        OffsetDateTime.parse(
                                "2026-09-08T18:00:00-03:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-09-08T18:00:00-03:00"
                        )
                );

        when(paymentService.processPayment(
                100L,
                20L
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/registrations/100/payment")
                                .principal(
                                        participantAuthentication()
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id").value(500)
                )
                .andExpect(
                        jsonPath("$.registrationId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(50.00)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("APPROVED")
                );

        verify(paymentService)
                .processPayment(
                        100L,
                        20L
                );
    }

    @Test
    void shouldReturnConflictWhenPaymentCannotBeProcessed()
            throws Exception {

        when(paymentService.processPayment(
                100L,
                20L
        )).thenThrow(
                new ConflictException(
                        "Registration reservation has expired"
                )
        );

        mockMvc.perform(
                        post("/api/registrations/100/payment")
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
                                        "Registration reservation has expired"
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