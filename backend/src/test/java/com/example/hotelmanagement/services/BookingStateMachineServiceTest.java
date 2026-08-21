package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingStateMachineServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final Long CUSTOMER_USER_ID = 1L;
    private static final Long STAFF_USER_ID = 99L;
    private static final String BOOKING_PUBLIC_ID = "booking-public-id";

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private InvoiceService invoiceService;

    private BookingStateMachineService service;

    @BeforeEach
    void setUp() {
        service = new BookingStateMachineService(
                bookingRepository,
                staffProfileRepository,
                invoiceService,
                FIXED_CLOCK
        );
    }

    private void stubSaveAndFlushReturnsArgument() {
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- checkIn (BR-010: only CONFIRMED -> CHECKED_IN) ----

    @Test
    void checkInTransitionsConfirmedBookingToCheckedIn() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.CONFIRMED, true);
        StaffProfile staff = createStaffProfile();
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));

        BookingResponse response = service.checkIn(BOOKING_PUBLIC_ID, STAFF_USER_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getCheckedInAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(booking.getCheckedInBy()).isSameAs(staff);
        assertLastHistoryEntry(booking, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, STAFF_USER_ID);
    }

    @Test
    void checkInRejectsBookingNotConfirmed() {
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.checkIn(BOOKING_PUBLIC_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsWhenActorHasNoStaffProfile() {
        Booking booking = createBooking(BookingStatus.CONFIRMED, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(BOOKING_PUBLIC_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInThrowsWhenBookingNotFound() {
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(BOOKING_PUBLIC_ID, STAFF_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- checkOut (BR-011: only CHECKED_IN -> CHECKED_OUT) ----

    @Test
    void checkOutTransitionsCheckedInBookingToCheckedOut() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.CHECKED_IN, true);
        StaffProfile staff = createStaffProfile();
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));

        BookingResponse response = service.checkOut(BOOKING_PUBLIC_ID, STAFF_USER_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(booking.getCheckedOutAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(booking.getCheckedOutBy()).isSameAs(staff);
        assertLastHistoryEntry(booking, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT, STAFF_USER_ID);
        verify(invoiceService).createDraftForCheckout(booking);
    }

    @Test
    void checkOutRejectsBookingNotCheckedIn() {
        Booking booking = createBooking(BookingStatus.CONFIRMED, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.checkOut(BOOKING_PUBLIC_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(invoiceService, never()).createDraftForCheckout(any());
    }

    // ---- cancel (BR-005: owner or cancel_any) ----

    @Test
    void cancelAllowsBookingOwnerWithoutCheckingAuthorities() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = service.cancel(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID, "Change of plans");

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(booking.getCancelledBy()).isEqualTo(CUSTOMER_USER_ID);
        assertThat(booking.getCancellationReason()).isEqualTo("Change of plans");
        assertLastHistoryEntry(booking, BookingStatus.PENDING, BookingStatus.CANCELLED, CUSTOMER_USER_ID);
    }

    @Test
    void cancelRejectsNonOwnerWithoutCancelAnyAuthority() {
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        authenticateAs("booking:cancel_own");

        assertThatThrownBy(() -> service.cancel(BOOKING_PUBLIC_ID, STAFF_USER_ID, "Not my booking"))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelAllowsNonOwnerWithCancelAnyAuthority() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        authenticateAs("booking:cancel_any");

        BookingResponse response = service.cancel(BOOKING_PUBLIC_ID, STAFF_USER_ID, "No-show expected");

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isEqualTo(STAFF_USER_ID);
    }

    @Test
    void cancelRejectsWhenBookingHasNoCustomerAndActorLacksCancelAny() {
        Booking booking = createBooking(BookingStatus.PENDING, false);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        authenticateAs("booking:cancel_own");

        assertThatThrownBy(() -> service.cancel(BOOKING_PUBLIC_ID, STAFF_USER_ID, "walk-in cleanup"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelRejectsAlreadyTerminalBooking() {
        Booking booking = createBooking(BookingStatus.CHECKED_OUT, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID, "too late"))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ---- system-triggered transitions (confirm / no-show / expire) ----

    @Test
    void confirmTransitionsPendingBookingAndUsesSystemActor() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = service.confirm(BOOKING_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        var history = booking.getStatusHistory().iterator().next();
        assertThat(history.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(history.getChangedBy()).isNull();
        assertThat(history.getSource()).isEqualTo(StatusChangeSource.PAYMENT_CALLBACK);
    }

    @Test
    void markNoShowTransitionsConfirmedBooking() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.CONFIRMED, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = service.markNoShow(BOOKING_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.NO_SHOW);
        assertLastHistorySource(booking, StatusChangeSource.NO_SHOW_JOB);
    }

    @Test
    void expireTransitionsPendingBooking() {
        stubSaveAndFlushReturnsArgument();
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = service.expire(BOOKING_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.EXPIRED);
        assertLastHistorySource(booking, StatusChangeSource.HOLD_EXPIRY_JOB);
    }

    @Test
    void confirmRejectsBookingNotPending() {
        Booking booking = createBooking(BookingStatus.CONFIRMED, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirm(BOOKING_PUBLIC_ID))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void markNoShowRejectsBookingNotConfirmed() {
        Booking booking = createBooking(BookingStatus.PENDING, true);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.markNoShow(BOOKING_PUBLIC_ID))
                .isInstanceOf(BusinessValidationException.class);
    }

    private void assertLastHistoryEntry(
            Booking booking,
            BookingStatus expectedFrom,
            BookingStatus expectedTo,
            Long expectedChangedBy
    ) {
        assertThat(booking.getStatusHistory()).hasSize(1);
        var history = booking.getStatusHistory().iterator().next();
        assertThat(history.getFromStatus()).isEqualTo(expectedFrom);
        assertThat(history.getToStatus()).isEqualTo(expectedTo);
        assertThat(history.getActorType()).isEqualTo(ActorType.USER);
        assertThat(history.getChangedBy()).isEqualTo(expectedChangedBy);
        assertThat(history.getSource()).isEqualTo(StatusChangeSource.MANUAL);
    }

    private void assertLastHistorySource(Booking booking, StatusChangeSource expectedSource) {
        var history = booking.getStatusHistory().iterator().next();
        assertThat(history.getSource()).isEqualTo(expectedSource);
    }

    private void authenticateAs(String... authorities) {
        List<GrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        Authentication authentication = new TestingAuthenticationToken(
                "staff@example.com", "N/A", grantedAuthorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Booking createBooking(BookingStatus status, boolean withCustomer) {
        BookingSource source = BookingSource.builder().code("WEBSITE").name("Website").build();
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000123")
                .source(source)
                .status(status)
                .contactName("Guest")
                .build();
        if (withCustomer) {
            User user = User.builder()
                    .publicId("customer-public-id")
                    .email("customer@example.com")
                    .fullName("Nguyen Van A")
                    .status(UserStatus.ACTIVE)
                    .failedLoginCount(0)
                    .build();
            user.setId(CUSTOMER_USER_ID);
            CustomerProfile customerProfile = CustomerProfile.builder().user(user).build();
            booking.setCustomerProfile(customerProfile);
        }
        return booking;
    }

    private StaffProfile createStaffProfile() {
        User user = User.builder()
                .publicId("staff-public-id")
                .email("staff@example.com")
                .fullName("Le Van B")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(STAFF_USER_ID);
        return StaffProfile.builder().user(user).employeeCode("EMP-0001").build();
    }
}
