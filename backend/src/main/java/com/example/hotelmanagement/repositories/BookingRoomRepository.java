package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
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

    @Query("""
            SELECT CASE WHEN COUNT(bookingRoom) > 0 THEN true ELSE false END
            FROM BookingRoom bookingRoom
            WHERE bookingRoom.room.id = :roomId
              AND bookingRoom.id <> :excludedBookingRoomId
              AND bookingRoom.status IN :statuses
              AND bookingRoom.checkInDate < :endDate
              AND bookingRoom.checkOutDate > :startDate
            """)
    boolean existsOverlappingBookingExcludingId(
            @Param("roomId") Long roomId,
            @Param("excludedBookingRoomId") Long excludedBookingRoomId,
            @Param("statuses") Set<BookingRoomStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT bookingRoom
            FROM BookingRoom bookingRoom
            JOIN FETCH bookingRoom.booking booking
            JOIN FETCH bookingRoom.room room
            WHERE bookingRoom.id = :bookingRoomId
              AND booking.publicId = :bookingPublicId
            """)
    Optional<BookingRoom> findForUpdateByIdAndBookingPublicId(
            @Param("bookingRoomId") Long bookingRoomId,
            @Param("bookingPublicId") String bookingPublicId
    );
}
