package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.EmailProperties;
import com.example.hotelmanagement.exceptions.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;

@Service
@Profile("!dev-email-logging")
public class SmtpEmailService implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public DeliveryResult send(DispatchMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(message.toEmail());
            helper.setSubject(message.subject());
            if (StringUtils.hasText(message.bodyHtml())) {
                helper.setText(valueOrEmpty(message.bodyText()), message.bodyHtml());
            } else {
                helper.setText(valueOrEmpty(message.bodyText()));
            }
            if (StringUtils.hasText(emailProperties.fromName())) {
                helper.setFrom(emailProperties.fromAddress(), emailProperties.fromName());
            } else {
                helper.setFrom(emailProperties.fromAddress());
            }
            if (StringUtils.hasText(emailProperties.replyTo())) {
                helper.setReplyTo(emailProperties.replyTo());
            }
            mailSender.send(mimeMessage);
            return new DeliveryResult(getProviderCode(), mimeMessage.getMessageID());
        } catch (MessagingException | org.springframework.mail.MailException | UnsupportedEncodingException e) {
            log.error("SMTP delivery failed messageId={}", message.id(), e);
            throw new EmailDeliveryException("Email transport failed", e);
        }
    }

    @Override
    public String getProviderCode() {
        return emailProperties.provider();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
