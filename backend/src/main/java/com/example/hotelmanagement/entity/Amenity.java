package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.AmenityCategory;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "amenities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Amenity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmenityCategory category;

    @Column(name = "is_filterable", nullable = false)
    @Builder.Default
    private Boolean isFilterable = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @ManyToMany(mappedBy = "amenities")
    @Builder.Default
    private Set<RoomType> roomTypes = new HashSet<>();

    @ManyToMany(mappedBy = "amenities")
    @Builder.Default
    private Set<Room> rooms = new HashSet<>();
}
