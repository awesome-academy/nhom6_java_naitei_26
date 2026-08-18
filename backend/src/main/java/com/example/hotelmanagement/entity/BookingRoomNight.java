package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "booking_room_nights",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_room_id", "stay_date"}),
        indexes = @Index(name = "idx_brn_stay_date", columnList = "stay_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRoomNight extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_room_id", nullable = false)
    private BookingRoom bookingRoom;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "rate_override_id")
    private Long rateOverrideId;
}
