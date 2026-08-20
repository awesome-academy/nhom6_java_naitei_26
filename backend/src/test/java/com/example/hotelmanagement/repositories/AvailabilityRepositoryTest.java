package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.RoomBlockType;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class AvailabilityRepositoryTest {

    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 9, 12);
    private static final Set<BookingRoomStatus> BLOCKING_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RoomType deluxeType;
    private RoomType suiteType;
    private Room roomA101;
    private Room roomA102;
    private Room roomB201;
    private Room roomB202;
    private int bookingSequence;

    @BeforeEach
    void setUp() {
        deluxeType = saveRoomType("DLX", "Deluxe");
        suiteType = saveRoomType("STE", "Suite");
        roomA101 = saveRoom("A101", deluxeType);
        roomA102 = saveRoom("A102", deluxeType);
        roomB201 = saveRoom("B201", suiteType);
        roomB202 = saveRoom("B202", suiteType);
    }

    @Test
    void findAvailableRoomsReturnsActiveInventoryInStableOrder() {
        List<AvailableRoomProjection> result = findAvailableRooms();

        assertEquals(
                List.of(roomA101.getId(), roomA102.getId(), roomB201.getId(), roomB202.getId()),
                result.stream().map(AvailableRoomProjection::getRoomId).toList()
        );
        assertEquals(
                List.of(deluxeType.getId(), deluxeType.getId(), suiteType.getId(), suiteType.getId()),
                result.stream().map(AvailableRoomProjection::getRoomTypeId).toList()
        );
    }

    @Test
    void findAvailableRoomsExcludesInactiveAndSoftDeletedRooms() {
        roomA101.setOperationalStatus(RoomOperationalStatus.MAINTENANCE);
        roomA102.setIsActive(false);
        roomB201.setDeletedAt(OffsetDateTime.now());
        roomRepository.saveAllAndFlush(List.of(roomA101, roomA102, roomB201));

        assertEquals(List.of(roomB202.getId()), roomIds(findAvailableRooms()));
    }

    @Test
    void findAvailableRoomsExcludesInactiveAndSoftDeletedRoomTypes() {
        deluxeType.setIsActive(false);
        suiteType.setDeletedAt(OffsetDateTime.now());
        roomTypeRepository.saveAllAndFlush(List.of(deluxeType, suiteType));

        assertEquals(List.of(), findAvailableRooms());
    }

    @Test
    void findAvailableRoomsExcludesReservedAndOccupiedOverlaps() {
        saveBookingRoom(roomA101, BookingRoomStatus.RESERVED, CHECK_IN_DATE, CHECK_OUT_DATE);
        saveBookingRoom(
                roomA102,
                BookingRoomStatus.OCCUPIED,
                CHECK_IN_DATE.minusDays(1),
                CHECK_IN_DATE.plusDays(1)
        );

        assertEquals(List.of(roomB201.getId(), roomB202.getId()), roomIds(findAvailableRooms()));
    }

    @Test
    void findAvailableRoomsIgnoresNonBlockingBookingStatuses() {
        saveBookingRoom(roomA101, BookingRoomStatus.COMPLETED, CHECK_IN_DATE, CHECK_OUT_DATE);
        saveBookingRoom(roomA102, BookingRoomStatus.RELEASED, CHECK_IN_DATE, CHECK_OUT_DATE);
        saveBookingRoom(roomB201, BookingRoomStatus.MOVED_OUT, CHECK_IN_DATE, CHECK_OUT_DATE);

        assertEquals(
                List.of(roomA101.getId(), roomA102.getId(), roomB201.getId(), roomB202.getId()),
                roomIds(findAvailableRooms())
        );
    }

    @Test
    void findAvailableRoomsExcludesOverlappingStatusBlocks() {
        saveBlock(roomA101, CHECK_IN_DATE, CHECK_OUT_DATE);
        saveBlock(roomB201, CHECK_IN_DATE.minusDays(1), CHECK_IN_DATE.plusDays(1));

        assertEquals(List.of(roomA102.getId(), roomB202.getId()), roomIds(findAvailableRooms()));
    }

    @Test
    void findAvailableRoomsAllowsBookingAndBlockAdjacency() {
        saveBookingRoom(
                roomA101,
                BookingRoomStatus.RESERVED,
                CHECK_IN_DATE.minusDays(2),
                CHECK_IN_DATE
        );
        saveBookingRoom(
                roomA102,
                BookingRoomStatus.OCCUPIED,
                CHECK_OUT_DATE,
                CHECK_OUT_DATE.plusDays(2)
        );
        saveBlock(roomB201, CHECK_IN_DATE.minusDays(2), CHECK_IN_DATE);
        saveBlock(roomB202, CHECK_OUT_DATE, CHECK_OUT_DATE.plusDays(2));

        assertEquals(
                List.of(roomA101.getId(), roomA102.getId(), roomB201.getId(), roomB202.getId()),
                roomIds(findAvailableRooms())
        );
    }

    private List<AvailableRoomProjection> findAvailableRooms() {
        entityManager.flush();
        entityManager.clear();
        return roomRepository.findAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_STATUSES
        );
    }

    private List<Long> roomIds(List<AvailableRoomProjection> rooms) {
        return rooms.stream().map(AvailableRoomProjection::getRoomId).toList();
    }

    private RoomType saveRoomType(String code, String name) {
        return roomTypeRepository.saveAndFlush(RoomType.builder()
                .code(code)
                .name(name)
                .slug(name.toLowerCase())
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(new BigDecimal("1000.00"))
                .currency("VND")
                .isActive(true)
                .build());
    }

    private Room saveRoom(String roomNumber, RoomType roomType) {
        return roomRepository.saveAndFlush(Room.builder()
                .roomNumber(roomNumber)
                .roomType(roomType)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .isActive(true)
                .build());
    }

    private void saveBookingRoom(
            Room room,
            BookingRoomStatus status,
            LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        bookingSequence++;
        jdbcTemplate.update("""
                INSERT INTO booking_rooms (
                    booking_id,
                    room_id,
                    room_type_id,
                    room_type_code_snapshot,
                    room_type_name_snapshot,
                    check_in_date,
                    check_out_date,
                    room_subtotal,
                    status,
                    guest_count,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                (long) bookingSequence,
                room.getId(),
                room.getRoomType().getId(),
                room.getRoomType().getCode(),
                room.getRoomType().getName(),
                checkInDate,
                checkOutDate,
                status.name()
        );
    }

    private void saveBlock(Room room, LocalDate startDate, LocalDate endDate) {
        entityManager.persistAndFlush(RoomStatusBlock.builder()
                .publicId(UUID.randomUUID().toString())
                .room(room)
                .blockType(RoomBlockType.MAINTENANCE)
                .startDate(startDate)
                .endDate(endDate)
                .reason("Availability test")
                .createdBy(1L)
                .build());
    }
}
