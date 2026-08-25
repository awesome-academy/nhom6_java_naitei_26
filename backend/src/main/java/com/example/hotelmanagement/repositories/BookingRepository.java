package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingCode(String bookingCode);

    boolean existsByPublicId(String publicId);

    @EntityGraph(attributePaths = {"bookingRooms", "bookingRooms.bookingRoomNights", "bookingRooms.room", "source"})
    Optional<Booking> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"bookingRooms", "bookingRooms.bookingRoomNights", "bookingRooms.room", "source"})
    List<Booking> findAllByCustomerProfile_User_IdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "source",
            "customerProfile",
            "customerProfile.user",
            "bookingRooms",
            "bookingRooms.bookingRoomNights",
            "bookingRooms.room",
            "bookingRooms.cancellationPolicy",
            "bookingGuests"
    })
    Optional<Booking> findByPublicIdAndCustomerProfile_User_Id(String publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT booking FROM Booking booking WHERE booking.publicId = :publicId")
    Optional<Booking> findForUpdateByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT booking FROM Booking booking WHERE booking.id = :bookingId")
    Optional<Booking> findForUpdateById(@Param("bookingId") Long bookingId);
}
