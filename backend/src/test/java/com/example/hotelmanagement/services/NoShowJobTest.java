package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.repositories.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoShowJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-26T02:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingStateMachineService bookingStateMachineService;
    @Mock
    private NoShowPenaltyCalculator noShowPenaltyCalculator;

    @Test
    void markNoShowsPersistsCalculatedPenaltyMetadataWithStatusTransition() {
        Booking booking = Booking.builder().publicId("booking-public-id").build();
        NoShowPenaltyCalculator.NoShowPenaltyCalculation calculation =
                new NoShowPenaltyCalculator.NoShowPenaltyCalculation(
                        new BigDecimal("600.00"),
                        new BigDecimal("100.00"),
                        "{\"penalty_amount\":600.00}"
                );
        when(bookingRepository.findConfirmedBookingsEligibleForNoShow(
                BookingStatus.CONFIRMED,
                BookingRoomStatus.RESERVED,
                LocalDate.of(2026, 8, 26)
        )).thenReturn(List.of(booking));
        when(noShowPenaltyCalculator.calculate(booking)).thenReturn(calculation);

        new NoShowJob(bookingRepository, bookingStateMachineService, noShowPenaltyCalculator, FIXED_CLOCK)
                .markNoShows();

        verify(noShowPenaltyCalculator).calculate(booking);
        verify(bookingStateMachineService).markNoShow("booking-public-id", calculation.metadataJson());
    }

    @Test
    void markNoShowsContinuesWhenOneBookingCannotBeProcessed() {
        Booking invalidBooking = Booking.builder().publicId("invalid-booking").build();
        Booking validBooking = Booking.builder().publicId("valid-booking").build();
        NoShowPenaltyCalculator.NoShowPenaltyCalculation calculation =
                new NoShowPenaltyCalculator.NoShowPenaltyCalculation(BigDecimal.ZERO, BigDecimal.ZERO, "{}");
        when(bookingRepository.findConfirmedBookingsEligibleForNoShow(any(), any(), any()))
                .thenReturn(List.of(invalidBooking, validBooking));
        when(noShowPenaltyCalculator.calculate(invalidBooking)).thenThrow(new IllegalStateException("bad snapshot"));
        when(noShowPenaltyCalculator.calculate(validBooking)).thenReturn(calculation);

        new NoShowJob(bookingRepository, bookingStateMachineService, noShowPenaltyCalculator, FIXED_CLOCK)
                .markNoShows();

        verify(bookingStateMachineService).markNoShow("valid-booking", "{}");
    }
}
