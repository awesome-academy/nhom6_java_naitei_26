package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookingRoomRepository extends JpaRepository<BookingRoom, Long> {

    @Query("""
            SELECT room.id AS roomId,
                   booking.publicId AS bookingPublicId,
                   booking.bookingCode AS bookingCode,
                   booking.status AS bookingStatus,
                   bookingRoom.status AS bookingRoomStatus,
                   bookingRoom.checkInDate AS startDate,
                   bookingRoom.checkOutDate AS endDate
            FROM BookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            JOIN bookingRoom.room room
            WHERE room.deletedAt IS NULL
              AND bookingRoom.status IN (
                    com.example.hotelmanagement.entity.enums.BookingRoomStatus.RESERVED,
                    com.example.hotelmanagement.entity.enums.BookingRoomStatus.OCCUPIED
                  )
              AND bookingRoom.checkInDate < :endDate
              AND bookingRoom.checkOutDate > :startDate
            ORDER BY room.roomNumber ASC, bookingRoom.checkInDate ASC
            """)
    List<RoomBookingTimelineProjection> findBookingTimeline(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT room.roomNumber AS roomNumber,
                   booking.status AS bookingStatus,
                   bookingRoom.status AS bookingRoomStatus
            FROM BookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            JOIN bookingRoom.room room
            WHERE room.deletedAt IS NULL
              AND room.isActive = true
              AND bookingRoom.checkInDate <= :date
              AND bookingRoom.checkOutDate > :date
              AND (
                    (booking.status = com.example.hotelmanagement.entity.enums.BookingStatus.PENDING
                        AND bookingRoom.status = com.example.hotelmanagement.entity.enums.BookingRoomStatus.RESERVED)
                    OR (booking.status = com.example.hotelmanagement.entity.enums.BookingStatus.CONFIRMED
                        AND bookingRoom.status = com.example.hotelmanagement.entity.enums.BookingRoomStatus.RESERVED)
                    OR (booking.status = com.example.hotelmanagement.entity.enums.BookingStatus.CHECKED_IN
                        AND bookingRoom.status = com.example.hotelmanagement.entity.enums.BookingRoomStatus.OCCUPIED)
                  )
            ORDER BY room.roomNumber ASC
            """)
    List<RoomOccupancyProjection> findOccupancyOnDate(@Param("date") LocalDate date);

    @Query("""
            SELECT booking.publicId AS bookingPublicId,
                   booking.bookingCode AS bookingCode,
                   booking.contactName AS contactName,
                   booking.contactPhone AS contactPhone,
                   room.roomNumber AS roomNumber,
                   bookingRoom.roomTypeNameSnapshot AS roomTypeName,
                   bookingRoom.checkInDate AS checkInDate,
                   bookingRoom.checkOutDate AS checkOutDate,
                   booking.status AS bookingStatus,
                   bookingRoom.status AS bookingRoomStatus,
                   booking.totalAmount AS totalAmount,
                   booking.paidAmount AS paidAmount,
                   booking.refundedAmount AS refundedAmount
            FROM BookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            JOIN bookingRoom.room room
            WHERE bookingRoom.checkInDate = :date
              AND bookingRoom.status = com.example.hotelmanagement.entity.enums.BookingRoomStatus.RESERVED
              AND booking.status = com.example.hotelmanagement.entity.enums.BookingStatus.CONFIRMED
            ORDER BY room.roomNumber ASC, booking.bookingCode ASC
            """)
    List<DashboardStayProjection> findDashboardArrivals(@Param("date") LocalDate date);

    @Query("""
            SELECT booking.publicId AS bookingPublicId,
                   booking.bookingCode AS bookingCode,
                   booking.contactName AS contactName,
                   booking.contactPhone AS contactPhone,
                   room.roomNumber AS roomNumber,
                   bookingRoom.roomTypeNameSnapshot AS roomTypeName,
                   bookingRoom.checkInDate AS checkInDate,
                   bookingRoom.checkOutDate AS checkOutDate,
                   booking.status AS bookingStatus,
                   bookingRoom.status AS bookingRoomStatus,
                   booking.totalAmount AS totalAmount,
                   booking.paidAmount AS paidAmount,
                   booking.refundedAmount AS refundedAmount
            FROM BookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            JOIN bookingRoom.room room
            WHERE bookingRoom.checkOutDate = :date
              AND bookingRoom.status = com.example.hotelmanagement.entity.enums.BookingRoomStatus.OCCUPIED
              AND booking.status = com.example.hotelmanagement.entity.enums.BookingStatus.CHECKED_IN
            ORDER BY room.roomNumber ASC, booking.bookingCode ASC
            """)
    List<DashboardStayProjection> findDashboardDepartures(@Param("date") LocalDate date);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM BookingRoom bookingRoom WHERE bookingRoom.booking.id = :bookingId")
    int deleteAllByBookingId(@Param("bookingId") Long bookingId);

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

    @Query("""
            SELECT bookingRoom
            FROM BookingRoom bookingRoom
            JOIN FETCH bookingRoom.booking booking
            JOIN FETCH bookingRoom.room room
            WHERE bookingRoom.id = :bookingRoomId
              AND booking.publicId = :bookingPublicId
            """)
    Optional<BookingRoom> findByIdAndBookingPublicId(
            @Param("bookingRoomId") Long bookingRoomId,
            @Param("bookingPublicId") String bookingPublicId
    );
}
