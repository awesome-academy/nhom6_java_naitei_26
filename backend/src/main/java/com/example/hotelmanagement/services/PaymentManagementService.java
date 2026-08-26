package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.PaymentCashVerificationRequest;
import com.example.hotelmanagement.dto.payment.PaymentDetailResponse;
import com.example.hotelmanagement.dto.payment.PaymentListItemResponse;
import com.example.hotelmanagement.dto.payment.PaymentListResponse;
import com.example.hotelmanagement.dto.payment.PaymentRefundRequest;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.PaymentSpecifications;
import com.example.hotelmanagement.repositories.RefundRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class PaymentManagementService {

    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<PaymentStatus> RECEIVED_STATUSES = Set.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED
    );
    private static final Set<RefundStatus> COMMITTED_REFUND_STATUSES = EnumSet.of(
            RefundStatus.PENDING,
            RefundStatus.PROCESSING,
            RefundStatus.COMPLETED
    );

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final BookingStateMachineService bookingStateMachineService;
    private final EmailService emailService;
    private final Clock clock;

    public PaymentManagementService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            PaymentLedgerService paymentLedgerService,
            BookingStateMachineService bookingStateMachineService,
            EmailService emailService,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentLedgerService = paymentLedgerService;
        this.bookingStateMachineService = bookingStateMachineService;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    public PaymentListResponse listPayments(
            String booking,
            Collection<PaymentStatus> statuses,
            PaymentMethod method,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    ) {
        validateDateRange(from, to);
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0 ? 20 : Math.min(size, 100);
        var pageable = org.springframework.data.domain.PageRequest.of(normalizedPage, normalizedSize);
        var result = paymentRepository.findAll(
                PaymentSpecifications.withFilters(booking, statuses, method, from, to, HOTEL_ZONE),
                pageable
        );
        List<PaymentListItemResponse> items = result.getContent().stream()
                .map(this::mapListItem)
                .toList();
        return new PaymentListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    public PaymentDetailResponse getPayment(String paymentCode) {
        return mapDetail(findPayment(paymentCode));
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    public PaymentDetailResponse verifyCashPayment(
            String paymentCode,
            PaymentCashVerificationRequest request,
            Long staffUserId
    ) {
        Payment payment = findPaymentForUpdate(paymentCode);
        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new BusinessValidationException("Only CASH payments can be verified manually");
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return mapDetail(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new BusinessValidationException("Only pending CASH payments can be verified");
        }
        if (payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessValidationException("The CASH payment has expired");
        }

        String providerTxnId = normalizeProviderTxnId(request == null ? null : request.providerTxnId());
        if (providerTxnId == null) {
            providerTxnId = "CASH-" + payment.getPaymentCode();
        }
        ensureProviderTransactionAvailable(payment, providerTxnId);

        OffsetDateTime verifiedAt = OffsetDateTime.now(clock);
        payment.setProvider("MANUAL");
        payment.setProviderTxnId(providerTxnId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(verifiedAt);
        payment.setVerifiedAt(verifiedAt);
        payment.setFailureCode(null);
        payment.setFailureMessage(null);

        Payment saved = paymentRepository.saveAndFlush(payment);
        PaymentLedgerResult ledgerResult = paymentLedgerService.synchronizeSuccessfulPayment(saved);
        emailService.sendPaymentSuccessEmail(saved);
        if (ledgerResult.shouldConfirmBooking()) {
            bookingStateMachineService.confirm(ledgerResult.bookingPublicId());
        }
        return mapDetail(saved);
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    public PaymentDetailResponse requestRefund(
            String paymentCode,
            PaymentRefundRequest request,
            Long actorUserId
    ) {
        if (request == null || request.amount() == null || request.reason() == null) {
            throw new BusinessValidationException("Refund amount and reason are required");
        }
        Payment payment = findPaymentForUpdate(paymentCode);
        if (!RECEIVED_STATUSES.contains(payment.getStatus())) {
            throw new BusinessValidationException("Only received payments can be refunded");
        }

        BigDecimal amount = money(request.amount());
        if (amount.signum() <= 0) {
            throw new BusinessValidationException("Refund amount must be greater than zero");
        }
        BigDecimal committedRefunds = money(refundRepository.sumAmountsByPaymentIdAndStatuses(
                payment.getId(),
                COMMITTED_REFUND_STATUSES
        ));
        BigDecimal available = money(payment.getAmount().subtract(committedRefunds));
        if (amount.compareTo(available) > 0) {
            throw new BusinessValidationException(
                    "Refund amount cannot exceed the remaining refundable amount of " + available
            );
        }

        Refund refund = Refund.builder()
                .payment(payment)
                .booking(payment.getBooking())
                .amount(amount)
                .reason(request.reason())
                .status(RefundStatus.PENDING)
                .requestedBy(actorUserId)
                .build();
        refundRepository.saveAndFlush(refund);
        return mapDetail(payment);
    }

    private Payment findPayment(String paymentCode) {
        String normalizedCode = normalizePaymentCode(paymentCode);
        return paymentRepository.findByPaymentCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", normalizedCode));
    }

    private Payment findPaymentForUpdate(String paymentCode) {
        String normalizedCode = normalizePaymentCode(paymentCode);
        return paymentRepository.findForManagementByPaymentCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", normalizedCode));
    }

    private void ensureProviderTransactionAvailable(Payment payment, String providerTxnId) {
        paymentRepository.findByProviderTxnId(providerTxnId)
                .filter(existing -> !existing.getId().equals(payment.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Payment", "provider transaction id", providerTxnId);
                });
    }

    private PaymentListItemResponse mapListItem(Payment payment) {
        var booking = payment.getBooking();
        return new PaymentListItemResponse(
                payment.getPaymentCode(),
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getContactName(),
                payment.getMethod(),
                money(payment.getAmount()),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderTxnId(),
                money(payment.getRefundedAmount()),
                payment.getPaidAt(),
                payment.getVerifiedAt(),
                payment.getCreatedAt()
        );
    }

    private PaymentDetailResponse mapDetail(Payment payment) {
        var booking = payment.getBooking();
        List<PaymentDetailResponse.RefundSummary> refunds = payment.getRefunds().stream()
                .sorted(Comparator.comparing(Refund::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(refund -> new PaymentDetailResponse.RefundSummary(
                        refund.getId(),
                        money(refund.getAmount()),
                        refund.getReason(),
                        refund.getStatus(),
                        refund.getRequestedBy(),
                        refund.getApprovedBy(),
                        refund.getProviderRefundId(),
                        refund.getCreatedAt(),
                        refund.getProcessedAt()
                ))
                .toList();
        return new PaymentDetailResponse(
                payment.getPaymentCode(),
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getContactName(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                money(booking.getTotalAmount()),
                money(booking.getPaidAmount()),
                money(booking.getRefundedAmount()),
                payment.getMethod(),
                money(payment.getAmount()),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderTxnId(),
                payment.getProviderBankCode(),
                money(payment.getRefundedAmount()),
                payment.getPaidAt(),
                payment.getVerifiedAt(),
                payment.getExpiresAt(),
                payment.getCreatedBy(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                refunds
        );
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessValidationException("Payment date range is invalid");
        }
    }

    private String normalizePaymentCode(String paymentCode) {
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new BusinessValidationException("Payment code is required");
        }
        return paymentCode.strip();
    }

    private String normalizeProviderTxnId(String providerTxnId) {
        if (providerTxnId == null || providerTxnId.isBlank()) {
            return null;
        }
        return providerTxnId.strip();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
