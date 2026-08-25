package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingGuest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingGuestRepository extends JpaRepository<BookingGuest, Long> {

    @EntityGraph(attributePaths = {"booking", "bookingRoom", "bookingRoom.room"})
    List<BookingGuest> findAllByBooking_PublicIdOrderByIdAsc(String bookingPublicId);

    @EntityGraph(attributePaths = {"booking", "bookingRoom", "bookingRoom.room"})
    Optional<BookingGuest> findByIdAndBooking_PublicId(Long id, String bookingPublicId);

    Optional<BookingGuest> findByIdDocumentLookupHash(byte[] idDocumentLookupHash);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM BookingGuest guest WHERE guest.bookingRoom.id = :bookingRoomId")
    int deleteAllByBookingRoomId(@Param("bookingRoomId") Long bookingRoomId);
}
