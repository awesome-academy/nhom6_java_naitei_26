package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    boolean existsByBooking_IdAndStatusIn(Long bookingId, Collection<RefundStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT refund FROM Refund refund WHERE refund.id = :id")
    Optional<Refund> findForUpdateById(@Param("id") Long id);

    @Query("""
            SELECT COALESCE(SUM(refund.amount), 0)
            FROM Refund refund
            WHERE refund.payment.id = :paymentId
              AND refund.status = :status
            """)
    BigDecimal sumAmountsByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );

    @Query("""
            SELECT COALESCE(SUM(refund.amount), 0)
            FROM Refund refund
            WHERE refund.booking.id = :bookingId
              AND refund.status = :status
            """)
    BigDecimal sumAmountsByBookingIdAndStatus(
            @Param("bookingId") Long bookingId,
            @Param("status") RefundStatus status
    );

    @Query("""
            SELECT COALESCE(SUM(refund.amount), 0)
            FROM Refund refund
            WHERE refund.payment.invoice.id = :invoiceId
              AND refund.status = :status
            """)
    BigDecimal sumAmountsByInvoiceIdAndStatus(
            @Param("invoiceId") Long invoiceId,
            @Param("status") RefundStatus status
    );
}
