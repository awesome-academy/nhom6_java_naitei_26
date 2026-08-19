package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RateOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RateOverrideRepository extends JpaRepository<RateOverride, Long> {

    @Query("""
            SELECT rateOverride FROM RateOverride rateOverride
            LEFT JOIN FETCH rateOverride.room targetRoom
            LEFT JOIN FETCH rateOverride.roomType targetRoomType
            WHERE rateOverride.isActive = true
              AND rateOverride.startDate < :checkOutDate
              AND rateOverride.endDate > :checkInDate
              AND (targetRoom.id = :roomId OR targetRoomType.id = :roomTypeId)
            """)
    List<RateOverride> findActiveOverridesForPricing(
            @Param("roomId") Long roomId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );
}
