package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.PaymentCreateRequest;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Service
@Validated
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int PAYMENT_CODE_MAX_ATTEMPTS = 5;
    private static final int PAYMENT_CODE_RANDOM_BYTES = 10;
    private static final Set<PaymentStatus> ACTIVE_PAYMENT_STATUSES =
            Set.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
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
        paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(
                        booking.getId(),
                        ACTIVE_PAYMENT_STATUSES
                )
                .ifPresent(payment -> {
                    throw new BusinessValidationException(
                            "A payment is already pending for this booking"
                    );
                });

        Payment payment = Payment.builder()
                .paymentCode(generatePaymentCode())
                .booking(booking)
                .method(request.method())
                .amount(calculateOutstandingAmount(booking))
                .currency(booking.getCurrency())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(normalizedIdempotencyKey)
                .expiresAt(booking.getHoldExpiresAt())
                .createdBy(userId)
                .build();

        try {
            Payment savedPayment = paymentRepository.saveAndFlush(payment);
            return mapResponse(savedPayment);
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
        return mapResponse(existingPayment);
    }

    private void validateBookingOwner(Booking booking, Long userId) {
        if (booking.getCustomerProfile() == null
                || booking.getCustomerProfile().getUser() == null
                || !userId.equals(booking.getCustomerProfile().getUser().getId())) {
            throw new BusinessValidationException("You cannot create a payment for this booking");
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
        if (calculateOutstandingAmount(booking).signum() <= 0) {
            throw new BusinessValidationException("This booking has no outstanding balance");
        }
    }

    private BigDecimal calculateOutstandingAmount(Booking booking) {
        BigDecimal paidAmount = booking.getPaidAmount() == null ? BigDecimal.ZERO : booking.getPaidAmount();
        return booking.getTotalAmount().subtract(paidAmount);
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

    private PaymentResponse mapResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentCode(),
                payment.getBooking().getPublicId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getExpiresAt(),
                payment.getCreatedAt()
        );
    }
}
