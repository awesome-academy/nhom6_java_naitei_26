package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.review.ReviewCreateRequest;
import com.example.hotelmanagement.dto.review.ReviewListResponse;
import com.example.hotelmanagement.dto.review.ReviewModerationRequest;
import com.example.hotelmanagement.dto.review.ReviewReplyRequest;
import com.example.hotelmanagement.dto.review.ReviewResponse;
import com.example.hotelmanagement.dto.review.PublishedReviewListResponse;
import com.example.hotelmanagement.dto.review.PublishedReviewResponse;
import com.example.hotelmanagement.dto.review.PublishedReviewSummaryResponse;
import com.example.hotelmanagement.dto.review.StaffReviewListResponse;
import com.example.hotelmanagement.dto.review.StaffReviewResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.CustomerProfile;
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
import com.example.hotelmanagement.repositories.PublishedReviewAggregateProjection;
import com.example.hotelmanagement.repositories.ReviewRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final int MAX_MODERATION_REASON_LENGTH = 1000;
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

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.REVIEW_MODERATE)
    public ReviewListResponse listReviews(
            ReviewStatus status,
            String roomTypeCode,
            Integer rating,
            Integer page,
            Integer size
    ) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0 ? 20 : Math.min(size, 100);
        String normalizedRoomTypeCode = normalizeOptionalText(roomTypeCode);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessValidationException("Overall rating must be between 1 and 5");
        }

        Page<Review> reviews = reviewRepository.findAllForAdmin(
                status,
                normalizedRoomTypeCode,
                rating,
                PageRequest.of(normalizedPage, normalizedSize)
        );
        return new ReviewListResponse(
                reviews.getContent().stream().map(this::mapResponse).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.REVIEW_REPLY)
    public StaffReviewListResponse listReviewsForStaffReply(
            ReviewStatus status,
            String roomTypeCode,
            Integer rating,
            Integer page,
            Integer size
    ) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0 ? 20 : Math.min(size, 100);
        String normalizedRoomTypeCode = normalizeOptionalText(roomTypeCode);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessValidationException("Overall rating must be between 1 and 5");
        }

        Page<Review> reviews = reviewRepository.findAllForStaffReply(
                status,
                normalizedRoomTypeCode,
                rating,
                PageRequest.of(normalizedPage, normalizedSize)
        );
        return new StaffReviewListResponse(
                reviews.getContent().stream().map(this::mapStaffResponse).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    /** Returns only approved reviews; category aggregates cover all published reviews. */
    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public PublishedReviewListResponse listPublishedReviews(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0 ? 5 : Math.min(size, 50);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize);

        Page<Review> reviews = reviewRepository.findAllPublished(ReviewStatus.PUBLISHED, pageable);
        PublishedReviewAggregateProjection aggregate = reviewRepository.aggregatePublishedReviews(
                ReviewStatus.PUBLISHED
        );
        PublishedReviewSummaryResponse summary = new PublishedReviewSummaryResponse(
                valueOrZero(aggregate.getTotalReviews()),
                aggregate.getAverageOverallRating(),
                aggregate.getAverageRoomRating(),
                aggregate.getAverageCleanlinessRating(),
                aggregate.getAverageServiceRating(),
                aggregate.getAverageValueRating()
        );

        return new PublishedReviewListResponse(
                reviews.getContent().stream().map(this::mapPublishedResponse).toList(),
                summary,
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    /** Admin approve/reject: PENDING (or an already-moderated review) -> PUBLISHED/HIDDEN/REJECTED. */
    @PreAuthorize(PermissionExpressions.REVIEW_MODERATE)
    public ReviewResponse moderate(String bookingPublicId, ReviewModerationRequest request) {
        if (request == null || request.status() == null || !MODERATION_TARGET_STATUSES.contains(request.status())) {
            throw new BusinessValidationException(
                    "Moderation status must be one of PUBLISHED, HIDDEN, or REJECTED"
            );
        }
        String moderationReason = normalizeOptionalText(request.moderationReason());
        if (moderationReason != null && moderationReason.length() > MAX_MODERATION_REASON_LENGTH) {
            throw new BusinessValidationException("Moderation reason must not exceed 1000 characters");
        }
        if (request.status() == ReviewStatus.REJECTED && moderationReason == null) {
            throw new BusinessValidationException("A moderation reason is required when rejecting a review");
        }
        Review review = getReviewForUpdate(bookingPublicId);
        review.setStatus(request.status());
        review.setModerationReason(request.status() == ReviewStatus.REJECTED ? moderationReason : null);
        return mapResponse(reviewRepository.saveAndFlush(review));
    }

    /** Staff reply, shown alongside the review; a later reply overwrites the previous one. */
    @PreAuthorize(PermissionExpressions.REVIEW_REPLY)
    public ReviewResponse reply(String bookingPublicId, ReviewReplyRequest request, Long actorUserId) {
        Review review = getReviewForUpdate(bookingPublicId);
        StaffProfile staffProfile = staffProfileRepository.findByUser_Id(actorUserId).orElse(null);

        review.setStaffReply(normalizeOptionalText(request.staffReply()));
        // ADMIN accounts do not require a StaffProfile; the audit aspect still records the actor.
        review.setStaffReplyBy(staffProfile == null ? null : staffProfile.getId());
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
        CustomerProfile customerProfile = review.getCustomerProfile();
        Room room = review.getRoom();
        RoomType roomType = review.getRoomType();
        String customerName = customerProfile != null && customerProfile.getUser() != null
                ? customerProfile.getUser().getFullName() : null;
        String customerEmail = customerProfile != null && customerProfile.getUser() != null
                ? customerProfile.getUser().getEmail() : null;
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getPublicId(),
                review.getBooking().getBookingCode(),
                customerName,
                customerEmail,
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
                review.getModerationReason(),
                review.getStaffReply(),
                review.getStaffReplyBy(),
                review.getStaffRepliedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private PublishedReviewResponse mapPublishedResponse(Review review) {
        CustomerProfile customerProfile = review.getCustomerProfile();
        RoomType roomType = review.getRoomType();
        String customerName = customerProfile != null && customerProfile.getUser() != null
                ? customerProfile.getUser().getFullName() : null;

        return new PublishedReviewResponse(
                customerName,
                roomType != null ? roomType.getName() : null,
                review.getOverallRating(),
                review.getRoomRating(),
                review.getCleanlinessRating(),
                review.getServiceRating(),
                review.getValueRating(),
                review.getTitle(),
                review.getComment(),
                review.getStaffReply(),
                review.getStaffRepliedAt(),
                review.getCreatedAt()
        );
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private StaffReviewResponse mapStaffResponse(Review review) {
        ReviewResponse response = mapResponse(review);
        return new StaffReviewResponse(
                response.id(),
                response.bookingPublicId(),
                response.bookingCode(),
                response.customerName(),
                response.customerEmail(),
                response.roomNumber(),
                response.roomTypeCode(),
                response.roomTypeName(),
                response.overallRating(),
                response.roomRating(),
                response.cleanlinessRating(),
                response.serviceRating(),
                response.valueRating(),
                response.title(),
                response.comment(),
                response.status(),
                response.staffReply(),
                response.staffReplyBy(),
                response.staffRepliedAt(),
                response.createdAt(),
                response.updatedAt()
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
