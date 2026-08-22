package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 2)
    private String nationality;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String province;

    @Column(length = 2)
    private String country;

    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Column(name = "total_stays", nullable = false)
    @Builder.Default
    private Integer totalStays = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "customerProfile", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Booking> bookings = new HashSet<>();

    @OneToMany(mappedBy = "customerProfile", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Review> reviews = new HashSet<>();
}
