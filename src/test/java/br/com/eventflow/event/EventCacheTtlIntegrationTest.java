package br.com.eventflow.event;

import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(
        properties =
                "spring.cache.redis.time-to-live=1s"
)
class EventCacheTtlIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private EventCachedReadService eventCachedReadService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldReloadEventAfterCacheTtlExpires()
            throws Exception {

        User organizer =
                createOrganizer();

        Event event =
                new Event(
                        organizer,
                        "Original Event",
                        "TTL integration test",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("50.00")
                );

        event.publish(
                OffsetDateTime.now()
                        .minusDays(1)
        );

        event =
                eventRepository.saveAndFlush(
                        event
                );

        Long eventId =
                event.getEventId();


        EventResponse firstResponse =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Original Event",
                firstResponse.name()
        );

        jdbcTemplate.update(
                """
                UPDATE events
                SET name = ?
                WHERE event_id = ?
                """,
                "Updated In PostgreSQL",
                eventId
        );

        entityManager.clear();

        EventResponse beforeExpiration =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Original Event",
                beforeExpiration.name()
        );

        Thread.sleep(1_500);

        EventResponse afterExpiration =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Updated In PostgreSQL",
                afterExpiration.name()
        );
    }

    private User createOrganizer() {
        String suffix =
                UUID.randomUUID().toString();

        User organizer =
                new User(
                        "TTL Cache Organizer",
                        "ttl-cache-organizer-"
                                + suffix
                                + "@example.com",
                        "encoded-password",
                        UserRole.ORGANIZER
                );

        return userRepository
                .saveAndFlush(organizer);
    }
}