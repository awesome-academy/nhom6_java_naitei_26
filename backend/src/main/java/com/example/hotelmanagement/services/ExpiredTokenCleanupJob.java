package com.example.hotelmanagement.services;

import com.example.hotelmanagement.repositories.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Permanently removes expired authentication tokens after the documented 30-day retention period. */
@Component
@ConditionalOnProperty(name = "hotel.jobs.expired-token-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanupJob.class);

    private final AuthTokenRepository authTokenRepository;
    private final Clock clock;
    private final Duration retention;

    public ExpiredTokenCleanupJob(
            AuthTokenRepository authTokenRepository,
            Clock clock,
            @Value("${hotel.jobs.expired-token-retention:30d}") Duration retention
    ) {
        this.authTokenRepository = authTokenRepository;
        this.clock = clock;
        this.retention = retention;
    }

    @Transactional
    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${hotel.jobs.expired-token-cleanup-delay:1h}').toMillis()}"
    )
    public void deleteExpiredTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(retention);
        int deletedCount = authTokenRepository.deleteExpiredBefore(cutoff);
        log.info("Deleted expired authentication tokens deletedCount={} cutoff={}", deletedCount, cutoff);
    }
}
