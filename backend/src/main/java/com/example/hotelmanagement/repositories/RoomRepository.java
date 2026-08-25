package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    @EntityGraph(attributePaths = {"roomType"})
    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    Optional<Room> findByRoomNumberIgnoreCaseAndDeletedAtIsNull(String roomNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    @Query("""
            SELECT room FROM Room room
            WHERE UPPER(room.roomNumber) = UPPER(:roomNumber)
              AND room.deletedAt IS NULL
            """)
    Optional<Room> findForUpdateByRoomNumber(@Param("roomNumber") String roomNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT room FROM Room room
            WHERE UPPER(room.roomNumber) = UPPER(:roomNumber)
              AND room.deletedAt IS NULL
            """)
    Optional<Room> findOperationalForUpdateByRoomNumber(@Param("roomNumber") String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndDeletedAtIsNull(String roomNumber);

    long countByDeletedAtIsNullAndIsActiveTrue();

    @Query("""
            SELECT COUNT(room)
            FROM Room room
            WHERE room.deletedAt IS NULL
              AND room.isActive = true
              AND room.operationalStatus = :operationalStatus
              AND room.roomType.deletedAt IS NULL
              AND room.roomType.isActive = true
            """)
    long countActiveOperationalRooms(
            @Param("operationalStatus") RoomOperationalStatus operationalStatus
    );

    @Query("""
            SELECT COUNT(room)
            FROM Room room
            WHERE room.deletedAt IS NULL
              AND room.isActive = true
              AND room.operationalStatus = :operationalStatus
              AND room.roomType.deletedAt IS NULL
              AND room.roomType.isActive = true
              AND NOT EXISTS (
                    SELECT bookingRoom.id
                    FROM BookingRoom bookingRoom
                    WHERE bookingRoom.room.id = room.id
                      AND bookingRoom.status IN :blockingStatuses
                      AND bookingRoom.checkInDate < :nextDate
                      AND bookingRoom.checkOutDate > :date
                  )
              AND NOT EXISTS (
                    SELECT block.id
                    FROM RoomStatusBlock block
                    WHERE block.room.id = room.id
                      AND block.startDate < :nextDate
                      AND block.endDate > :date
                  )
            """)
    long countAvailableOnDate(
            @Param("date") LocalDate date,
            @Param("nextDate") LocalDate nextDate,
            @Param("operationalStatus") RoomOperationalStatus operationalStatus,
            @Param("blockingStatuses") Set<BookingRoomStatus> blockingStatuses
    );

    @Query("""
            SELECT COUNT(DISTINCT bookingRoom.room.id)
            FROM BookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            WHERE bookingRoom.status IN :blockingStatuses
              AND bookingRoom.checkInDate < :nextDate
              AND bookingRoom.checkOutDate > :date
              AND booking.status IN :bookingStatuses
            """)
    long countOccupiedOrReservedOnDate(
            @Param("date") LocalDate date,
            @Param("nextDate") LocalDate nextDate,
            @Param("blockingStatuses") Set<BookingRoomStatus> blockingStatuses,
            @Param("bookingStatuses") Set<BookingStatus> bookingStatuses
    );

    @Query("""
            SELECT room.roomType.id AS roomTypeId, room.id AS roomId
            FROM Room room
            WHERE room.deletedAt IS NULL
              AND room.isActive = true
              AND room.operationalStatus = :operationalStatus
              AND room.roomType.deletedAt IS NULL
              AND room.roomType.isActive = true
              AND NOT EXISTS (
                    SELECT bookingRoom.id
                    FROM BookingRoom bookingRoom
                    WHERE bookingRoom.room.id = room.id
                      AND bookingRoom.status IN :blockingStatuses
                      AND bookingRoom.checkInDate < :checkOutDate
                      AND bookingRoom.checkOutDate > :checkInDate
                  )
              AND NOT EXISTS (
                    SELECT block.id
                    FROM RoomStatusBlock block
                    WHERE block.room.id = room.id
                      AND block.startDate < :checkOutDate
                      AND block.endDate > :checkInDate
                  )
            ORDER BY room.roomType.id ASC, room.id ASC
            """)
    List<AvailableRoomProjection> findAvailableRooms(
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("operationalStatus") RoomOperationalStatus operationalStatus,
            @Param("blockingStatuses") Set<BookingRoomStatus> blockingStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "roomType",
            "roomType.cancellationPolicyOptions",
            "roomType.cancellationPolicyOptions.cancellationPolicy",
            "roomType.cancellationPolicyOptions.cancellationPolicy.rules"
    })
    @Query("""
            SELECT room
            FROM Room room
            WHERE room.deletedAt IS NULL
              AND room.isActive = true
              AND room.operationalStatus = :operationalStatus
              AND room.roomType.deletedAt IS NULL
              AND room.roomType.isActive = true
              AND UPPER(room.roomType.code) = UPPER(:roomTypeCode)
              AND NOT EXISTS (
                    SELECT bookingRoom.id
                    FROM BookingRoom bookingRoom
                    WHERE bookingRoom.room.id = room.id
                      AND bookingRoom.status IN :blockingStatuses
                      AND bookingRoom.checkInDate < :checkOutDate
                      AND bookingRoom.checkOutDate > :checkInDate
                  )
              AND NOT EXISTS (
                    SELECT block.id
                    FROM RoomStatusBlock block
                    WHERE block.room.id = room.id
                      AND block.startDate < :checkOutDate
                      AND block.endDate > :checkInDate
                  )
            ORDER BY room.id ASC
            """)
    List<Room> findAvailableRoomsByTypeForUpdate(
            @Param("roomTypeCode") String roomTypeCode,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("operationalStatus") RoomOperationalStatus operationalStatus,
            @Param("blockingStatuses") Set<BookingRoomStatus> blockingStatuses
    );

    @Override
    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    List<Room> findAll(Specification<Room> specification, Sort sort);
}
