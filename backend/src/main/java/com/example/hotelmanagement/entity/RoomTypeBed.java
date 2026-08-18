package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.BedType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_type_beds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_type_id", "bed_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeBed extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type", nullable = false)
    private BedType bedType;

    @Column(nullable = false)
    private Integer quantity;
}
