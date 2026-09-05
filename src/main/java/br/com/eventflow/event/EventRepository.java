package br.com.eventflow.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByStatus(EventStatus status);

    List<Event> findAllByOrganizer_UserId(Long organizerId);
}
