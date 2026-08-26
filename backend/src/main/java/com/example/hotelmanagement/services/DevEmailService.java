package com.example.hotelmanagement.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev-email-logging")
public class DevEmailService implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(DevEmailService.class);

    @Override
    public DeliveryResult send(DispatchMessage message) {
        log.info(
                "Development email accepted messageId={} template={} recipient={}",
                message.id(),
                sanitizeForLog(message.templateCode()),
                maskEmail(message.toEmail())
        );
        return new DeliveryResult(getProviderCode(), "dev-" + message.id());
    }

    @Override
    public String getProviderCode() {
        return "LOG";
    }

    private String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        String sanitized = sanitizeForLog(email);
        int atIndex = sanitized.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return sanitized.charAt(0) + "***" + sanitized.substring(atIndex);
    }

    private String sanitizeForLog(String value) {
        return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
    }
}
