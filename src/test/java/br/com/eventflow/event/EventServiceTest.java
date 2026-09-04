package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.BadRequestException;
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
        User organizer = new User(
                "Event Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserIdForTest(organizer, 10L);

        CreateEventRequest request = validRequest();

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(organizer));

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response =
                eventService.createEvent(10L, request);

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
    void shouldRejectEventWhenStartDateIsNotBeforeEndDate() {
        User organizer = new User(
                "Event Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserIdForTest(organizer, 10L);

        OffsetDateTime date =
                OffsetDateTime.now().plusDays(10);

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

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(organizer));

        assertThrows(
                BadRequestException.class,
                () -> eventService.createEvent(10L, request)
        );

        verify(eventRepository, never())
                .save(any());
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

        assertThrows(
                IllegalStateException.class,
                () -> eventService.createEvent(
                        20L,
                        validRequest()
                )
        );

        verify(eventRepository, never())
                .save(any());
    }

    private CreateEventRequest validRequest() {
        return new CreateEventRequest(
                "Java Conference",
                "Backend conference",
                "Petrópolis",
                OffsetDateTime.now().plusDays(10),
                OffsetDateTime.now().plusDays(10).plusHours(8),
                100,
                new BigDecimal("50.00")
        );
    }

    private void setUserIdForTest(User user, Long userId) {
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
