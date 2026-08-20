package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
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

    @Override
    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    List<Room> findAll(Specification<Room> specification, Sort sort);
}
