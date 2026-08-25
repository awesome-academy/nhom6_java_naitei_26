package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingCode(String bookingCode);

    boolean existsByPublicId(String publicId);

    @EntityGraph(attributePaths = {
            "bookingRooms", "bookingRooms.bookingRoomNights", "bookingRooms.room",
            "bookingRooms.roomType", "bookingRooms.roomType.beds", "source"
    })
    Optional<Booking> findByPublicId(String publicId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Booking booking WHERE booking.id = :bookingId")
    int deleteRowById(@Param("bookingId") Long bookingId);

    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.status = :status
              AND booking.holdExpiresAt IS NOT NULL
              AND booking.holdExpiresAt < :now
            ORDER BY booking.holdExpiresAt ASC
            """)
    List<Booking> findPendingBookingsPastHold(
            @Param("status") BookingStatus status,
            @Param("now") OffsetDateTime now
    );

    @EntityGraph(attributePaths = {
            "bookingRooms", "bookingRooms.bookingRoomNights", "bookingRooms.room",
            "bookingRooms.roomType", "bookingRooms.roomType.beds", "source"
    })
    List<Booking> findAllByCustomerProfile_User_IdOrderByCreatedAtDesc(Long userId);

    long countByCustomerProfile_User_Id(Long userId);

    @EntityGraph(attributePaths = {
            "source",
            "bookingRooms",
            "bookingRooms.bookingRoomNights",
            "bookingRooms.roomType",
            "bookingRooms.roomType.beds",
            "bookingRooms.cancellationPolicy",
            "statusHistory"
    })
    Optional<Booking> findOneByPublicIdAndCustomerProfile_User_Id(String publicId, Long userId);

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

    @Query("""
            SELECT COUNT(booking) FROM Booking booking
            WHERE booking.status = :status
            """)
    long countByStatus(@Param("status") BookingStatus status);

    @Query("""
            SELECT booking FROM Booking booking
            LEFT JOIN FETCH booking.source
            LEFT JOIN FETCH booking.bookingRooms br
            LEFT JOIN FETCH br.room
            WHERE booking.publicId = :publicId
            """)
    Optional<Booking> findForStaffDetailByPublicId(@Param("publicId") String publicId);

    @Query("""
            SELECT booking FROM Booking booking
            LEFT JOIN FETCH booking.source
            LEFT JOIN FETCH booking.bookingRooms
            WHERE booking.status IN :statuses
            ORDER BY booking.createdAt DESC
            """)
    Page<Booking> findAllByStatusIn(@Param("statuses") Set<BookingStatus> statuses, Pageable pageable);

    @Query("""
            SELECT booking FROM Booking booking
            LEFT JOIN FETCH booking.source
            LEFT JOIN FETCH booking.bookingRooms
            WHERE booking.source.code = :sourceCode
            ORDER BY booking.createdAt DESC
            """)
    Page<Booking> findAllBySourceCode(@Param("sourceCode") String sourceCode, Pageable pageable);

    @Query("""
            SELECT booking FROM Booking booking
            LEFT JOIN FETCH booking.source
            LEFT JOIN FETCH booking.bookingRooms
            ORDER BY booking.createdAt DESC
            """)
    Page<Booking> findAllOrderByCreatedAtDesc(Pageable pageable);
}
