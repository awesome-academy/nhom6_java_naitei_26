package com.example.hotelmanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.convert.DurationStyle;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailQueueWorkerTest {

    @Mock
    private QueuedEmailService queuedEmailService;
    @Mock
    private EmailTransport emailTransport;

    private EmailQueueWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EmailQueueWorker(queuedEmailService, emailTransport);
    }

    @Test
    void dispatchQueuedEmailsMarksSuccessfulDelivery() {
        EmailTransport.DispatchMessage message = new EmailTransport.DispatchMessage(
                1L, "BOOKING_CONFIRMED", "guest@example.com", "Subject", "<p>Body</p>", "Body"
        );
        EmailTransport.DeliveryResult result = new EmailTransport.DeliveryResult("SMTP", "provider-1");
        when(queuedEmailService.claimDueMessages()).thenReturn(List.of(1L));
        when(queuedEmailService.getClaimedMessage(1L)).thenReturn(Optional.of(message));
        when(emailTransport.send(message)).thenReturn(result);

        worker.dispatchQueuedEmails();

        verify(queuedEmailService).markSent(1L, result);
    }

    @Test
    void dispatchQueuedEmailsReturnsFailedDeliveryToQueue() {
        EmailTransport.DispatchMessage message = new EmailTransport.DispatchMessage(
                1L, "BOOKING_CONFIRMED", "guest@example.com", "Subject", null, "Body"
        );
        IllegalStateException failure = new IllegalStateException("SMTP unavailable");
        when(queuedEmailService.claimDueMessages()).thenReturn(List.of(1L));
        when(queuedEmailService.getClaimedMessage(1L)).thenReturn(Optional.of(message));
        when(emailTransport.send(message)).thenThrow(failure);
        when(emailTransport.getProviderCode()).thenReturn("SMTP");

        worker.dispatchQueuedEmails();

        verify(queuedEmailService).markFailed(1L, "SMTP", failure);
    }

    @Test
    void pollIntervalAcceptsHumanReadableDuration() {
        assertThat(DurationStyle.detectAndParse("2s")).isEqualTo(Duration.ofSeconds(2));
    }
}
