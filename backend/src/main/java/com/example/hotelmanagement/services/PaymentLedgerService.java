package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.InvoicePaymentStatus;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Synchronizes aggregate payment caches from the immutable payment/refund ledger. The ledger
 * remains the source of truth; Booking and Invoice only store transactional read models.
 */
@Service
@Transactional
public class PaymentLedgerService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final Set<PaymentStatus> RECEIVED_PAYMENT_STATUSES = Set.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED
    );

    private final BookingRepository bookingRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public PaymentLedgerService(
            BookingRepository bookingRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    public PaymentLedgerResult synchronizeSuccessfulPayment(Payment payment) {
        validateSucceededPayment(payment);
        Booking booking = getBookingForUpdate(payment.getBooking().getId());
        synchronizeBookingAmounts(booking);

        if (payment.getInvoice() != null) {
            Invoice invoice = getInvoiceForUpdate(payment.getInvoice().getId());
            synchronizeInvoiceAmounts(invoice);
        }

        boolean isDepositSatisfied = booking.getPaidAmount()
                .compareTo(booking.getRequiredDepositAmount()) >= 0;
        return new PaymentLedgerResult(
                booking.getPublicId(),
                booking.getStatus() == BookingStatus.PENDING && isDepositSatisfied
        );
    }

    /**
     * Prepared for BE-7.4. It must be invoked in the same transaction that changes a refund to
     * COMPLETED so the aggregate cache cannot diverge from the refund ledger.
     */
    public void synchronizeCompletedRefund(Refund refund) {
        if (refund == null || refund.getStatus() != RefundStatus.COMPLETED
                || refund.getPayment() == null || refund.getPayment().getPaymentCode() == null
                || refund.getBooking() == null || refund.getBooking().getId() == null) {
            throw new BusinessValidationException("Only completed refunds can synchronize the payment ledger");
        }
        String paymentCode = refund.getPayment().getPaymentCode();
        Payment payment = paymentRepository.findForUpdateByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentCode));
        synchronizePaymentRefundAmount(payment);

        Booking booking = getBookingForUpdate(refund.getBooking().getId());
        synchronizeBookingAmounts(booking);

        if (payment.getInvoice() != null) {
            Invoice invoice = getInvoiceForUpdate(payment.getInvoice().getId());
            synchronizeInvoiceAmounts(invoice);
        }
    }

    private void synchronizePaymentRefundAmount(Payment payment) {
        BigDecimal refundedAmount = money(refundRepository.sumAmountsByPaymentIdAndStatus(
                payment.getId(),
                RefundStatus.COMPLETED
        ));
        payment.setRefundedAmount(refundedAmount);
        if (refundedAmount.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else if (refundedAmount.signum() > 0) {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
    }

    private void synchronizeBookingAmounts(Booking booking) {
        BigDecimal paidAmount = money(paymentRepository.sumAmountsByBookingIdAndStatuses(
                booking.getId(),
                RECEIVED_PAYMENT_STATUSES
        ));
        BigDecimal refundedAmount = money(refundRepository.sumAmountsByBookingIdAndStatus(
                booking.getId(),
                RefundStatus.COMPLETED
        ));
        booking.setPaidAmount(paidAmount);
        booking.setRefundedAmount(refundedAmount);
        booking.setPaymentStatus(resolveBookingPaymentStatus(booking));
    }

    private void synchronizeInvoiceAmounts(Invoice invoice) {
        BigDecimal paidAmount = money(paymentRepository.sumAmountsByInvoiceIdAndStatuses(
                invoice.getId(),
                RECEIVED_PAYMENT_STATUSES
        ));
        BigDecimal refundedAmount = money(refundRepository.sumAmountsByInvoiceIdAndStatus(
                invoice.getId(),
                RefundStatus.COMPLETED
        ));
        invoice.setPaidAmount(paidAmount);
        invoice.setRefundedAmount(refundedAmount);
        invoice.setPaymentStatus(resolveInvoicePaymentStatus(invoice));
    }

    private BookingPaymentStatus resolveBookingPaymentStatus(Booking booking) {
        if (booking.getRefundedAmount().signum() > 0) {
            return booking.getRefundedAmount().compareTo(booking.getPaidAmount()) >= 0
                    ? BookingPaymentStatus.REFUNDED
                    : BookingPaymentStatus.PARTIALLY_REFUNDED;
        }
        if (booking.getPaidAmount().signum() <= 0) {
            return BookingPaymentStatus.UNPAID;
        }
        return booking.getPaidAmount().compareTo(booking.getTotalAmount()) >= 0
                ? BookingPaymentStatus.PAID
                : BookingPaymentStatus.PARTIALLY_PAID;
    }

    private InvoicePaymentStatus resolveInvoicePaymentStatus(Invoice invoice) {
        if (invoice.getRefundedAmount().signum() > 0) {
            return invoice.getRefundedAmount().compareTo(invoice.getPaidAmount()) >= 0
                    ? InvoicePaymentStatus.REFUNDED
                    : InvoicePaymentStatus.PARTIALLY_REFUNDED;
        }
        if (invoice.getPaidAmount().signum() <= 0) {
            return InvoicePaymentStatus.UNPAID;
        }
        return invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0
                ? InvoicePaymentStatus.PAID
                : InvoicePaymentStatus.PARTIALLY_PAID;
    }

    private Booking getBookingForUpdate(Long bookingId) {
        return bookingRepository.findForUpdateById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId.toString()));
    }

    private Invoice getInvoiceForUpdate(Long invoiceId) {
        return invoiceRepository.findForUpdateById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));
    }

    private void validateSucceededPayment(Payment payment) {
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED
                || payment.getVerifiedAt() == null || payment.getBooking() == null
                || payment.getBooking().getId() == null) {
            throw new BusinessValidationException("Only verified successful payments can synchronize the ledger");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO_MONEY : value).setScale(2, RoundingMode.HALF_UP);
    }
}
