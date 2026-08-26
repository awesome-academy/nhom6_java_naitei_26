package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reviews",
        indexes = @Index(name = "idx_rev_room_type_status", columnList = "room_type_id, status, created_at DESC"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id")
    private RoomType roomType;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(name = "room_rating")
    private Integer roomRating;

    @Column(name = "cleanliness_rating")
    private Integer cleanlinessRating;

    @Column(name = "service_rating")
    private Integer serviceRating;

    @Column(name = "value_rating")
    private Integer valueRating;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "moderation_reason", columnDefinition = "TEXT")
    private String moderationReason;

    @Column(name = "staff_reply", columnDefinition = "TEXT")
    private String staffReply;

    @Column(name = "staff_reply_by")
    private Long staffReplyBy;

    @Column(name = "staff_replied_at")
    private OffsetDateTime staffRepliedAt;
}
