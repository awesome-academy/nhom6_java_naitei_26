package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms",
        indexes = {
                @Index(name = "idx_room_view_type", columnList = "view_type"),
                @Index(name = "idx_room_type_id", columnList = "room_type_id"),
                @Index(name = "idx_room_operational_active", columnList = "operational_status, is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type", nullable = false)
    @Builder.Default
    private RoomView viewType = RoomView.NONE;

    private Integer floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false)
    @Builder.Default
    private RoomOperationalStatus operationalStatus = RoomOperationalStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "housekeeping_status", nullable = false)
    @Builder.Default
    private HousekeepingStatus housekeepingStatus = HousekeepingStatus.CLEAN;

    @Column(name = "price_override", precision = 14, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "max_occupancy_override")
    private Integer maxOccupancyOverride;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<RoomImage> images = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "room_amenities",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    @Builder.Default
    private Set<Amenity> amenities = new HashSet<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<RoomStatusBlock> roomStatusBlocks = new HashSet<>();
}
