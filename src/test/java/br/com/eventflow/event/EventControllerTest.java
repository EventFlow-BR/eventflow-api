package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.BadRequestException;
import br.com.eventflow.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class)
@Import(GlobalExceptionHandler.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void shouldCreateEventUsingAuthenticatedOrganizerId()
            throws Exception {

        EventResponse response =
                new EventResponse(
                        100L,
                        10L,
                        "Java Conference",
                        "Backend conference",
                        "Petrópolis",
                        OffsetDateTime.parse(
                                "2026-10-10T10:00:00-03:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-10-10T18:00:00-03:00"
                        ),
                        100,
                        new BigDecimal("50.00"),
                        EventStatus.DRAFT,
                        null,
                        null,
                        null
                );

        when(eventService.createEvent(
                eq(10L),
                any(CreateEventRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/events")
                                .principal(
                                        new UsernamePasswordAuthenticationToken(
                                                10L,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_ORGANIZER"
                                                        )
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestBody())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.organizerId").value(10))
                .andExpect(jsonPath("$.name")
                        .value("Java Conference"))
                .andExpect(jsonPath("$.status")
                        .value("DRAFT"));

        verify(eventService)
                .createEvent(
                        eq(10L),
                        any(CreateEventRequest.class)
                );
    }

    @Test
    void shouldReturnBadRequestWhenPayloadIsInvalid()
            throws Exception {

        mockMvc.perform(
                        post("/api/events")
                                .principal(
                                        organizerAuthentication()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "",
                                          "description": "",
                                          "location": "",
                                          "startDate": null,
                                          "endDate": null,
                                          "capacity": 0,
                                          "price": -1
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.location").exists())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void shouldReturnBadRequestWhenPriceExceedsAllowedPrecision()
            throws Exception {

        mockMvc.perform(
                        post("/api/events")
                                .principal(
                                        organizerAuthentication()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Java Conference",
                                          "description": "Backend conference",
                                          "location": "Petrópolis",
                                          "startDate": "2026-10-10T10:00:00-03:00",
                                          "endDate": "2026-10-10T18:00:00-03:00",
                                          "capacity": 100,
                                          "price": 123456789.123
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenServiceRejectsInvalidDates()
            throws Exception {

        when(eventService.createEvent(
                eq(10L),
                any(CreateEventRequest.class)
        )).thenThrow(
                new BadRequestException(
                        "Event start must be before end date"
                )
        );

        mockMvc.perform(
                        post("/api/events")
                                .principal(
                                        organizerAuthentication()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestBody())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Event start must be before end date"));
    }

    private UsernamePasswordAuthenticationToken organizerAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                10L,
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_ORGANIZER"
                        )
                )
        );
    }

    private String validRequestBody() {
        return """
                {
                  "name": "Java Conference",
                  "description": "Backend conference",
                  "location": "Petrópolis",
                  "startDate": "2026-10-10T10:00:00-03:00",
                  "endDate": "2026-10-10T18:00:00-03:00",
                  "capacity": 100,
                  "price": 50.00
                }
                """;
    }
}