package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

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
}
