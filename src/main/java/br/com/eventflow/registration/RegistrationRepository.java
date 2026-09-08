package br.com.eventflow.registration;

import br.com.eventflow.registration.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository
        extends JpaRepository<Registration, Long> {

    Optional<Registration>
    findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
            Long participantId,
            Long eventId,
            List<RegistrationStatus> statuses
    );

    @Query("""
            select count(r)
            from Registration r
            where r.event.eventId = :eventId
              and (
                    r.status = :confirmedStatus
                    or (
                        r.status = :pendingStatus
                        and r.reservationExpiresAt > :now
                    )
                  )
            """)
    long countOccupyingCapacity(
            @Param("eventId") Long eventId,
            @Param("confirmedStatus")
            RegistrationStatus confirmedStatus,
            @Param("pendingStatus")
            RegistrationStatus pendingStatus,
            @Param("now") OffsetDateTime now
    );
}