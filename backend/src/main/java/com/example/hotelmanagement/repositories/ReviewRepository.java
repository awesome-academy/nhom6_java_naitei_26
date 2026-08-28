package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Review;
import com.example.hotelmanagement.entity.enums.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            SELECT review
            FROM Review review
            JOIN FETCH review.booking booking
            JOIN FETCH review.customerProfile customer
            JOIN FETCH customer.user customerUser
            LEFT JOIN FETCH review.room room
            LEFT JOIN FETCH review.roomType roomType
            WHERE (:status IS NULL OR review.status = :status)
              AND (:roomTypeCode IS NULL OR UPPER(roomType.code) = UPPER(:roomTypeCode))
              AND (:rating IS NULL OR review.overallRating = :rating)
            ORDER BY review.createdAt DESC, review.id DESC
            """,
            countQuery = """
            SELECT COUNT(review)
            FROM Review review
            LEFT JOIN review.roomType roomType
            WHERE (:status IS NULL OR review.status = :status)
              AND (:roomTypeCode IS NULL OR UPPER(roomType.code) = UPPER(:roomTypeCode))
              AND (:rating IS NULL OR review.overallRating = :rating)
            """)
    Page<Review> findAllForAdmin(
            @Param("status") ReviewStatus status,
            @Param("roomTypeCode") String roomTypeCode,
            @Param("rating") Integer rating,
            Pageable pageable
    );

    @Query(value = """
            SELECT review
            FROM Review review
            JOIN FETCH review.booking booking
            JOIN FETCH review.customerProfile customer
            JOIN FETCH customer.user customerUser
            LEFT JOIN FETCH review.room room
            LEFT JOIN FETCH review.roomType roomType
            WHERE (:status IS NULL OR review.status = :status)
              AND (:roomTypeCode IS NULL OR UPPER(roomType.code) = UPPER(:roomTypeCode))
              AND (:rating IS NULL OR review.overallRating = :rating)
            ORDER BY review.createdAt DESC, review.id DESC
            """,
            countQuery = """
            SELECT COUNT(review)
            FROM Review review
            LEFT JOIN review.roomType roomType
            WHERE (:status IS NULL OR review.status = :status)
              AND (:roomTypeCode IS NULL OR UPPER(roomType.code) = UPPER(:roomTypeCode))
              AND (:rating IS NULL OR review.overallRating = :rating)
            """)
    Page<Review> findAllForStaffReply(
            @Param("status") ReviewStatus status,
            @Param("roomTypeCode") String roomTypeCode,
            @Param("rating") Integer rating,
            Pageable pageable
    );

    @Query("""
            SELECT review
            FROM Review review
            JOIN FETCH review.customerProfile customer
            JOIN FETCH customer.user customerUser
            LEFT JOIN FETCH review.roomType roomType
            WHERE review.status = :status
            ORDER BY review.createdAt DESC, review.id DESC
            """)
    Page<Review> findAllPublished(
            @Param("status") ReviewStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(review.id) AS totalReviews,
                   AVG(review.overallRating) AS averageOverallRating,
                   AVG(review.roomRating) AS averageRoomRating,
                   AVG(review.cleanlinessRating) AS averageCleanlinessRating,
                   AVG(review.serviceRating) AS averageServiceRating,
                   AVG(review.valueRating) AS averageValueRating
            FROM Review review
            WHERE review.status = :status
            """)
    PublishedReviewAggregateProjection aggregatePublishedReviews(@Param("status") ReviewStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM Review review WHERE review.booking.publicId = :bookingPublicId")
    Optional<Review> findForUpdateByBooking_PublicId(@Param("bookingPublicId") String bookingPublicId);
}
