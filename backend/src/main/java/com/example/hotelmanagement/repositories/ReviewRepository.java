package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBooking_Id(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "room", "roomType"})
    Optional<Review> findByBooking_PublicId(String bookingPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM Review review WHERE review.booking.publicId = :bookingPublicId")
    Optional<Review> findForUpdateByBooking_PublicId(@Param("bookingPublicId") String bookingPublicId);
}
