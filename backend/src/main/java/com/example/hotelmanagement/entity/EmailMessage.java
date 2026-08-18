package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "email_messages",
        indexes = {
                @Index(name = "idx_em_status_scheduled", columnList = "status, scheduled_at"),
                @Index(name = "idx_em_to_email", columnList = "to_email"),
                @Index(name = "idx_em_booking", columnList = "related_booking_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage extends BaseEntity {

    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Column
    private String cc;

    @Column
    private String bcc;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailStatus status = EmailStatus.QUEUED;

    @Column(length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 150)
    private String providerMessageId;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "related_booking_id")
    private Long relatedBookingId;

    @Column(name = "created_by")
    private Long createdBy;
}
