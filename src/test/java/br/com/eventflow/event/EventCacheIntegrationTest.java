package br.com.eventflow.event;

import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.event.dto.UpdateEventRequest;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventCacheIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private EventService eventService;

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

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearEventCache() {
        Cache cache =
                cacheManager.getCache("events");

        assertNotNull(cache);

        cache.clear();
    }

    @Test
    void shouldReturnCachedEventOnSecondRead() {
        Event event =
                createPublishedEvent();

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
                "Changed Directly In PostgreSQL",
                eventId
        );

        entityManager.clear();

        EventResponse secondResponse =
                eventCachedReadService.getById(
                        eventId
                );


        assertEquals(
                "Original Event",
                secondResponse.name()
        );
    }

    @Test
    void shouldStillHideCachedDraftEventFromParticipant() {
        User organizer =
                createOrganizer();

        Event event =
                new Event(
                        organizer,
                        "Private Draft",
                        "Draft event",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("50.00")
                );

        event =
                eventRepository.saveAndFlush(
                        event
                );

        Long eventId =
                event.getEventId();

        Long organizerId =
                organizer.getUserId();


        EventResponse organizerResponse =
                eventService.getVisibleEvent(
                        eventId,
                        organizerId,
                        UserRole.ORGANIZER
                );

        assertEquals(
                "Private Draft",
                organizerResponse.name()
        );

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () ->
                                eventService.getVisibleEvent(
                                        eventId,
                                        999999L,
                                        UserRole.PARTICIPANT
                                )
                );

        assertEquals(
                "Event not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnUpdatedEventAfterSuccessfulUpdate() {
        Event event =
                createPublishedEvent();

        Long eventId =
                event.getEventId();

        Long organizerId =
                event.getOrganizer()
                        .getUserId();


        EventResponse beforeUpdate =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Original Event",
                beforeUpdate.name()
        );

        UpdateEventRequest request =
                new UpdateEventRequest(
                        "Updated Event",
                        "Updated description",
                        "Teresópolis",
                        OffsetDateTime.now()
                                .plusDays(10),
                        OffsetDateTime.now()
                                .plusDays(10)
                                .plusHours(8),
                        200,
                        new BigDecimal("75.00")
                );

        eventService.updateEvent(
                eventId,
                organizerId,
                request
        );

        String persistedName =
                jdbcTemplate.queryForObject(
                        """
                        SELECT name
                        FROM events
                        WHERE event_id = ?
                        """,
                        String.class,
                        eventId
                );

        assertEquals(
                "Updated Event",
                persistedName,
                "PostgreSQL should contain the updated event"
        );

        Cache cache =
                cacheManager.getCache("events");

        assertNotNull(cache);

        assertNull(
                cache.get(eventId),
                "Event should have been evicted from Redis after update"
        );

        EventResponse afterUpdate =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Updated Event",
                afterUpdate.name()
        );

        assertEquals(
                "Updated description",
                afterUpdate.description()
        );

        assertEquals(
                "Teresópolis",
                afterUpdate.location()
        );

        assertEquals(
                200,
                afterUpdate.capacity()
        );

        assertEquals(
                new BigDecimal("75.00"),
                afterUpdate.price()
        );
    }

    @Test
    void shouldReturnPublishedEventAfterPublishing() {
        User organizer =
                createOrganizer();

        Event event =
                new Event(
                        organizer,
                        "Draft Event",
                        "Draft event",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("50.00")
                );

        event =
                eventRepository.saveAndFlush(
                        event
                );

        Long eventId =
                event.getEventId();


        EventResponse beforePublishing =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                EventStatus.DRAFT,
                beforePublishing.status()
        );

        eventService.publishEvent(
                eventId,
                organizer.getUserId()
        );


        EventResponse afterPublishing =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                EventStatus.PUBLISHED,
                afterPublishing.status()
        );

        assertNotNull(
                afterPublishing.publishedAt()
        );
    }

    @Test
    void shouldReturnCancelledEventAfterCancellation() {
        Event event =
                createPublishedEvent();

        Long eventId =
                event.getEventId();

        Long organizerId =
                event.getOrganizer()
                        .getUserId();


        EventResponse beforeCancellation =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                EventStatus.PUBLISHED,
                beforeCancellation.status()
        );

        eventService.cancelEvent(
                eventId,
                organizerId
        );

        String persistedStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM events
                        WHERE event_id = ?
                        """,
                        String.class,
                        eventId
                );

        assertEquals(
                "CANCELLED",
                persistedStatus,
                "PostgreSQL should contain the cancelled event"
        );

        Cache cache =
                cacheManager.getCache("events");

        assertNotNull(cache);

        assertNull(
                cache.get(eventId),
                "Event should have been evicted from Redis after cancellation"
        );


        EventResponse afterCancellation =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                EventStatus.CANCELLED,
                afterCancellation.status()
        );
    }

    @Test
    void shouldKeepCachedEventWhenUpdateFails() {
        Event event =
                createPublishedEvent();

        Long eventId =
                event.getEventId();


        EventResponse beforeFailedUpdate =
                eventCachedReadService.getById(
                        eventId
                );

        assertEquals(
                "Original Event",
                beforeFailedUpdate.name()
        );

        UpdateEventRequest request =
                new UpdateEventRequest(
                        "Unauthorized Update",
                        "Description",
                        "Teresópolis",
                        OffsetDateTime.now()
                                .plusDays(10),
                        OffsetDateTime.now()
                                .plusDays(10)
                                .plusHours(8),
                        200,
                        new BigDecimal("75.00")
                );

        assertThrows(
                NotFoundException.class,
                () ->
                        eventService.updateEvent(
                                eventId,
                                999999L,
                                request
                        )
        );

        jdbcTemplate.update(
                """
                UPDATE events
                SET name = ?
                WHERE event_id = ?
                """,
                "Changed Directly In PostgreSQL",
                eventId
        );

        entityManager.clear();

        EventResponse afterFailedUpdate =
                eventCachedReadService.getById(
                        eventId
                );


        assertEquals(
                "Original Event",
                afterFailedUpdate.name()
        );
    }

    private Event createPublishedEvent() {
        User organizer =
                createOrganizer();

        Event event =
                new Event(
                        organizer,
                        "Original Event",
                        "Cache integration test",
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

        return eventRepository
                .saveAndFlush(event);
    }

    private User createOrganizer() {
        String suffix =
                UUID.randomUUID().toString();

        User organizer =
                new User(
                        "Cache Organizer",
                        "cache-organizer-"
                                + suffix
                                + "@example.com",
                        "encoded-password",
                        UserRole.ORGANIZER
                );

        return userRepository
                .saveAndFlush(organizer);
    }
}