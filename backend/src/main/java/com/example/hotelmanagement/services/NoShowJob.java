package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.repositories.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Marks confirmed arrivals as no-shows after every reserved room's check-in date has passed. */
@Component
@ConditionalOnProperty(name = "hotel.jobs.no-show-enabled", havingValue = "true", matchIfMissing = true)
public class NoShowJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowJob.class);

    private final BookingRepository bookingRepository;
    private final BookingStateMachineService bookingStateMachineService;
    private final NoShowPenaltyCalculator noShowPenaltyCalculator;
    private final Clock clock;

    public NoShowJob(
            BookingRepository bookingRepository,
            BookingStateMachineService bookingStateMachineService,
            NoShowPenaltyCalculator noShowPenaltyCalculator,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingStateMachineService = bookingStateMachineService;
        this.noShowPenaltyCalculator = noShowPenaltyCalculator;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${hotel.jobs.no-show-delay:1h}').toMillis()}"
    )
    public void markNoShows() {
        LocalDate today = LocalDate.now(clock);
        bookingRepository.findConfirmedBookingsEligibleForNoShow(
                        BookingStatus.CONFIRMED,
                        BookingRoomStatus.RESERVED,
                        today
                )
                .forEach(booking -> {
                    try {
                        NoShowPenaltyCalculator.NoShowPenaltyCalculation calculation = noShowPenaltyCalculator
                                .calculate(booking);
                        bookingStateMachineService.markNoShow(booking.getPublicId(), calculation.metadataJson());
                    } catch (RuntimeException exception) {
                        log.error(
                                "Failed to mark booking as no-show bookingPublicId={}",
                                booking.getPublicId(),
                                exception
                        );
                    }
                });
    }
}
