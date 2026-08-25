package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

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
