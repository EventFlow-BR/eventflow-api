package br.com.eventflow.event;

import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.event.dto.UpdateEventRequest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class EventUpdateIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldReturnUpdatedTimestampAfterEventUpdate() {
        User organizer = new User(
                "Integration Organizer",
                "event-update-integration@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        organizer = userRepository.saveAndFlush(organizer);

        Event event = new Event(
                organizer,
                "Original Event",
                "Original description",
                "Petrópolis",
                OffsetDateTime.parse(
                        "2026-10-10T10:00:00-03:00"
                ),
                OffsetDateTime.parse(
                        "2026-10-10T18:00:00-03:00"
                ),
                100,
                new BigDecimal("50.00")
        );

        event = eventRepository.saveAndFlush(event);

        OffsetDateTime previousUpdatedAt =
                event.getUpdatedAt();

        UpdateEventRequest request =
                new UpdateEventRequest(
                        "Updated Event",
                        "Updated description",
                        "Teresópolis",
                        OffsetDateTime.parse(
                                "2026-11-10T10:00:00-03:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-11-10T18:00:00-03:00"
                        ),
                        200,
                        new BigDecimal("75.00")
                );

        EventResponse response =
                eventService.updateEvent(
                        event.getEventId(),
                        organizer.getUserId(),
                        request
                );

        assertNotNull(response.updatedAt());

        assertTrue(
                response.updatedAt()
                        .isAfter(previousUpdatedAt),
                "updatedAt returned by the service should be newer"
        );

        entityManager.clear();

        Event persistedEvent =
                eventRepository.findById(event.getEventId())
                        .orElseThrow();

        assertEquals(
                response.updatedAt().toInstant(),
                persistedEvent.getUpdatedAt().toInstant()
        );

        assertEquals(
                "Updated Event",
                persistedEvent.getName()
        );
    }
}