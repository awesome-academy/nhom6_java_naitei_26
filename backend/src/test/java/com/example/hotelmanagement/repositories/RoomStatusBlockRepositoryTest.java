package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.RoomBlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class RoomStatusBlockRepositoryTest {

    private static final LocalDate QUERY_START = LocalDate.of(2026, 9, 10);
    private static final LocalDate QUERY_END = LocalDate.of(2026, 9, 12);

    @Autowired
    private RoomStatusBlockRepository roomStatusBlockRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private Room roomA101;
    private Room roomA102;

    @BeforeEach
    void setUp() {
        RoomType roomType = roomTypeRepository.saveAndFlush(RoomType.builder()
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
                .build());
        roomA101 = roomRepository.save(Room.builder()
                .roomNumber("A101")
                .roomType(roomType)
                .isActive(true)
                .build());
        roomA102 = roomRepository.saveAndFlush(Room.builder()
                .roomNumber("A102")
                .roomType(roomType)
                .isActive(true)
                .build());
    }

    @Test
    void dateRangeQueryUsesHalfOpenOverlapAndExpectedSort() {
        saveBlock(roomA101, QUERY_START.minusDays(2), QUERY_START);
        RoomStatusBlock startsEarlier = saveBlock(
                roomA102,
                QUERY_START.minusDays(1),
                QUERY_START.plusDays(1)
        );
        RoomStatusBlock sameStart = saveBlock(roomA101, QUERY_START, QUERY_END);
        saveBlock(roomA102, QUERY_END, QUERY_END.plusDays(2));

        var result = roomStatusBlockRepository.findOverlappingDateRange(QUERY_START, QUERY_END);

        assertEquals(
                java.util.List.of(startsEarlier.getPublicId(), sameStart.getPublicId()),
                result.stream().map(RoomStatusBlock::getPublicId).toList()
        );
    }

    @Test
    void overlapChecksAllowAdjacencyAndCanExcludeCurrentBlock() {
        RoomStatusBlock current = saveBlock(roomA101, QUERY_START, QUERY_END);
        saveBlock(roomA101, QUERY_END, QUERY_END.plusDays(2));

        assertFalse(roomStatusBlockRepository.existsOverlappingBlockExcludingId(
                roomA101.getId(),
                current.getId(),
                QUERY_START,
                QUERY_END
        ));
        assertTrue(roomStatusBlockRepository.existsOverlappingBlockExcludingId(
                roomA101.getId(),
                current.getId(),
                QUERY_START,
                QUERY_END.plusDays(1)
        ));
        assertFalse(roomStatusBlockRepository.existsOverlappingBlock(
                roomA102.getId(),
                QUERY_START,
                QUERY_END
        ));
    }

    private RoomStatusBlock saveBlock(Room room, LocalDate startDate, LocalDate endDate) {
        return roomStatusBlockRepository.saveAndFlush(RoomStatusBlock.builder()
                .publicId(UUID.randomUUID().toString())
                .room(room)
                .blockType(RoomBlockType.MAINTENANCE)
                .startDate(startDate)
                .endDate(endDate)
                .reason("Maintenance")
                .createdBy(1L)
                .build());
    }
}
