package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.repositories.AuthTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.example.hotelmanagement.entity.enums.AuthTokenType.EMAIL_VERIFICATION;
import static com.example.hotelmanagement.entity.enums.AuthTokenType.PASSWORD_RESET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-19T08:00:00Z"),
        ZoneOffset.UTC
    );
    private static final OffsetDateTime NOW = OffsetDateTime.now(FIXED_CLOCK);

    @Mock
    private AuthTokenRepository authTokenRepository;

    private AuthTokenService authTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties(
            5,
            Duration.ofMinutes(15),
            Duration.ofHours(24),
            Duration.ofMinutes(1),
            Duration.ofMinutes(30),
            "http://localhost:3000/auth/verify-email",
            "http://localhost:3000/auth/reset-password",
            "http://localhost:3000/auth/staff-invitation"
        );
        authTokenService = new AuthTokenService(authTokenRepository, authProperties, FIXED_CLOCK);
        user = User.builder()
            .publicId("public-id")
            .email("guest@example.com")
            .fullName("Guest")
            .build();
    }

    @Test
    void createTokenStoresHashAndExpiresActiveTokens() {
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthTokenService.IssuedAuthToken issuedToken = authTokenService.createToken(
            user,
            EMAIL_VERIFICATION,
            " 127.0.0.1 "
        );

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).expireActiveTokens(user, EMAIL_VERIFICATION, NOW);
        verify(authTokenRepository).save(tokenCaptor.capture());
        AuthToken savedToken = tokenCaptor.getValue();

        assertThat(issuedToken.value()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(issuedToken.expiresAt()).isEqualTo(NOW.plusHours(24));
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(issuedToken.value());
        assertThat(savedToken.getRequestedIp()).isEqualTo("127.0.0.1");
        assertThat(savedToken.getExpiresAt()).isEqualTo(NOW.plusHours(24));
    }

    @Test
    void createResendEmailVerificationTokenSkipsRecentRequests() {
        when(authTokenRepository.existsByUserAndTokenTypeAndCreatedAtAfter(
            user,
            EMAIL_VERIFICATION,
            NOW.minusMinutes(1)
        )).thenReturn(true);

        Optional<AuthTokenService.IssuedAuthToken> issuedToken = authTokenService
            .createResendEmailVerificationToken(user, null);

        assertThat(issuedToken).isEmpty();
    }

    @Test
    void validateTokenReturnsActiveTokenForExpectedType() {
        TokenFixture token = createPersistedToken(EMAIL_VERIFICATION, NOW.plusHours(1), null);
        when(authTokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token.authToken()));

        AuthToken validatedToken = authTokenService.validateToken(token.rawToken(), EMAIL_VERIFICATION);

        assertThat(validatedToken).isSameAs(token.authToken());
    }

    @Test
    void consumeTokenMarksTokenUsed() {
        TokenFixture token = createPersistedToken(PASSWORD_RESET, NOW.plusMinutes(10), null);
        when(authTokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token.authToken()));

        AuthToken consumedToken = authTokenService.consumeToken(token.rawToken(), PASSWORD_RESET);

        assertThat(consumedToken.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void validateTokenRejectsWrongType() {
        TokenFixture token = createPersistedToken(EMAIL_VERIFICATION, NOW.plusHours(1), null);
        when(authTokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token.authToken()));

        assertThatThrownBy(() -> authTokenService.validateToken(token.rawToken(), PASSWORD_RESET))
            .isInstanceOfSatisfying(AuthException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    void validateTokenRejectsExpiredToken() {
        TokenFixture token = createPersistedToken(PASSWORD_RESET, NOW.minusSeconds(1), null);
        when(authTokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token.authToken()));

        assertThatThrownBy(() -> authTokenService.validateToken(token.rawToken(), PASSWORD_RESET))
            .isInstanceOfSatisfying(AuthException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE)
            );
    }

    @Test
    void validateTokenRejectsUsedToken() {
        TokenFixture token = createPersistedToken(PASSWORD_RESET, NOW.plusMinutes(10), NOW.minusMinutes(1));
        when(authTokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token.authToken()));

        assertThatThrownBy(() -> authTokenService.validateToken(token.rawToken(), PASSWORD_RESET))
            .isInstanceOfSatisfying(AuthException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE)
            );
    }

    private TokenFixture createPersistedToken(
        AuthTokenType tokenType,
        OffsetDateTime expiresAt,
        OffsetDateTime usedAt
    ) {
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthTokenService.IssuedAuthToken issuedToken = authTokenService.createToken(user, tokenType, null);

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());
        AuthToken authToken = tokenCaptor.getValue();
        authToken.setExpiresAt(expiresAt);
        authToken.setUsedAt(usedAt);
        return new TokenFixture(authToken, issuedToken.value());
    }

    private record TokenFixture(AuthToken authToken, String rawToken) {

        private String getTokenHash() {
            return authToken.getTokenHash();
        }
    }
}
