package com.example.hotelmanagement.services;

import com.example.hotelmanagement.repositories.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredTokenCleanupJobTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Test
    void deleteExpiredTokensUsesThirtyDayRetentionCutoff() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T02:00:00Z"), ZoneOffset.UTC);
        when(authTokenRepository.deleteExpiredBefore(OffsetDateTime.parse("2026-07-27T02:00:00Z"))).thenReturn(3);

        new ExpiredTokenCleanupJob(authTokenRepository, clock, Duration.ofDays(30)).deleteExpiredTokens();

        verify(authTokenRepository).deleteExpiredBefore(OffsetDateTime.parse("2026-07-27T02:00:00Z"));
    }
}
