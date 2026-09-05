package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.BadRequestException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                userRepository
        );
    }

    @Test
    void shouldCreateEventForOrganizer() {
        User organizer = organizer(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(organizer));

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response =
                eventService.createEvent(
                        10L,
                        validRequest()
                );

        ArgumentCaptor<Event> eventCaptor =
                ArgumentCaptor.forClass(Event.class);

        verify(eventRepository)
                .save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();

        assertEquals(10L, savedEvent.getOrganizer().getUserId());
        assertEquals("Java Conference", savedEvent.getName());
        assertEquals(EventStatus.DRAFT, savedEvent.getStatus());

        assertEquals(10L, response.organizerId());
        assertEquals("Java Conference", response.name());
        assertEquals(EventStatus.DRAFT, response.status());
    }

    @Test
    void shouldRejectParticipantCreatingEvent() {
        User participant = new User(
                "Participant",
                "participant@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(participant, 20L);

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> eventService.createEvent(
                        20L,
                        validRequest()
                )
        );

        assertEquals(
                "Only organizers can create events",
                exception.getMessage()
        );

        verify(eventRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectEventWhenStartDateEqualsEndDate() {
        User organizer = organizer(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(organizer));

        OffsetDateTime date =
                OffsetDateTime.parse(
                        "2026-10-10T10:00:00-03:00"
                );

        CreateEventRequest request =
                new CreateEventRequest(
                        "Java Conference",
                        "Backend conference",
                        "Petrópolis",
                        date,
                        date,
                        100,
                        new BigDecimal("50.00")
                );

        assertThrows(
                BadRequestException.class,
                () -> eventService.createEvent(
                        10L,
                        request
                )
        );

        verify(eventRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectEventWhenStartDateIsAfterEndDate() {
        User organizer = organizer(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(organizer));

        CreateEventRequest request =
                new CreateEventRequest(
                        "Java Conference",
                        "Backend conference",
                        "Petrópolis",
                        OffsetDateTime.parse(
                                "2026-10-10T18:00:00-03:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-10-10T10:00:00-03:00"
                        ),
                        100,
                        new BigDecimal("50.00")
                );

        assertThrows(
                BadRequestException.class,
                () -> eventService.createEvent(
                        10L,
                        request
                )
        );

        verify(eventRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectCreationWhenAuthenticatedUserNoLongerExists() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                UnauthorizedException.class,
                () -> eventService.createEvent(
                        10L,
                        validRequest()
                )
        );

        verify(eventRepository, never())
                .save(any());
    }

    @Test
    void shouldListOnlyPublishedEventsForParticipant() {
        User organizer = organizer(10L);

        Event publishedEvent = new Event(
                organizer,
                "Published Event",
                "Description",
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

        setEventStatusForTest(
                publishedEvent,
                EventStatus.PUBLISHED
        );

        when(eventRepository.findAllByStatus(
                EventStatus.PUBLISHED
        )).thenReturn(List.of(publishedEvent));

        List<EventResponse> response =
                eventService.listVisibleEvents(
                        20L,
                        UserRole.PARTICIPANT
                );

        assertEquals(1, response.size());
        assertEquals(
                "Published Event",
                response.getFirst().name()
        );

        verify(eventRepository)
                .findAllByStatus(EventStatus.PUBLISHED);

        verify(eventRepository, never())
                .findAllByOrganizer_UserId(anyLong());
    }

    @Test
    void shouldListOwnEventsForOrganizer() {
        User organizer = organizer(10L);

        Event ownEvent = new Event(
                organizer,
                "Organizer Draft",
                "Description",
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

        when(eventRepository.findAllByOrganizer_UserId(10L))
                .thenReturn(List.of(ownEvent));

        List<EventResponse> response =
                eventService.listVisibleEvents(
                        10L,
                        UserRole.ORGANIZER
                );

        assertEquals(1, response.size());
        assertEquals(
                "Organizer Draft",
                response.getFirst().name()
        );

        verify(eventRepository)
                .findAllByOrganizer_UserId(10L);

        verify(eventRepository, never())
                .findAllByStatus(any());
    }

    @Test
    void shouldAllowParticipantToViewPublishedEvent() {
        User organizer = organizer(10L);

        Event event = new Event(
                organizer,
                "Published Event",
                "Description",
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

        setEventStatusForTest(
                event,
                EventStatus.PUBLISHED
        );

        when(eventRepository.findById(100L))
                .thenReturn(Optional.of(event));

        EventResponse response =
                eventService.getVisibleEvent(
                        100L,
                        20L,
                        UserRole.PARTICIPANT
                );

        assertEquals(
                "Published Event",
                response.name()
        );
    }

    @Test
    void shouldHidePrivateEventFromAnotherOrganizer() {
        User owner = organizer(10L);

        Event event = new Event(
                owner,
                "Private Draft",
                "Description",
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

        when(eventRepository.findById(100L))
                .thenReturn(Optional.of(event));

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> eventService.getVisibleEvent(
                                100L,
                                20L,
                                UserRole.ORGANIZER
                        )
                );

        assertEquals(
                "Event not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowOrganizerToViewOwnDraftEvent() {
        User organizer = organizer(10L);

        Event event = new Event(
                organizer,
                "Private Draft",
                "Description",
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

        when(eventRepository.findById(100L))
                .thenReturn(Optional.of(event));

        EventResponse response =
                eventService.getVisibleEvent(
                        100L,
                        10L,
                        UserRole.ORGANIZER
                );

        assertEquals(
                "Private Draft",
                response.name()
        );
    }

    @Test
    void shouldHideDraftEventFromParticipant() {
        User organizer = organizer(10L);

        Event event = new Event(
                organizer,
                "Private Draft",
                "Description",
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

        when(eventRepository.findById(100L))
                .thenReturn(Optional.of(event));

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> eventService.getVisibleEvent(
                                100L,
                                20L,
                                UserRole.PARTICIPANT
                        )
                );

        assertEquals(
                "Event not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> eventService.getVisibleEvent(
                                999L,
                                20L,
                                UserRole.PARTICIPANT
                        )
                );

        assertEquals(
                "Event not found",
                exception.getMessage()
        );
    }

    private CreateEventRequest validRequest() {
        return new CreateEventRequest(
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
                new BigDecimal("50.00")
        );
    }

    private User organizer(Long userId) {
        User organizer = new User(
                "Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserIdForTest(organizer, userId);

        return organizer;
    }

    private void setUserIdForTest(
            User user,
            Long userId
    ) {
        try {
            var field =
                    User.class.getDeclaredField("userId");

            field.setAccessible(true);
            field.set(user, userId);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setEventStatusForTest(
            Event event,
            EventStatus status
    ) {
        try {
            var field =
                    Event.class.getDeclaredField("status");

            field.setAccessible(true);
            field.set(event, status);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}