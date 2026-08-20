package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RateOverride;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class RateOverrideRepositoryTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 25);

    @Autowired
    private RateOverrideRepository rateOverrideRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void findActiveConflictsFiltersByExactTargetAndExcludedId() {
        RoomType roomType = roomTypeRepository.saveAndFlush(createRoomType());
        Room room = roomRepository.saveAndFlush(
                Room.builder()
                        .roomNumber("A101")
                        .roomType(roomType)
                        .isActive(true)
                        .build()
        );
        RateOverride roomTypeRate = rateOverrideRepository.saveAndFlush(
                createRateOverride(roomType, null, true)
        );
        RateOverride roomRate = rateOverrideRepository.saveAndFlush(
                createRateOverride(null, room, true)
        );
        rateOverrideRepository.saveAndFlush(createRateOverride(roomType, null, false));

        List<RateOverride> roomTypeConflicts = rateOverrideRepository.findActiveConflicts(
                null,
                roomType.getId(),
                START_DATE,
                END_DATE,
                5,
                null
        );
        List<RateOverride> roomConflicts = rateOverrideRepository.findActiveConflicts(
                room.getId(),
                null,
                START_DATE,
                END_DATE,
                5,
                null
        );
        List<RateOverride> excludedConflicts = rateOverrideRepository.findActiveConflicts(
                null,
                roomType.getId(),
                START_DATE,
                END_DATE,
                5,
                roomTypeRate.getId()
        );

        assertEquals(List.of(roomTypeRate.getId()), getRateOverrideIds(roomTypeConflicts));
        assertEquals(List.of(roomRate.getId()), getRateOverrideIds(roomConflicts));
        assertTrue(excludedConflicts.isEmpty());
        assertEquals(
                2,
                rateOverrideRepository.findAllByIsActiveTrueOrderByStartDateAscPriorityDescIdAsc().size()
        );
    }

    private RateOverride createRateOverride(RoomType roomType, Room room, boolean active) {
        return RateOverride.builder()
                .roomType(roomType)
                .room(room)
                .name("Weekend")
                .startDate(START_DATE)
                .endDate(END_DATE)
                .price(new BigDecimal("1200.00"))
                .weekdays("[6,7]")
                .priority(5)
                .isActive(active)
                .build();
    }

    private RoomType createRoomType() {
        return RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(new BigDecimal("1000.00"))
                .currency("VND")
                .isActive(true)
                .build();
    }

    private List<Long> getRateOverrideIds(List<RateOverride> rateOverrides) {
        return rateOverrides.stream().map(RateOverride::getId).toList();
    }
}
