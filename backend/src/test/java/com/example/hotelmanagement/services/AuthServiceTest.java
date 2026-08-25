package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.auth.AuthMessageResponse;
import com.example.hotelmanagement.dto.auth.AuthResponse;
import com.example.hotelmanagement.dto.auth.EmailVerificationRequest;
import com.example.hotelmanagement.dto.auth.LoginRequest;
import com.example.hotelmanagement.dto.auth.OAuthGoogleRequest;
import com.example.hotelmanagement.dto.auth.RegisterRequest;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import com.example.hotelmanagement.repositories.UserSocialAccountRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.example.hotelmanagement.entity.enums.AuthTokenType.EMAIL_VERIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-19T08:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private EmailService emailService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(
            userRepository,
            roleRepository,
            userSocialAccountRepository,
            customerProfileRepository,
            passwordEncoder,
            jwtService,
            refreshTokenService,
            authTokenService,
            emailService,
            new AuthProperties(
                5,
                Duration.ofMinutes(15),
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                "http://localhost:3000/auth/verify-email",
                "http://localhost:3000/auth/reset-password"
            ),
            FIXED_CLOCK
        );
        customerRole = Role.builder().code("CUSTOMER").name("Customer").build();
        stubIssuedTokens();
    }

    @Test
    void registerHashesPasswordAndAssignsCustomerRole() {
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("guest@example.com")).thenReturn(false);
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authTokenService.createToken(any(User.class), any(), any()))
            .thenReturn(new AuthTokenService.IssuedAuthToken("verification-token", OffsetDateTime.now(FIXED_CLOCK).plusHours(24)));

        AuthMessageResponse response = authService.register(new RegisterRequest(
            "Guest@Example.com",
            "very-secure-password",
            "Nguyen Van A",
            "+84901234567"
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("guest@example.com");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("very-secure-password");
        assertThat(passwordEncoder.matches("very-secure-password", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(savedUser.getUserRoles()).hasSize(1);
        assertThat(response.message()).isNotBlank();
        verify(authTokenService).createToken(savedUser, EMAIL_VERIFICATION, null);
        verify(emailService).sendVerificationEmail("guest@example.com", "Nguyen Van A", "verification-token");
    }

    @Test
    void verifyEmailQueuesAccountActivatedMessageOnlyOnFirstVerification() {
        User user = User.builder()
                .publicId("public-id")
                .email("guest@example.com")
                .fullName("Guest")
                .status(UserStatus.PENDING_VERIFICATION)
                .build();
        AuthToken token = AuthToken.builder()
                .user(user)
                .build();
        when(authTokenService.findTokenForVerification("verification-token"))
                .thenReturn(Optional.of(token));

        AuthMessageResponse response = authService.verifyEmail(
                new EmailVerificationRequest("verification-token")
        );

        assertThat(response.message()).isEqualTo("Xác thực email thành công");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        verify(emailService).sendAccountActivatedEmail(user);
    }

    @Test
    void loginLocksUserAfterMaxFailedAttempts() {
        User user = User.builder()
            .publicId("public-id")
            .email("guest@example.com")
            .fullName("Guest")
            .passwordHash(passwordEncoder.encode("correct-password"))
            .emailVerifiedAt(OffsetDateTime.now(FIXED_CLOCK))
            .status(UserStatus.ACTIVE)
            .failedLoginCount(4)
            .build();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("guest@example.com"))
            .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(
            "guest@example.com",
            "wrong-password"
        )))
            .isInstanceOf(AuthException.class)
            .hasMessage("Email hoặc mật khẩu không đúng");

        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(15));
    }

    @Test
    void refreshRotatesRefreshToken() {
        Claims claims = mock(Claims.class);
        User user = User.builder()
            .publicId("public-id")
            .email("guest@example.com")
            .fullName("Guest")
            .emailVerifiedAt(OffsetDateTime.now(FIXED_CLOCK))
            .status(UserStatus.ACTIVE)
            .failedLoginCount(0)
            .build();
        user.getUserRoles().add(com.example.hotelmanagement.entity.UserRole.builder()
            .user(user)
            .role(customerRole)
            .assignedAt(OffsetDateTime.now(FIXED_CLOCK))
            .build());

        when(claims.getSubject()).thenReturn("public-id");
        when(claims.getId()).thenReturn("old-refresh-jti");
        when(jwtService.parseRefreshToken("old-refresh-token")).thenReturn(claims);
        when(userRepository.findByPublicIdAndDeletedAtIsNull("public-id")).thenReturn(Optional.of(user));

        AuthResponse response = authService.refresh("old-refresh-token");

        verify(refreshTokenService).validateRefreshToken("old-refresh-jti", "public-id");
        verify(refreshTokenService).revokeRefreshToken("old-refresh-jti", "refresh-jti");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void googleStubCreatesActiveCustomerUser() {
        when(userSocialAccountRepository.findByProviderAndProviderUserId(any(), any()))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("oauth@example.com"))
            .thenReturn(Optional.empty());
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.loginWithGoogleStub(new OAuthGoogleRequest(
            "google-user-1",
            "OAuth@Example.com",
            "OAuth Guest"
        ));

        assertThat(response.user().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.user().emailVerifiedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(response.user().roles()).containsExactly("CUSTOMER");
    }

    private void stubIssuedTokens() {
        lenient().when(jwtService.generateAccessToken(any(), any(), any()))
            .thenReturn(new JwtService.GeneratedToken("access-token", "access-jti", Instant.now(FIXED_CLOCK).plusSeconds(3600)));
        lenient().when(jwtService.generateRefreshToken(any()))
            .thenReturn(new JwtService.GeneratedToken("refresh-token", "refresh-jti", Instant.now(FIXED_CLOCK).plusSeconds(604800)));
        lenient().when(jwtService.getAccessTokenTtlSeconds()).thenReturn(3600L);
        lenient().when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(604800L);
    }
}
