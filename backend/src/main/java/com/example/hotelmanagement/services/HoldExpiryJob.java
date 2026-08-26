package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.repositories.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(
        name = "hotel.jobs.hold-expiry-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final BookingStateMachineService bookingStateMachineService;
    private final Clock clock;

    public HoldExpiryJob(
            BookingRepository bookingRepository,
            BookingStateMachineService bookingStateMachineService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingStateMachineService = bookingStateMachineService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${hotel.jobs.hold-expiry-delay:1m}').toMillis()}"
    )
    public void expirePendingHolds() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        bookingRepository.findPendingBookingsPastHold(BookingStatus.PENDING, now)
                .forEach(booking -> {
                    try {
                        bookingStateMachineService.expire(booking.getPublicId());
                    } catch (RuntimeException exception) {
                        log.error(
                                "Failed to expire booking hold bookingPublicId={} holdExpiresAt={}",
                                booking.getPublicId(),
                                booking.getHoldExpiresAt(),
                                exception
                        );
                    }
                });
    }
}
