package com.example.hotelmanagement.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueWorker.class);

    private final QueuedEmailService queuedEmailService;
    private final EmailTransport emailTransport;

    public EmailQueueWorker(QueuedEmailService queuedEmailService, EmailTransport emailTransport) {
        this.queuedEmailService = queuedEmailService;
        this.emailTransport = emailTransport;
    }

    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${app.email.poll-interval:30s}').toMillis()}"
    )
    public void dispatchQueuedEmails() {
        List<Long> messageIds = queuedEmailService.claimDueMessages();
        for (Long messageId : messageIds) {
            dispatchMessage(messageId);
        }
    }

    private void dispatchMessage(Long messageId) {
        Optional<EmailTransport.DispatchMessage> claimedMessage = queuedEmailService.getClaimedMessage(messageId);
        if (claimedMessage.isEmpty()) {
            return;
        }
        try {
            EmailTransport.DeliveryResult result = emailTransport.send(claimedMessage.get());
            queuedEmailService.markSent(messageId, result);
        } catch (RuntimeException exception) {
            log.error(
                    "Email delivery failed messageId={} provider={}",
                    messageId,
                    emailTransport.getProviderCode(),
                    exception
            );
            queuedEmailService.markFailed(messageId, emailTransport.getProviderCode(), exception);
        }
    }
}
