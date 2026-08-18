package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_event_id"}),
        indexes = @Index(name = "idx_pe_processed", columnList = "processed_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_event_id", length = 120)
    private String providerEventId;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSON")
    private String rawPayload;

    @Column(name = "received_ip")
    private String receivedIp;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
