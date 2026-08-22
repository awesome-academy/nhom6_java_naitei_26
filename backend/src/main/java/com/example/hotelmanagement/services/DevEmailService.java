package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("dev-email-logging")
public class DevEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevEmailService.class);
    private final AuthProperties authProperties;

    @Override
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verifyUrl = authProperties.frontendVerifyUrl() + "?token=" + token;
        log.info("========================================");
        log.info("SENDING EMAIL VERIFICATION");
        log.info("To: {}", toEmail);
        log.info("FullName: {}", fullName);
        log.info("Subject: Please verify your email address");
        log.info("Link: {}", verifyUrl);
        log.info("========================================");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetUrl = authProperties.frontendResetUrl() + "?token=" + token;
        log.info("========================================");
        log.info("SENDING PASSWORD RESET");
        log.info("To: {}", toEmail);
        log.info("FullName: {}", fullName);
        log.info("Subject: Reset your password");
        log.info("Link: {}", resetUrl);
        log.info("========================================");
    }
}
