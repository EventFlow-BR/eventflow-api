package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.user.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long registrationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private User participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "reservation_expires_at")
    private OffsetDateTime reservationExpiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Registration() {
    }

    public Registration(
            Event event,
            User participant,
            OffsetDateTime reservationExpiresAt
    ) {
        this.event = event;
        this.participant = participant;
        this.reservationExpiresAt = reservationExpiresAt;
        this.status = RegistrationStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);
    }


    public Long getRegistrationId() {
        return registrationId;
    }

    public Event getEvent() {
        return event;
    }

    public User getParticipant() {
        return participant;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void confirm() {
        this.status = RegistrationStatus.CONFIRMED;
    }

    public void expire() {
        if (this.status != RegistrationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending registrations can expire"
            );
        }

        this.status = RegistrationStatus.EXPIRED;
    }

    public void cancel() {
        if (this.status != RegistrationStatus.PENDING
                && this.status != RegistrationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only pending or confirmed registrations can be cancelled"
            );
        }

        this.status = RegistrationStatus.CANCELLED;
    }
}