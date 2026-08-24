package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaymentCode(String paymentCode);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(
            Long bookingId,
            Collection<PaymentStatus> statuses
    );
}
