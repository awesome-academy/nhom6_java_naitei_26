package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.PaymentProperties;
import com.example.hotelmanagement.dto.payment.PaymentCreateRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.PaymentGatewayException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Validated
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int PAYMENT_CODE_MAX_ATTEMPTS = 5;
    private static final int PAYMENT_CODE_RANDOM_BYTES = 10;
    private static final Set<PaymentStatus> ACTIVE_PAYMENT_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
    );
    private static final Set<PaymentStatus> RETRYABLE_PAYMENT_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.EXPIRED)
    );
    private static final String CUSTOMER_CANCELLED_CODE = "CUSTOMER_CANCELLED";
    private static final String PAYMENT_EXPIRED_CODE = "PAYMENT_LINK_EXPIRED";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties paymentProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayRegistry gatewayRegistry,
            PaymentProperties paymentProperties,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public PaymentResponse createPayment(
            String bookingPublicId,
            @Valid PaymentCreateRequest request,
            String idempotencyKey,
            Long userId
    ) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Booking booking = bookingRepository.findForUpdateByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));

        validateBookingOwner(booking, userId);
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existingPayment.isPresent()) {
            return returnIdempotentPayment(existingPayment.get(), booking, request.method());
        }

        validateBookingIsPayable(booking);
        Optional<Payment> activePayment = paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(
                        booking.getId(),
                        ACTIVE_PAYMENT_STATUSES
                );
        if (activePayment.isPresent()) {
            Payment payment = activePayment.get();
            if (expireIfNeeded(payment)) {
                paymentRepository.saveAndFlush(payment);
            } else {
                throw new BusinessValidationException("A payment is already pending for this booking");
            }
        }

        Payment payment = Payment.builder()
                .paymentCode(generatePaymentCode())
                .booking(booking)
                .method(request.method())
                .amount(calculateOutstandingBookingAmount(booking))
                .currency(booking.getCurrency())
                .status(PaymentStatus.PENDING)
                .provider(resolveProvider(request.method()))
                .idempotencyKey(normalizedIdempotencyKey)
                .expiresAt(calculatePaymentExpiresAt(booking))
                .createdBy(userId)
                .build();

        try {
            Payment savedPayment = paymentRepository.saveAndFlush(payment);
            return createCheckoutResponse(savedPayment);
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Database rejected payment creation bookingPublicId={} userId={}",
                    bookingPublicId,
                    userId,
                    exception
            );
            throw new DuplicateResourceException("Payment", "payment code", payment.getPaymentCode());
        }
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public PaymentStatusResponse getPayment(
            String bookingPublicId,
            String paymentCode,
            Long userId
    ) {
        Payment payment = getOwnedPaymentForUpdate(bookingPublicId, paymentCode, userId);
        if (expireIfNeeded(payment)) {
            paymentRepository.saveAndFlush(payment);
        }
        return mapStatusResponse(payment);
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public PaymentStatusResponse cancelPayment(
            String bookingPublicId,
            String paymentCode,
            Long userId
    ) {
        Payment payment = getOwnedPaymentForUpdate(bookingPublicId, paymentCode, userId);
        if (expireIfNeeded(payment)) {
            paymentRepository.saveAndFlush(payment);
            return mapStatusResponse(payment);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return mapStatusResponse(payment);
        }
        if (!ACTIVE_PAYMENT_STATUSES.contains(payment.getStatus())) {
            throw new BusinessValidationException(
                    "Only pending or processing payments can be cancelled"
            );
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setFailureCode(CUSTOMER_CANCELLED_CODE);
        payment.setFailureMessage("Payment was cancelled by the customer");
        return mapStatusResponse(paymentRepository.saveAndFlush(payment));
    }

    private PaymentResponse returnIdempotentPayment(
            Payment existingPayment,
            Booking booking,
            PaymentMethod requestedMethod
    ) {
        if (!existingPayment.getBooking().getId().equals(booking.getId())) {
            throw new BusinessValidationException("Idempotency key has already been used for another booking");
        }
        if (existingPayment.getMethod() != requestedMethod) {
            throw new BusinessValidationException("Idempotency key has already been used with another payment method");
        }
        boolean paymentChanged = expireIfNeeded(existingPayment);
        if (existingPayment.getProvider() == null) {
            existingPayment.setProvider(resolveProvider(requestedMethod));
            paymentChanged = true;
        }
        if (paymentChanged) {
            paymentRepository.saveAndFlush(existingPayment);
        }
        return createCheckoutResponse(existingPayment);
    }

    private Payment getOwnedPaymentForUpdate(
            String bookingPublicId,
            String paymentCode,
            Long userId
    ) {
        Payment payment = paymentRepository.findForUpdateByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentCode));
        if (!payment.getBooking().getPublicId().equals(bookingPublicId)) {
            throw new ResourceNotFoundException("Payment", paymentCode);
        }
        validateBookingOwner(payment.getBooking(), userId);
        return payment;
    }

    private void validateBookingOwner(Booking booking, Long userId) {
        if (booking.getCustomerProfile() == null
                || booking.getCustomerProfile().getUser() == null
                || !Objects.equals(userId, booking.getCustomerProfile().getUser().getId())) {
            throw new AccessDeniedException("You cannot access payments for this booking");
        }
    }

    private void validateBookingIsPayable(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessValidationException("Only pending bookings can be paid");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(now)) {
            throw new BusinessValidationException("The booking payment hold has expired");
        }
        if (calculateOutstandingBookingAmount(booking).signum() <= 0) {
            throw new BusinessValidationException("This booking has no outstanding balance");
        }
    }

    private BigDecimal calculateOutstandingBookingAmount(Booking booking) {
        BigDecimal paidAmount = booking.getPaidAmount() == null ? BigDecimal.ZERO : booking.getPaidAmount();
        return booking.getTotalAmount().subtract(paidAmount);
    }

    private OffsetDateTime calculatePaymentExpiresAt(Booking booking) {
        if (paymentProperties.getCheckoutTtl() == null
                || paymentProperties.getCheckoutTtl().isZero()
                || paymentProperties.getCheckoutTtl().isNegative()) {
            throw new BusinessValidationException("Payment checkout TTL must be greater than zero");
        }
        OffsetDateTime gatewayExpiry = OffsetDateTime.now(clock).plus(paymentProperties.getCheckoutTtl());
        return gatewayExpiry.isBefore(booking.getHoldExpiresAt())
                ? gatewayExpiry
                : booking.getHoldExpiresAt();
    }

    private boolean expireIfNeeded(Payment payment) {
        if (!ACTIVE_PAYMENT_STATUSES.contains(payment.getStatus())
                || payment.getExpiresAt() == null
                || payment.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            return false;
        }
        payment.setStatus(PaymentStatus.EXPIRED);
        payment.setFailureCode(PAYMENT_EXPIRED_CODE);
        payment.setFailureMessage("Payment link has expired");
        return true;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessValidationException("Idempotency-Key header is required");
        }
        String normalizedKey = idempotencyKey.strip();
        if (normalizedKey.length() > 80) {
            throw new BusinessValidationException("Idempotency-Key header must not exceed 80 characters");
        }
        return normalizedKey;
    }

    private String generatePaymentCode() {
        int year = LocalDate.now(clock).getYear();
        for (int attempt = 0; attempt < PAYMENT_CODE_MAX_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[PAYMENT_CODE_RANDOM_BYTES];
            secureRandom.nextBytes(randomBytes);
            String candidate = "PAY-" + year + "-" + HexFormat.of().formatHex(randomBytes).toUpperCase();
            if (!paymentRepository.existsByPaymentCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessValidationException("Unable to generate a unique payment code, please retry");
    }

    private String resolveProvider(PaymentMethod method) {
        if (method == PaymentMethod.CASH) {
            return "MANUAL";
        }
        String configuredProvider = method == PaymentMethod.E_WALLET
                ? paymentProperties.getEWalletProvider()
                : paymentProperties.getDefaultProvider();
        return gatewayRegistry.getGateway(configuredProvider, method).getProviderCode();
    }

    private PaymentResponse createCheckoutResponse(Payment payment) {
        PaymentGatewayCheckout checkout = payment.getMethod() == PaymentMethod.CASH
                || !ACTIVE_PAYMENT_STATUSES.contains(payment.getStatus())
                ? null
                : gatewayRegistry.getGateway(payment.getProvider(), payment.getMethod()).createCheckout(payment);
        return mapResponse(payment, checkout);
    }

    private PaymentResponse mapResponse(Payment payment, PaymentGatewayCheckout checkout) {
        return new PaymentResponse(
                payment.getPaymentCode(),
                payment.getBooking().getPublicId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                checkout == null ? null : checkout.paymentUrl(),
                checkout == null ? null : checkout.deeplink(),
                checkout == null ? null : checkout.qrCodeValue(),
                checkout == null ? List.of() : checkout.checkoutFields(),
                payment.getExpiresAt(),
                payment.getCreatedAt()
        );
    }

    private PaymentStatusResponse mapStatusResponse(Payment payment) {
        return new PaymentStatusResponse(
                payment.getPaymentCode(),
                payment.getBooking().getPublicId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                payment.getExpiresAt(),
                isRetryable(payment),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private boolean isRetryable(Payment payment) {
        Booking booking = payment.getBooking();
        if (!RETRYABLE_PAYMENT_STATUSES.contains(payment.getStatus())
                || booking.getStatus() != BookingStatus.PENDING
                || booking.getHoldExpiresAt() == null
                || !booking.getHoldExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            return false;
        }
        BigDecimal paidAmount = booking.getPaidAmount() == null ? BigDecimal.ZERO : booking.getPaidAmount();
        return booking.getTotalAmount().subtract(paidAmount).signum() > 0;
    }
}
