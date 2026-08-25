package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRoomNightRepository extends JpaRepository<BookingRoomNight, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE BookingRoomNight night
            SET night.bookingRoom = :newBookingRoom
            WHERE night.bookingRoom = :previousBookingRoom
              AND night.stayDate >= :moveDate
            """)
    int transferNightsFromDate(
            @Param("previousBookingRoom") BookingRoom previousBookingRoom,
            @Param("newBookingRoom") BookingRoom newBookingRoom,
            @Param("moveDate") LocalDate moveDate
    );

    @Query("""
            SELECT COALESCE(SUM(night.price), 0) AS roomRevenue, COUNT(night) AS nightsCount
            FROM BookingRoomNight night
            JOIN night.bookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            WHERE bookingRoom.status IN :soldRoomStatuses
              AND booking.status IN :realizedBookingStatuses
              AND night.stayDate BETWEEN :from AND :to
            """)
    NightRevenueProjection aggregateSoldNights(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("soldRoomStatuses") Collection<BookingRoomStatus> soldRoomStatuses,
            @Param("realizedBookingStatuses") Collection<BookingStatus> realizedBookingStatuses
    );

    @Query("""
            SELECT bookingRoom.roomTypeCodeSnapshot AS roomTypeCode,
                   bookingRoom.roomTypeNameSnapshot AS roomTypeName,
                   COALESCE(SUM(night.price), 0) AS revenue,
                   COUNT(night) AS roomNights
            FROM BookingRoomNight night
            JOIN night.bookingRoom bookingRoom
            JOIN bookingRoom.booking booking
            WHERE bookingRoom.status IN :soldRoomStatuses
              AND booking.status IN :realizedBookingStatuses
              AND night.stayDate BETWEEN :from AND :to
            GROUP BY bookingRoom.roomTypeCodeSnapshot, bookingRoom.roomTypeNameSnapshot
            ORDER BY SUM(night.price) DESC
            """)
    List<RoomTypeRevenueProjection> findRevenueByRoomType(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("soldRoomStatuses") Collection<BookingRoomStatus> soldRoomStatuses,
            @Param("realizedBookingStatuses") Collection<BookingStatus> realizedBookingStatuses
    );
}
