package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;

@Repository
public interface BookingRoomRepository extends JpaRepository<BookingRoom, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(bookingRoom) > 0 THEN true ELSE false END
            FROM BookingRoom bookingRoom
            WHERE bookingRoom.room.id = :roomId
              AND bookingRoom.status IN :statuses
              AND bookingRoom.checkInDate < :endDate
              AND bookingRoom.checkOutDate > :startDate
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") Long roomId,
            @Param("statuses") Set<BookingRoomStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    boolean existsByRoomIdAndStatusIn(Long roomId, Set<BookingRoomStatus> statuses);
}
