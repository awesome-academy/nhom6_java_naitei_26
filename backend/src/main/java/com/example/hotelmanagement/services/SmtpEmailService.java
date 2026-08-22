package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.entity.EmailTemplate;
import com.example.hotelmanagement.repositories.EmailTemplateRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email service that reads templates from database and sends emails via SMTP.
 * Supports placeholder replacement with {{placeholder}} syntax.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Primary
public class SmtpEmailService implements EmailService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository emailTemplateRepository;
    private final AuthProperties authProperties;

    @Override
    @Async
    @Transactional
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verifyUrl = authProperties.frontendVerifyUrl() + "?token=" + token;
        Map<String, String> variables = new HashMap<>();
        variables.put("verificationLink", verifyUrl);
        variables.put("token", token);
        variables.put("email", toEmail);
        variables.put("fullName", fullName);
        sendTemplatedEmail("EMAIL_VERIFICATION", toEmail, fullName, variables);
    }

    @Override
    @Async
    @Transactional
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetUrl = authProperties.frontendResetUrl() + "?token=" + token;
        Map<String, String> variables = new HashMap<>();
        variables.put("resetLink", resetUrl);
        variables.put("token", token);
        variables.put("email", toEmail);
        sendTemplatedEmail("PASSWORD_RESET", toEmail, fullName, variables);
    }

    /**
     * Send email using a database template with variable substitution.
     */
    @Transactional
    public void sendTemplatedEmail(String templateCode, String toEmail, Map<String, String> variables) {
        sendTemplatedEmail(templateCode, toEmail, null, variables);
    }

    @Transactional
    public void sendTemplatedEmail(String templateCode, String toEmail, String fullName, Map<String, String> variables) {
        Optional<EmailTemplate> templateOpt = emailTemplateRepository.findByCodeAndIsActiveTrue(templateCode);

        if (templateOpt.isEmpty()) {
            log.error("Email template not found or inactive: {}", templateCode);
            return;
        }

        EmailTemplate template = templateOpt.get();

        // Substitute placeholders
        String subject = replacePlaceholders(template.getSubject(), variables);
        String bodyHtml = replacePlaceholders(template.getBodyHtml(), variables);
        String bodyText = template.getBodyText() != null
            ? replacePlaceholders(template.getBodyText(), variables)
            : stripHtml(bodyHtml);

        // Add fullName to variables if provided and not already present
        if (StringUtils.hasText(fullName) && !variables.containsKey("fullName")) {
            subject = replacePlaceholder(subject, "fullName", fullName);
            bodyHtml = replacePlaceholder(bodyHtml, "fullName", fullName);
            bodyText = replacePlaceholder(bodyText, "fullName", fullName);
        }

        // Send email
        sendEmail(
            toEmail,
            template.getFromEmail(),
            template.getFromName(),
            template.getReplyTo(),
            subject,
            bodyHtml,
            bodyText
        );
    }

    private void sendEmail(String to, String fromEmail, String fromName, String replyTo,
                          String subject, String bodyHtml, String bodyText) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(bodyText, bodyHtml);

            if (StringUtils.hasText(fromEmail)) {
                String from = StringUtils.hasText(fromName)
                    ? fromName + " <" + fromEmail + ">"
                    : fromEmail;
                helper.setFrom(from);
            }

            if (StringUtils.hasText(replyTo)) {
                helper.setReplyTo(replyTo);
            }

            mailSender.send(message);
            log.info("Email sent successfully to: {}, subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}, error: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Replace all {{placeholder}} patterns with values from the variables map.
     */
    private String replacePlaceholders(String template, Map<String, String> variables) {
        if (template == null || variables == null) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = variables.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Replace a single placeholder.
     */
    private String replacePlaceholder(String template, String placeholder, String value) {
        return template.replace("{{" + placeholder + "}}", value != null ? value : "");
    }

    /**
     * Strip HTML tags for plain text version.
     */
    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .trim();
    }

}
