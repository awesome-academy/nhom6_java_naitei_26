package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.config.EmailProperties;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.EmailMessage;
import com.example.hotelmanagement.entity.EmailTemplate;
import com.example.hotelmanagement.entity.enums.EmailStatus;
import com.example.hotelmanagement.repositories.EmailMessageRepository;
import com.example.hotelmanagement.repositories.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueuedEmailServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-25T04:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    private QueuedEmailService service;

    @BeforeEach
    void setUp() {
        service = new QueuedEmailService(
                emailMessageRepository,
                emailTemplateRepository,
                new EmailTemplateRenderer(),
                new AuthProperties(
                        5,
                        Duration.ofMinutes(15),
                        Duration.ofHours(24),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(30),
                        "http://localhost:3000/auth/verify-email",
                        "http://localhost:3000/auth/reset-password",
                        "http://localhost:3000/auth/staff-invitation"
                ),
                emailProperties(),
                FIXED_CLOCK
        );
    }

    @Test
    void sendBookingConfirmedEmailPersistsRenderedQueueSnapshot() {
        EmailTemplate template = template(
                "BOOKING_CONFIRMED",
                "Booking {{booking_code}} confirmed",
                "<p>Hello {{customer_name}}: {{total_amount}} {{currency}}</p>",
                "Hello {{customer_name}}: {{total_amount}} {{currency}}"
        );
        when(emailTemplateRepository.findByCodeAndIsActiveTrue("BOOKING_CONFIRMED"))
                .thenReturn(Optional.of(template));

        Booking booking = Booking.builder()
                .bookingCode("BK-2026-000001")
                .contactName("Nguyen <Admin>")
                .contactEmail("Guest@Example.com")
                .totalAmount(new BigDecimal("1200000.00"))
                .currency("VND")
                .build();
        booking.setId(10L);

        service.sendBookingConfirmedEmail(booking);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).save(captor.capture());
        EmailMessage queued = captor.getValue();
        assertThat(queued.getTemplateCode()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(queued.getToEmail()).isEqualTo("guest@example.com");
        assertThat(queued.getRelatedBookingId()).isEqualTo(10L);
        assertThat(queued.getStatus()).isEqualTo(EmailStatus.QUEUED);
        assertThat(queued.getScheduledAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(queued.getBodyHtml()).contains("Nguyen &lt;Admin&gt;");
        assertThat(queued.getBodyText()).contains("Nguyen <Admin>");
    }

    @Test
    void claimDueMessagesRecoversStaleRowsAndMarksBatchSending() {
        EmailMessage first = queuedMessage(1L, 0);
        EmailMessage second = queuedMessage(2L, 0);
        when(emailMessageRepository.findDueForUpdate(
                any(), any(), any()
        )).thenReturn(List.of(first, second));

        List<Long> claimed = service.claimDueMessages();

        assertThat(claimed).containsExactly(1L, 2L);
        assertThat(first.getStatus()).isEqualTo(EmailStatus.SENDING);
        assertThat(second.getStatus()).isEqualTo(EmailStatus.SENDING);
        verify(emailMessageRepository).recoverStaleMessages(
                EmailStatus.SENDING,
                EmailStatus.QUEUED,
                OffsetDateTime.now(FIXED_CLOCK).minusMinutes(5),
                OffsetDateTime.now(FIXED_CLOCK),
                "Recovered after email worker timeout"
        );
        verify(emailMessageRepository).saveAllAndFlush(List.of(first, second));
    }

    @Test
    void markFailedSchedulesRetryBeforeMaximumAttempts() {
        EmailMessage message = queuedMessage(1L, 0);
        message.setStatus(EmailStatus.SENDING);
        when(emailMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        service.markFailed(1L, "SMTP", new IllegalStateException("temporary failure"));

        assertThat(message.getStatus()).isEqualTo(EmailStatus.QUEUED);
        assertThat(message.getAttemptCount()).isEqualTo(1);
        assertThat(message.getScheduledAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK).plusSeconds(30));
        assertThat(message.getLastError()).isEqualTo("temporary failure");
    }

    @Test
    void markFailedStopsAfterThirdAttempt() {
        EmailMessage message = queuedMessage(1L, 2);
        message.setStatus(EmailStatus.SENDING);
        when(emailMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        service.markFailed(1L, "SMTP", new IllegalStateException("permanent failure"));

        assertThat(message.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(message.getAttemptCount()).isEqualTo(3);
        assertThat(message.getScheduledAt()).isNull();
    }

    @Test
    void markSentTracksProviderAndTimestamp() {
        EmailMessage message = queuedMessage(1L, 0);
        message.setStatus(EmailStatus.SENDING);
        when(emailMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        service.markSent(1L, new EmailTransport.DeliveryResult("SMTP", "provider-1"));

        assertThat(message.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(message.getAttemptCount()).isEqualTo(1);
        assertThat(message.getProvider()).isEqualTo("SMTP");
        assertThat(message.getProviderMessageId()).isEqualTo("provider-1");
        assertThat(message.getSentAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(message.getLastError()).isNull();
    }

    private EmailProperties emailProperties() {
        return new EmailProperties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                3,
                20,
                "SMTP",
                "noreply@tripstay.vn",
                "TripStay",
                "support@tripstay.vn"
        );
    }

    private EmailTemplate template(String code, String subject, String bodyHtml, String bodyText) {
        return EmailTemplate.builder()
                .code(code)
                .name(code)
                .subject(subject)
                .bodyHtml(bodyHtml)
                .bodyText(bodyText)
                .isActive(true)
                .build();
    }

    private EmailMessage queuedMessage(Long id, int attempts) {
        EmailMessage message = EmailMessage.builder()
                .templateCode("BOOKING_CONFIRMED")
                .toEmail("guest@example.com")
                .subject("Subject")
                .bodyText("Body")
                .status(EmailStatus.QUEUED)
                .attemptCount(attempts)
                .scheduledAt(OffsetDateTime.now(FIXED_CLOCK))
                .build();
        message.setId(id);
        return message;
    }
}
