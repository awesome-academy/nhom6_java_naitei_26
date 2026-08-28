package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.review.ReviewCreateRequest;
import com.example.hotelmanagement.dto.review.ReviewModerationRequest;
import com.example.hotelmanagement.dto.review.ReviewReplyRequest;
import com.example.hotelmanagement.dto.review.ReviewListResponse;
import com.example.hotelmanagement.dto.review.ReviewResponse;
import com.example.hotelmanagement.repositories.PublishedReviewAggregateProjection;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Review;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.ReviewStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.ReviewRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String BOOKING_PUBLIC_ID = "booking-public-id";
    private static final Long CUSTOMER_USER_ID = 1L;
    private static final Long STAFF_USER_ID = 99L;
    private static final Long BOOKING_ID = 10L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-25T09:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private PublishedReviewAggregateProjection publishedReviewAggregate;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository, staffProfileRepository, FIXED_CLOCK);
    }

    @Test
    void createReviewSucceedsForCheckedOutBookingWithSingleRoom() {
        Booking booking = checkedOutBooking(1);
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBooking_Id(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.createReview(
                BOOKING_PUBLIC_ID,
                new ReviewCreateRequest(5, 4, 5, 4, 5, "Great stay", "Loved it"),
                CUSTOMER_USER_ID
        );

        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.overallRating()).isEqualTo(5);
        assertThat(response.roomNumber()).isEqualTo("101");
        assertThat(response.roomTypeCode()).isEqualTo("DLX");
        assertThat(response.title()).isEqualTo("Great stay");
    }

    @Test
    void createReviewLeavesRoomContextNullForMultiRoomBooking() {
        Booking booking = checkedOutBooking(2);
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBooking_Id(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.createReview(
                BOOKING_PUBLIC_ID,
                new ReviewCreateRequest(5, null, null, null, null, null, null),
                CUSTOMER_USER_ID
        );

        assertThat(response.roomNumber()).isNull();
        assertThat(response.roomTypeCode()).isNull();
    }

    @Test
    void createReviewRejectsBookingNotCheckedOut() {
        Booking booking = checkedOutBooking(1);
        booking.setStatus(BookingStatus.CHECKED_IN);
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.createReview(
                BOOKING_PUBLIC_ID,
                new ReviewCreateRequest(5, null, null, null, null, null, null),
                CUSTOMER_USER_ID
        )).isInstanceOf(BusinessValidationException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReviewRejectsDuplicateReview() {
        Booking booking = checkedOutBooking(1);
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBooking_Id(BOOKING_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(
                BOOKING_PUBLIC_ID,
                new ReviewCreateRequest(5, null, null, null, null, null, null),
                CUSTOMER_USER_ID
        )).isInstanceOf(DuplicateResourceException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReviewRejectsWhenBookingNotOwnedByActor() {
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(
                BOOKING_PUBLIC_ID,
                new ReviewCreateRequest(5, null, null, null, null, null, null),
                CUSTOMER_USER_ID
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getReviewReturnsOwnedBookingReview() {
        Booking booking = checkedOutBooking(1);
        Review review = pendingReview();
        review.setBooking(booking);
        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(reviewRepository.findByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));

        ReviewResponse response = reviewService.getReview(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID);

        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.bookingPublicId()).isEqualTo(BOOKING_PUBLIC_ID);
    }

    @Test
    void getReviewRejectsBookingNotOwnedByActor() {
        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getReview(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).findByBooking_PublicId(any());
    }

    @Test
    void getReviewReturnsNotFoundWhenOwnedBookingHasNoReview() {
        Booking booking = checkedOutBooking(1);
        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(reviewRepository.findByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getReview(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listReviewsUsesAdminFiltersAndPagination() {
        org.springframework.data.domain.Page<Review> result =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(pendingReview()));
        when(reviewRepository.findAllForAdmin(
                ReviewStatus.PENDING,
                "DLX",
                5,
                org.springframework.data.domain.PageRequest.of(1, 10)
        )).thenReturn(result);

        ReviewListResponse response = reviewService.listReviews(ReviewStatus.PENDING, "  DLX  ", 5, 1, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(1);
        verify(reviewRepository).findAllForAdmin(
                ReviewStatus.PENDING,
                "DLX",
                5,
                org.springframework.data.domain.PageRequest.of(1, 10)
        );
    }

    @Test
    void listPublishedReviewsUsesPublishedPageAndAllPublishedAggregates() {
        Review review = publishedReview();
        org.springframework.data.domain.PageRequest pageRequest =
                org.springframework.data.domain.PageRequest.of(1, 5);
        when(reviewRepository.findAllPublished(ReviewStatus.PUBLISHED, pageRequest))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(review), pageRequest, 6
                ));
        when(reviewRepository.aggregatePublishedReviews(ReviewStatus.PUBLISHED))
                .thenReturn(publishedReviewAggregate);
        when(publishedReviewAggregate.getTotalReviews()).thenReturn(6L);
        when(publishedReviewAggregate.getAverageOverallRating()).thenReturn(4.5);
        when(publishedReviewAggregate.getAverageRoomRating()).thenReturn(4.25);
        when(publishedReviewAggregate.getAverageCleanlinessRating()).thenReturn(4.75);
        when(publishedReviewAggregate.getAverageServiceRating()).thenReturn(4.0);
        when(publishedReviewAggregate.getAverageValueRating()).thenReturn(null);

        var response = reviewService.listPublishedReviews(1, 5);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalItems()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.summary().totalReviews()).isEqualTo(6);
        assertThat(response.summary().averageOverallRating()).isEqualTo(4.5);
        assertThat(response.summary().averageValueRating()).isNull();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.customerName()).isEqualTo("Nguyen Van A");
            assertThat(item.roomTypeName()).isEqualTo("Deluxe");
            assertThat(item.staffReply()).isEqualTo("Thank you");
        });
        verify(reviewRepository).findAllPublished(ReviewStatus.PUBLISHED, pageRequest);
        verify(reviewRepository).aggregatePublishedReviews(ReviewStatus.PUBLISHED);
    }

    @Test
    void moderateTransitionsPendingToPublished() {
        Review review = pendingReview();
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        ReviewResponse response = reviewService.moderate(
                BOOKING_PUBLIC_ID, new ReviewModerationRequest(ReviewStatus.PUBLISHED, null)
        );

        assertThat(response.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void moderateRejectsPendingAsTargetStatus() {
        assertThatThrownBy(() -> reviewService.moderate(
                BOOKING_PUBLIC_ID, new ReviewModerationRequest(ReviewStatus.PENDING, null)
        )).isInstanceOf(BusinessValidationException.class);
        verify(reviewRepository, never()).findForUpdateByBooking_PublicId(any());
    }

    @Test
    void moderateThrowsWhenReviewNotFound() {
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.moderate(
                BOOKING_PUBLIC_ID, new ReviewModerationRequest(ReviewStatus.REJECTED, "Policy violation")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void moderateRejectsWithoutReason() {
        assertThatThrownBy(() -> reviewService.moderate(
                BOOKING_PUBLIC_ID, new ReviewModerationRequest(ReviewStatus.REJECTED, "  ")
        )).isInstanceOf(BusinessValidationException.class);
        verify(reviewRepository, never()).findForUpdateByBooking_PublicId(any());
    }

    @Test
    void moderateStoresRejectReason() {
        Review review = pendingReview();
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        reviewService.moderate(
                BOOKING_PUBLIC_ID,
                new ReviewModerationRequest(ReviewStatus.REJECTED, "  Nội dung vi phạm chính sách  ")
        );

        assertThat(review.getModerationReason()).isEqualTo("Nội dung vi phạm chính sách");
    }

    @Test
    void moderateClearsReasonWhenPublishingAgain() {
        Review review = pendingReview();
        review.setStatus(ReviewStatus.REJECTED);
        review.setModerationReason("Nội dung cũ");
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        reviewService.moderate(
                BOOKING_PUBLIC_ID, new ReviewModerationRequest(ReviewStatus.PUBLISHED, null)
        );

        assertThat(review.getModerationReason()).isNull();
    }

    @Test
    void replySetsStaffReplyFieldsFromActorStaffProfile() {
        Review review = pendingReview();
        review.setStatus(ReviewStatus.PUBLISHED);
        StaffProfile staffProfile = StaffProfile.builder().employeeCode("EMP-0001").build();
        staffProfile.setId(77L);
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staffProfile));
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        ReviewResponse response = reviewService.reply(
                BOOKING_PUBLIC_ID, new ReviewReplyRequest(" Thank you for staying with us "), STAFF_USER_ID
        );

        assertThat(response.staffReply()).isEqualTo("Thank you for staying with us");
        assertThat(response.staffReplyBy()).isEqualTo(77L);
        assertThat(response.staffRepliedAt()).isEqualTo(java.time.OffsetDateTime.now(FIXED_CLOCK));
    }

    @Test
    void replyAllowsAdminActorWithoutStaffProfile() {
        Review review = pendingReview();
        when(reviewRepository.findForUpdateByBooking_PublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(review));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        ReviewResponse response = reviewService.reply(
                BOOKING_PUBLIC_ID, new ReviewReplyRequest("Thanks"), STAFF_USER_ID
        );

        assertThat(response.staffReply()).isEqualTo("Thanks");
        assertThat(response.staffReplyBy()).isNull();
    }

    private Booking checkedOutBooking(int roomCount) {
        User user = User.builder()
                .publicId("user-" + CUSTOMER_USER_ID)
                .email("customer@example.com")
                .fullName("Nguyen Van A")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(CUSTOMER_USER_ID);
        CustomerProfile customerProfile = CustomerProfile.builder().user(user).build();

        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .status(BookingStatus.CHECKED_OUT)
                .customerProfile(customerProfile)
                .build();
        booking.setId(BOOKING_ID);

        Set<BookingRoom> bookingRooms = new HashSet<>();
        for (int i = 0; i < roomCount; i++) {
            RoomType roomType = RoomType.builder().code("DLX").name("Deluxe").build();
            Room room = Room.builder().roomNumber(roomCount == 1 ? "101" : "10" + (i + 1)).roomType(roomType).build();
            bookingRooms.add(BookingRoom.builder().booking(booking).room(room).build());
        }
        booking.setBookingRooms(bookingRooms);
        return booking;
    }

    private Review pendingReview() {
        Booking booking = Booking.builder().publicId(BOOKING_PUBLIC_ID).build();
        booking.setId(BOOKING_ID);
        Review review = Review.builder()
                .booking(booking)
                .overallRating(5)
                .status(ReviewStatus.PENDING)
                .build();
        review.setId(500L);
        return review;
    }

    private Review publishedReview() {
        User user = User.builder()
                .fullName("Nguyen Van A")
                .email("private@example.com")
                .build();
        CustomerProfile customerProfile = CustomerProfile.builder().user(user).build();
        RoomType roomType = RoomType.builder().name("Deluxe").code("DLX").build();
        return Review.builder()
                .customerProfile(customerProfile)
                .roomType(roomType)
                .overallRating(5)
                .roomRating(4)
                .cleanlinessRating(5)
                .serviceRating(4)
                .title("Great stay")
                .comment("Loved it")
                .staffReply("Thank you")
                .status(ReviewStatus.PUBLISHED)
                .build();
    }
}
