package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.EmailTemplate;
import com.example.hotelmanagement.exceptions.EmailQueueException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EmailTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");
    private static final int MAX_SUBJECT_LENGTH = 300;

    public RenderedEmail render(EmailTemplate template, Map<String, String> variables) {
        if (template == null) {
            throw new EmailQueueException("Email template is required");
        }
        Map<String, String> safeVariables = variables == null ? Map.of() : variables;

        String subject = replacePlaceholders(
                template.getSubject(), safeVariables,
                value -> sanitizeHeader(value).strip()
        );
        if (subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH) {
            throw new EmailQueueException("Rendered email subject is invalid for template " + template.getCode());
        }

        String bodyHtml = replacePlaceholders(
                template.getBodyHtml(), safeVariables,
                value -> HtmlUtils.htmlEscape(value, "UTF-8")
        );
        String bodyText = replacePlaceholders(
                template.getBodyText(), safeVariables,
                UnaryOperator.identity()
        );
        if ((bodyHtml == null || bodyHtml.isBlank()) && (bodyText == null || bodyText.isBlank())) {
            throw new EmailQueueException("Rendered email body is empty for template " + template.getCode());
        }
        return new RenderedEmail(subject, bodyHtml, bodyText);
    }

    private String replacePlaceholders(
            String source,
            Map<String, String> variables,
            UnaryOperator<String> encoder
    ) {
        if (source == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(source);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.containsKey(name)) {
                throw new EmailQueueException("Missing email template variable: " + name);
            }
            String rawValue = variables.get(name);
            String encodedValue = encoder.apply(rawValue == null ? "" : rawValue);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(encodedValue));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String sanitizeHeader(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    public record RenderedEmail(String subject, String bodyHtml, String bodyText) {
    }
}
