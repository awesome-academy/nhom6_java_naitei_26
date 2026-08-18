package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "room_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "storage_key", columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
