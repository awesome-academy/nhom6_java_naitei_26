package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {"bookingRooms", "bookingRooms.bookingRoomNights", "bookingRooms.room", "source"})
    Optional<Booking> findByPublicId(String publicId);
}
