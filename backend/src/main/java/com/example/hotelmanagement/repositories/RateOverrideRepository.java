package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RateOverride;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateOverrideRepository extends JpaRepository<RateOverride, Long> {

    @EntityGraph(attributePaths = "roomType")
    List<RateOverride> findAllByIsActiveTrueOrderByStartDateAscPriorityDescIdAsc();

    @EntityGraph(attributePaths = "roomType")
    Optional<RateOverride> findByIdAndIsActiveTrue(Long id);

    @Query("""
            SELECT rateOverride FROM RateOverride rateOverride
            LEFT JOIN FETCH rateOverride.roomType targetRoomType
            WHERE rateOverride.isActive = true
              AND rateOverride.startDate < :checkOutDate
              AND rateOverride.endDate > :checkInDate
              AND targetRoomType.id = :roomTypeId
            """)
    List<RateOverride> findActiveRoomTypeOverridesForPricing(
            @Param("roomTypeId") Long roomTypeId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );

    @Query("""
            SELECT rateOverride FROM RateOverride rateOverride
            LEFT JOIN FETCH rateOverride.roomType targetRoomType
            WHERE rateOverride.isActive = true
              AND rateOverride.priority = :priority
              AND rateOverride.startDate < :endDate
              AND rateOverride.endDate > :startDate
              AND (:excludedId IS NULL OR rateOverride.id <> :excludedId)
              AND targetRoomType.id = :roomTypeId
            """)
    List<RateOverride> findActiveConflicts(
            @Param("roomTypeId") Long roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("priority") Integer priority,
            @Param("excludedId") Long excludedId
    );
}
