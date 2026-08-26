package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.review.ReviewCreateRequest;
import com.example.hotelmanagement.dto.review.ReviewModerationRequest;
import com.example.hotelmanagement.dto.review.ReviewReplyRequest;
import com.example.hotelmanagement.dto.review.ReviewResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Review;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.ReviewStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.ReviewRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Guest reviews for a completed stay (BE-8.2). BR-006/BR-007 (checked-out booking owned by the
 * reviewing customer; one review per booking) are already enforced by the DB trigger
 * {@code trg_reviews_before_insert} and the {@code UNIQUE(booking_id)} constraint from V1 — the
 * checks here are the same application-layer pre-checks used elsewhere in this codebase (e.g.
 * RefundService) so callers get a clean 4xx instead of a raw constraint-violation 500.
 *
 * <p>Note: the {@code reviews.status} column defaults to {@code PUBLISHED} at the DB level, but
 * this service always sets a newly created review to {@code PENDING} so it goes through admin
 * moderation first, per the ticket's described workflow
 * ({@code PENDING → PUBLISHED/HIDDEN/REJECTED}). The column default only applies when a row is
 * inserted without an explicit status, which never happens through this service.</p>
 */
@Service
@Transactional
public class ReviewService {

    private static final Set<ReviewStatus> MODERATION_TARGET_STATUSES = Set.of(
            ReviewStatus.PUBLISHED, ReviewStatus.HIDDEN, ReviewStatus.REJECTED
    );

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final Clock clock;

    public ReviewService(
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            StaffProfileRepository staffProfileRepository,
            Clock clock
    ) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.clock = clock;
    }

    /** BR-006/BR-007: only the checked-out booking's own customer, once per booking. */
    @PreAuthorize(PermissionExpressions.REVIEW_CREATE)
    public ReviewResponse createReview(String bookingPublicId, ReviewCreateRequest request, Long actorUserId) {
        Booking booking = bookingRepository.findByPublicIdAndCustomerProfile_User_Id(bookingPublicId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));

        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw new BusinessValidationException("A review can only be created for a checked-out booking");
        }
        if (reviewRepository.existsByBooking_Id(booking.getId())) {
            throw new DuplicateResourceException("Review", "booking id", booking.getId().toString());
        }

        Review.ReviewBuilder reviewBuilder = Review.builder()
                .booking(booking)
                .customerProfile(booking.getCustomerProfile())
                .overallRating(request.overallRating())
                .roomRating(request.roomRating())
                .cleanlinessRating(request.cleanlinessRating())
                .serviceRating(request.serviceRating())
                .valueRating(request.valueRating())
                .title(normalizeOptionalText(request.title()))
                .comment(normalizeOptionalText(request.comment()))
                .status(ReviewStatus.PENDING);
        applySingleRoomContext(reviewBuilder, booking);

        return mapResponse(reviewRepository.saveAndFlush(reviewBuilder.build()));
    }

    /** Returns the current customer's review for the booking, if one has already been submitted. */
    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.REVIEW_CREATE)
    public ReviewResponse getReview(String bookingPublicId, Long actorUserId) {
        bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id(bookingPublicId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));

        Review review = reviewRepository.findByBooking_PublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", bookingPublicId));
        return mapResponse(review);
    }

    /** Admin approve/reject: PENDING (or an already-moderated review) -> PUBLISHED/HIDDEN/REJECTED. */
    @PreAuthorize(PermissionExpressions.REVIEW_MODERATE)
    public ReviewResponse moderate(String bookingPublicId, ReviewModerationRequest request) {
        if (!MODERATION_TARGET_STATUSES.contains(request.status())) {
            throw new BusinessValidationException(
                    "Moderation status must be one of PUBLISHED, HIDDEN, or REJECTED"
            );
        }
        Review review = getReviewForUpdate(bookingPublicId);
        review.setStatus(request.status());
        return mapResponse(reviewRepository.saveAndFlush(review));
    }

    /** Staff reply, shown alongside the review; a later reply overwrites the previous one. */
    @PreAuthorize(PermissionExpressions.REVIEW_REPLY)
    public ReviewResponse reply(String bookingPublicId, ReviewReplyRequest request, Long actorUserId) {
        Review review = getReviewForUpdate(bookingPublicId);
        StaffProfile staffProfile = staffProfileRepository.findByUser_Id(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile", actorUserId.toString()));

        review.setStaffReply(normalizeOptionalText(request.staffReply()));
        review.setStaffReplyBy(staffProfile.getId());
        review.setStaffRepliedAt(OffsetDateTime.now(clock));

        return mapResponse(reviewRepository.saveAndFlush(review));
    }

    private void applySingleRoomContext(Review.ReviewBuilder builder, Booking booking) {
        Set<BookingRoom> bookingRooms = booking.getBookingRooms();
        if (bookingRooms != null && bookingRooms.size() == 1) {
            Room room = bookingRooms.iterator().next().getRoom();
            RoomType roomType = room.getRoomType();
            builder.room(room).roomType(roomType);
        }
    }

    private Review getReviewForUpdate(String bookingPublicId) {
        return reviewRepository.findForUpdateByBooking_PublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", bookingPublicId));
    }

    private ReviewResponse mapResponse(Review review) {
        Room room = review.getRoom();
        RoomType roomType = review.getRoomType();
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getPublicId(),
                room != null ? room.getRoomNumber() : null,
                roomType != null ? roomType.getCode() : null,
                roomType != null ? roomType.getName() : null,
                review.getOverallRating(),
                review.getRoomRating(),
                review.getCleanlinessRating(),
                review.getServiceRating(),
                review.getValueRating(),
                review.getTitle(),
                review.getComment(),
                review.getStatus(),
                review.getStaffReply(),
                review.getStaffReplyBy(),
                review.getStaffRepliedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
