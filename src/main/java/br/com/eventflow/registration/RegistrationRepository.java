package br.com.eventflow.registration;

import br.com.eventflow.registration.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository
        extends JpaRepository<Registration, Long> {

    @Query("""
        select r
        from Registration r
        where r.participant.userId = :participantId
          and r.event.eventId = :eventId
          and (
                r.status = :confirmedStatus
                or (
                    r.status = :pendingStatus
                    and r.reservationExpiresAt > :now
                )
              )
        """)
    Optional<Registration> findActiveRegistration(
            @Param("participantId") Long participantId,
            @Param("eventId") Long eventId,
            @Param("confirmedStatus") RegistrationStatus confirmedStatus,
            @Param("pendingStatus") RegistrationStatus pendingStatus,
            @Param("now") OffsetDateTime now
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from Registration r
        where r.registrationId = :registrationId
        """)
    Optional<Registration> findByIdForUpdate(
            @Param("registrationId") Long registrationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select r
    from Registration r
    where r.status = :status
      and r.reservationExpiresAt <= :now
    """)
    List<Registration> findExpiredPendingRegistrations(
            @Param("status") RegistrationStatus status,
            @Param("now") OffsetDateTime now
    );
}