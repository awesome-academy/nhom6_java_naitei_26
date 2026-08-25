package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    @Query("""
            SELECT booking.checkedOutAt AS checkedOutAt,
                   booking.totalAmount AS totalAmount,
                   booking.roomsTotal AS roomsTotal,
                   booking.sourceCommissionPercentSnapshot AS sourceCommissionPercentSnapshot,
                   booking.source.code AS sourceCode,
                   booking.source.name AS sourceName
            FROM Booking booking
            WHERE booking.status = :status
              AND booking.checkedOutAt >= :from
              AND booking.checkedOutAt < :toExclusive
            """)
    List<BookingRevenueProjection> findRevenueRecognizedBookings(
            @Param("status") BookingStatus status,
            @Param("from") OffsetDateTime from,
            @Param("toExclusive") OffsetDateTime toExclusive
    );
}
