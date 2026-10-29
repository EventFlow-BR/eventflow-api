package br.com.eventflow.payment;

import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.Registration;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "registration_id",
            nullable = false,
            unique = true
    )
    private Registration registration;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
    }

    public Payment(
            Registration registration,
            BigDecimal amount
    ) {
        this.registration = registration;
        this.amount = amount;
        this.status = PaymentStatus.APPROVED;
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

    public Long getPaymentId() {
        return paymentId;
    }

    public Registration getRegistration() {
        return registration;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void refund() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only approved payments can be refunded"
            );
        }

        this.status = PaymentStatus.REFUNDED;
    }
}
