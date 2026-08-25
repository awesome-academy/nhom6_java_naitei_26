package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.config.EmailProperties;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.EmailMessage;
import com.example.hotelmanagement.entity.EmailTemplate;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.EmailStatus;
import com.example.hotelmanagement.entity.enums.EmailTemplateCode;
import com.example.hotelmanagement.exceptions.EmailQueueException;
import com.example.hotelmanagement.repositories.EmailMessageRepository;
import com.example.hotelmanagement.repositories.EmailTemplateRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class QueuedEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(QueuedEmailService.class);
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 2_000;
    private static final String STALE_MESSAGE_REASON = "Recovered after email worker timeout";

    private final EmailMessageRepository emailMessageRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateRenderer templateRenderer;
    private final AuthProperties authProperties;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public QueuedEmailService(
            EmailMessageRepository emailMessageRepository,
            EmailTemplateRepository emailTemplateRepository,
            EmailTemplateRenderer templateRenderer,
            AuthProperties authProperties,
            EmailProperties emailProperties,
            Clock clock
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.templateRenderer = templateRenderer;
        this.authProperties = authProperties;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verifyUrl = authProperties.frontendVerifyUrl() + "?token=" + token;
        queueTemplate(
                EmailTemplateCode.EMAIL_VERIFICATION,
                toEmail,
                null,
                null,
                Map.of(
                        "verificationLink", verifyUrl,
                        "token", valueOrEmpty(token),
                        "email", valueOrEmpty(toEmail),
                        "fullName", valueOrEmpty(fullName)
                )
        );
    }

    @Override
    @Transactional
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetUrl = authProperties.frontendResetUrl() + "?token=" + token;
        queueTemplate(
                EmailTemplateCode.PASSWORD_RESET,
                toEmail,
                null,
                null,
                Map.of(
                        "resetLink", resetUrl,
                        "token", valueOrEmpty(token),
                        "email", valueOrEmpty(toEmail),
                        "fullName", valueOrEmpty(fullName)
                )
        );
    }

    @Override
    @Transactional
    public void sendAccountActivatedEmail(User user) {
        if (user == null) {
            throw new EmailQueueException("Activated user is required");
        }
        queueTemplate(
                EmailTemplateCode.ACCOUNT_ACTIVATED,
                user.getEmail(),
                user.getId(),
                null,
                Map.of("customer_name", valueOrEmpty(user.getFullName()))
        );
    }

    @Override
    @Transactional
    public void sendBookingConfirmedEmail(Booking booking) {
        queueBookingTemplate(
                EmailTemplateCode.BOOKING_CONFIRMED,
                booking,
                Map.of(
                        "customer_name", valueOrEmpty(booking.getContactName()),
                        "booking_code", valueOrEmpty(booking.getBookingCode()),
                        "total_amount", formatAmount(booking.getTotalAmount()),
                        "currency", valueOrEmpty(booking.getCurrency())
                )
        );
    }

    @Override
    @Transactional
    public void sendBookingCancelledEmail(Booking booking) {
        queueBookingTemplate(
                EmailTemplateCode.BOOKING_CANCELLED,
                booking,
                Map.of(
                        "customer_name", valueOrEmpty(booking.getContactName()),
                        "booking_code", valueOrEmpty(booking.getBookingCode()),
                        "cancellation_reason", valueOrDefault(booking.getCancellationReason(), "Không có")
                )
        );
    }

    @Override
    @Transactional
    public void sendPaymentSuccessEmail(Payment payment) {
        if (payment == null || payment.getBooking() == null) {
            throw new EmailQueueException("Successful payment and booking are required");
        }
        Booking booking = payment.getBooking();
        queueBookingTemplate(
                EmailTemplateCode.PAYMENT_SUCCESS,
                booking,
                Map.of(
                        "customer_name", valueOrEmpty(booking.getContactName()),
                        "booking_code", valueOrEmpty(booking.getBookingCode()),
                        "payment_code", valueOrEmpty(payment.getPaymentCode()),
                        "payment_amount", formatAmount(payment.getAmount()),
                        "currency", valueOrEmpty(payment.getCurrency())
                )
        );
    }

    @Override
    @Transactional
    public void sendPaymentRefundEmail(Refund refund) {
        if (refund == null || refund.getBooking() == null) {
            throw new EmailQueueException("Completed refund and booking are required");
        }
        Booking booking = refund.getBooking();
        queueBookingTemplate(
                EmailTemplateCode.PAYMENT_REFUND,
                booking,
                Map.of(
                        "customer_name", valueOrEmpty(booking.getContactName()),
                        "booking_code", valueOrEmpty(booking.getBookingCode()),
                        "refund_amount", formatAmount(refund.getAmount()),
                        "currency", valueOrEmpty(booking.getCurrency())
                )
        );
    }

    @Transactional
    public List<Long> claimDueMessages() {
        OffsetDateTime now = now();
        emailMessageRepository.recoverStaleMessages(
                EmailStatus.SENDING,
                EmailStatus.QUEUED,
                now.minus(emailProperties.sendingTimeout()),
                now,
                STALE_MESSAGE_REASON
        );
        List<EmailMessage> messages = emailMessageRepository.findDueForUpdate(
                EmailStatus.QUEUED,
                now,
                PageRequest.of(0, emailProperties.batchSize())
        );
        messages.forEach(message -> message.setStatus(EmailStatus.SENDING));
        emailMessageRepository.saveAllAndFlush(messages);
        return messages.stream().map(EmailMessage::getId).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EmailTransport.DispatchMessage> getClaimedMessage(Long messageId) {
        return emailMessageRepository.findById(messageId)
                .filter(message -> message.getStatus() == EmailStatus.SENDING)
                .map(message -> new EmailTransport.DispatchMessage(
                        message.getId(),
                        message.getTemplateCode(),
                        message.getToEmail(),
                        message.getSubject(),
                        message.getBodyHtml(),
                        message.getBodyText()
                ));
    }

    @Transactional
    public void markSent(Long messageId, EmailTransport.DeliveryResult result) {
        EmailMessage message = getSendingMessage(messageId);
        if (message == null) {
            return;
        }
        message.setStatus(EmailStatus.SENT);
        message.setAttemptCount(attemptCount(message) + 1);
        message.setProvider(result.provider());
        message.setProviderMessageId(result.providerMessageId());
        message.setSentAt(now());
        message.setScheduledAt(null);
        message.setLastError(null);
    }

    @Transactional
    public void markFailed(Long messageId, String provider, Throwable failure) {
        EmailMessage message = getSendingMessage(messageId);
        if (message == null) {
            return;
        }
        int nextAttempt = attemptCount(message) + 1;
        message.setAttemptCount(nextAttempt);
        message.setProvider(provider);
        message.setLastError(truncateError(failure));
        if (nextAttempt >= emailProperties.maxAttempts()) {
            message.setStatus(EmailStatus.FAILED);
            message.setScheduledAt(null);
        } else {
            message.setStatus(EmailStatus.QUEUED);
            message.setScheduledAt(now().plus(emailProperties.retryDelay()));
        }
    }

    private void queueBookingTemplate(
            EmailTemplateCode templateCode,
            Booking booking,
            Map<String, String> variables
    ) {
        if (booking == null) {
            throw new EmailQueueException("Booking is required for email template " + templateCode);
        }
        if (booking.getContactEmail() == null || booking.getContactEmail().isBlank()) {
            return;
        }
        queueTemplate(
                templateCode,
                booking.getContactEmail(),
                getRecipientUserId(booking),
                booking.getId(),
                variables
        );
    }

    private void queueTemplate(
            EmailTemplateCode templateCode,
            String toEmail,
            Long toUserId,
            Long relatedBookingId,
            Map<String, String> variables
    ) {
        String normalizedEmail = normalizeEmail(toEmail);
        EmailTemplate template = emailTemplateRepository.findByCodeAndIsActiveTrue(templateCode.name())
                .orElseThrow(() -> new EmailQueueException(
                        "Active email template was not found: " + templateCode
                ));
        EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(template, variables);
        EmailMessage message = EmailMessage.builder()
                .templateCode(templateCode.name())
                .toEmail(normalizedEmail)
                .toUserId(toUserId)
                .subject(rendered.subject())
                .bodyHtml(rendered.bodyHtml())
                .bodyText(rendered.bodyText())
                .status(EmailStatus.QUEUED)
                .attemptCount(0)
                .scheduledAt(now())
                .relatedBookingId(relatedBookingId)
                .build();
        emailMessageRepository.save(message);
    }

    private EmailMessage getSendingMessage(Long messageId) {
        return emailMessageRepository.findById(messageId)
                .filter(message -> message.getStatus() == EmailStatus.SENDING)
                .orElse(null);
    }

    private Long getRecipientUserId(Booking booking) {
        CustomerProfile customerProfile = booking.getCustomerProfile();
        return customerProfile == null || customerProfile.getUser() == null
                ? null
                : customerProfile.getUser().getId();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > MAX_EMAIL_LENGTH
                || email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0) {
            throw new EmailQueueException("Email recipient is invalid");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
            return normalized;
        } catch (AddressException exception) {
            log.warn("Rejected invalid email recipient", exception);
            throw new EmailQueueException("Email recipient is invalid", exception);
        }
    }

    private String truncateError(Throwable failure) {
        String message = failure == null ? "Unknown email delivery error" : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null ? "Unknown email delivery error" : failure.getClass().getSimpleName();
        }
        String sanitized = message.replace('\0', '_');
        return sanitized.length() <= MAX_ERROR_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    private int attemptCount(EmailMessage message) {
        return message.getAttemptCount() == null ? 0 : message.getAttemptCount();
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
