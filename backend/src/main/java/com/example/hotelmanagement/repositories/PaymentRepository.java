package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaymentCode(String paymentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.paymentCode = :paymentCode")
    Optional<Payment> findForUpdateByPaymentCode(@Param("paymentCode") String paymentCode);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderTxnId(String providerTxnId);

    Optional<Payment> findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(
            Long bookingId,
            Collection<PaymentStatus> statuses
    );

    @Query("""
            SELECT COALESCE(SUM(payment.amount), 0)
            FROM Payment payment
            WHERE payment.booking.id = :bookingId
              AND payment.status IN :statuses
            """)
    BigDecimal sumAmountsByBookingIdAndStatuses(
            @Param("bookingId") Long bookingId,
            @Param("statuses") Collection<PaymentStatus> statuses
    );

    @Query("""
            SELECT COALESCE(SUM(payment.amount), 0)
            FROM Payment payment
            WHERE payment.invoice.id = :invoiceId
              AND payment.status IN :statuses
            """)
    BigDecimal sumAmountsByInvoiceIdAndStatuses(
            @Param("invoiceId") Long invoiceId,
            @Param("statuses") Collection<PaymentStatus> statuses
    );
}
