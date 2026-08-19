package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.RoomBlockType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "room_status_blocks",
        indexes = @Index(name = "idx_room_block_dates", columnList = "room_id, start_date, end_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomStatusBlock extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false)
    private RoomBlockType blockType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
