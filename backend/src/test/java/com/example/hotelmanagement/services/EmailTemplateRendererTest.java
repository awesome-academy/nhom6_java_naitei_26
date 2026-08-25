package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.EmailTemplate;
import com.example.hotelmanagement.exceptions.EmailQueueException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void renderEscapesHtmlAndSanitizesSubjectHeaders() {
        EmailTemplate template = EmailTemplate.builder()
                .code("BOOKING_CONFIRMED")
                .name("Booking confirmed")
                .subject("Confirmed {{booking_code}}")
                .bodyHtml("<p>Hello {{customer_name}}</p>")
                .bodyText("Hello {{customer_name}}")
                .build();

        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(
                template,
                Map.of(
                        "booking_code", "BK-1\r\nBcc: injected@example.com",
                        "customer_name", "<script>alert('x')</script>"
                )
        );

        assertThat(rendered.subject()).isEqualTo("Confirmed BK-1  Bcc: injected@example.com");
        assertThat(rendered.bodyHtml()).doesNotContain("<script>")
                .contains("&lt;script&gt;");
        assertThat(rendered.bodyText()).contains("<script>alert('x')</script>");
    }

    @Test
    void renderRejectsMissingVariables() {
        EmailTemplate template = EmailTemplate.builder()
                .code("BOOKING_CONFIRMED")
                .name("Booking confirmed")
                .subject("Confirmed {{booking_code}}")
                .bodyHtml("<p>Confirmed</p>")
                .build();

        assertThatThrownBy(() -> renderer.render(template, Map.of()))
                .isInstanceOf(EmailQueueException.class)
                .hasMessageContaining("booking_code");
    }
}
