package br.com.eventflow.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByRegistration_RegistrationId(
            Long registrationId
    );

    Optional<Payment> findByRegistration_RegistrationId(
            Long registrationId
    );
}
