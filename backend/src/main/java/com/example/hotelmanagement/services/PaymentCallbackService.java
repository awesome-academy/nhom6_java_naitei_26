package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.PaymentEvent;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.InvalidPaymentCallbackException;
import com.example.hotelmanagement.repositories.PaymentEventRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
public class PaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);
    private static final int MAX_CALLBACK_LENGTH = 1_048_576;
    private static final int MAX_LOG_VALUE_LENGTH = 120;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 2_000;
    private static final int OK_STATUS = 200;
    // A verified gateway settlement remains the ledger truth even if the local checkout was
    // cancelled, failed, or expired before its callback arrived.
    private static final Set<PaymentStatus> SUCCESS_SETTLEABLE_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(
                    PaymentStatus.PENDING,
                    PaymentStatus.PROCESSING,
                    PaymentStatus.FAILED,
                    PaymentStatus.CANCELLED,
                    PaymentStatus.EXPIRED
            )
    );

    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final BookingStateMachineService bookingStateMachineService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentCallbackService(
            PaymentGatewayRegistry gatewayRegistry,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            PaymentLedgerService paymentLedgerService,
            BookingStateMachineService bookingStateMachineService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.gatewayRegistry = gatewayRegistry;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.paymentLedgerService = paymentLedgerService;
        this.bookingStateMachineService = bookingStateMachineService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void handleCallback(
            String provider,
            PaymentGatewayCallbackRequest callbackRequest,
            String receivedIp
    ) {
        String rawPayload = callbackRequest.rawPayload();
        validateRawPayload(rawPayload);

        PaymentGatewayService gateway = gatewayRegistry.getGateway(provider);
        PaymentGatewayCallback callback;
        try {
            callback = gateway.verifyCallback(callbackRequest);
        } catch (InvalidPaymentCallbackException exception) {
            log.warn(
                    "Rejected malformed payment callback provider={} errorType={}",
                    gateway.getProviderCode(),
                    exception.getClass().getSimpleName()
            );
            saveMalformedEvent(gateway.getProviderCode(), rawPayload, receivedIp);
            return;
        }

        processCallback(callback, rawPayload, receivedIp, "IPN_RECEIVED");
    }

    @Transactional
    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public void handleTrustedCallback(
            PaymentGatewayCallback callback,
            String rawPayload,
            String receivedIp
    ) {
        validateRawPayload(rawPayload);
        if (callback == null
                || !callback.signatureValid()
                || !MockWalletPaymentGatewayService.PROVIDER_CODE.equals(callback.provider())) {
            throw new BusinessValidationException("Trusted mock wallet callback must be verified");
        }
        processCallback(callback, rawPayload, receivedIp, "SIMULATOR_RESULT");
    }

    private void processCallback(
            PaymentGatewayCallback callback,
            String rawPayload,
            String receivedIp,
            String eventType
    ) {

        if (callback.signatureValid()
                && callback.providerEventId() != null
                && paymentEventRepository.existsByProviderAndProviderEventId(
                        callback.provider(),
                        callback.providerEventId()
                )) {
            return;
        }

        Optional<Payment> paymentOptional = callback.paymentCode() == null
                ? Optional.empty()
                : paymentRepository.findForUpdateByPaymentCode(callback.paymentCode());
        Payment payment = paymentOptional.orElse(null);
        PaymentEvent event = buildEvent(callback, payment, rawPayload, receivedIp, eventType);
        boolean hasNewVerifiedSuccess = false;

        if (!callback.signatureValid()) {
            log.warn(
                    "Rejected payment callback with invalid verification provider={} paymentCode={}",
                    callback.provider(),
                    sanitizeForLog(callback.paymentCode())
            );
        } else if (payment == null) {
            log.warn(
                    "Payment callback did not match a payment provider={} paymentCode={}",
                    callback.provider(),
                    sanitizeForLog(callback.paymentCode())
            );
        } else if (!matchesPayment(payment, callback)) {
            log.warn(
                    "Payment callback data mismatch provider={} paymentCode={}",
                    callback.provider(),
                    sanitizeForLog(callback.paymentCode())
            );
        } else {
            hasNewVerifiedSuccess = applyVerifiedResult(payment, callback);
        }

        paymentEventRepository.save(event);
        if (hasNewVerifiedSuccess) {
            PaymentLedgerResult ledgerResult = paymentLedgerService.synchronizeSuccessfulPayment(payment);
            if (ledgerResult.shouldConfirmBooking()) {
                bookingStateMachineService.confirm(ledgerResult.bookingPublicId());
            }
        }
    }

    private boolean applyVerifiedResult(Payment payment, PaymentGatewayCallback callback) {
        if (callback.isSuccessful()) {
            if (!isProviderTransactionAvailable(payment, callback.providerTransactionId())) {
                log.warn(
                        "Provider transaction is already linked to another payment provider={} paymentCode={}",
                        callback.provider(),
                        sanitizeForLog(callback.paymentCode())
                );
                return false;
            }
            if (SUCCESS_SETTLEABLE_STATUSES.contains(payment.getStatus())) {
                OffsetDateTime verifiedAt = OffsetDateTime.now(clock);
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setProviderTxnId(callback.providerTransactionId());
                payment.setPaidAt(verifiedAt);
                payment.setVerifiedAt(verifiedAt);
                payment.setFailureCode(null);
                payment.setFailureMessage(null);
                return true;
            }
            return false;
        }

        if (callback.isAuthorized()) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.PROCESSING);
            }
            return false;
        }

        if (payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode(String.valueOf(callback.resultCode()));
            payment.setFailureMessage(truncate(callback.message(), MAX_FAILURE_MESSAGE_LENGTH));
        }
        return false;
    }

    private boolean matchesPayment(Payment payment, PaymentGatewayCallback callback) {
        return payment.getProvider() != null
                && payment.getProvider().equalsIgnoreCase(callback.provider())
                && callback.amount() != null
                && callback.amount().compareTo(payment.getAmount()) == 0
                && "VND".equalsIgnoreCase(payment.getCurrency());
    }

    private boolean isProviderTransactionAvailable(Payment payment, String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            return false;
        }
        return paymentRepository.findByProviderTxnId(providerTransactionId)
                .map(existingPayment -> existingPayment.getId().equals(payment.getId()))
                .orElse(true);
    }

    private PaymentEvent buildEvent(
            PaymentGatewayCallback callback,
            Payment payment,
            String rawPayload,
            String receivedIp,
            String eventType
    ) {
        return PaymentEvent.builder()
                .payment(payment)
                .eventType(eventType)
                .provider(callback.provider())
                .providerEventId(callback.providerEventId())
                .signatureValid(callback.signatureValid())
                .httpStatus(OK_STATUS)
                .rawPayload(rawPayload)
                .receivedIp(receivedIp)
                .processedAt(OffsetDateTime.now(clock))
                .build();
    }

    private void validateRawPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new BusinessValidationException("Payment callback payload is required");
        }
        if (rawPayload.length() > MAX_CALLBACK_LENGTH) {
            throw new BusinessValidationException("Payment callback payload is too large");
        }
    }

    private void saveMalformedEvent(String provider, String rawPayload, String receivedIp) {
        String safePayload = objectMapper.createObjectNode()
                .put("unparseablePayload", rawPayload)
                .toString();
        PaymentEvent event = PaymentEvent.builder()
                .eventType("IPN_RECEIVED")
                .provider(provider)
                .signatureValid(false)
                .httpStatus(OK_STATUS)
                .rawPayload(safePayload)
                .receivedIp(receivedIp)
                .processedAt(OffsetDateTime.now(clock))
                .build();
        paymentEventRepository.save(event);
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
