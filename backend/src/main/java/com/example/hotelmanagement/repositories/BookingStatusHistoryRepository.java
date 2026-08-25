package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM BookingStatusHistory history WHERE history.booking.id = :bookingId")
    int deleteAllByBookingId(@Param("bookingId") Long bookingId);
}
