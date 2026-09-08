package br.com.eventflow.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByStatus(EventStatus status);

    List<Event> findAllByOrganizer_UserId(Long organizerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select e
        from Event e
        where e.eventId = :eventId
        """)
    Optional<Event> findByIdForUpdate(
            @Param("eventId") Long eventId
    );
}
