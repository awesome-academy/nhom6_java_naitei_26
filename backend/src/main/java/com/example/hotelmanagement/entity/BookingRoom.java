package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "booking_rooms",
        indexes = {
                @Index(name = "idx_br_booking", columnList = "booking_id"),
                @Index(name = "idx_br_room_status", columnList = "room_id, status"),
                @Index(name = "idx_br_dates_status", columnList = "room_id, check_in_date, check_out_date, status"),
                @Index(name = "idx_br_arrivals", columnList = "check_in_date, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRoom extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(name = "room_type_code_snapshot", nullable = false, length = 30)
    private String roomTypeCodeSnapshot;

    @Column(name = "room_type_name_snapshot", nullable = false, length = 120)
    private String roomTypeNameSnapshot;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "nights", insertable = false, updatable = false)
    private Integer nights;

    @Column(name = "room_subtotal", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal roomSubtotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingRoomStatus status = BookingRoomStatus.RESERVED;

    @Column(name = "guest_count", nullable = false)
    @Builder.Default
    private Integer guestCount = 1;

    @Column(name = "moved_from_booking_room_id")
    private Long movedFromBookingRoomId;

    @Column(name = "assigned_at")
    private java.time.OffsetDateTime assignedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @OneToMany(mappedBy = "bookingRoom", cascade = CascadeType.ALL)
    @Builder.Default
    private java.util.Set<BookingRoomNight> bookingRoomNights = new java.util.HashSet<>();
}
