package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.auth.AuthMessageResponse;
import com.example.hotelmanagement.dto.auth.AuthResponse;
import com.example.hotelmanagement.dto.auth.EmailVerificationRequest;
import com.example.hotelmanagement.dto.auth.LoginRequest;
import com.example.hotelmanagement.dto.auth.OAuthGoogleRequest;
import com.example.hotelmanagement.dto.auth.PasswordResetConfirmRequest;
import com.example.hotelmanagement.dto.auth.PasswordResetEmailRequest;
import com.example.hotelmanagement.dto.auth.RegisterRequest;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.UserSocialAccount;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import com.example.hotelmanagement.entity.enums.OAuthProvider;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import com.example.hotelmanagement.repositories.UserSocialAccountRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final com.example.hotelmanagement.config.AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public AuthMessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }

        User user = User.builder()
            .publicId(UUID.randomUUID().toString())
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .phone(normalizeBlank(request.phone()))
            .fullName(request.fullName().trim())
            .status(UserStatus.PENDING_VERIFICATION)
            .failedLoginCount(0)
            .build();
        assignCustomerRole(user);

        User savedUser = userRepository.save(user);

        // Create customer profile automatically
        createCustomerProfile(savedUser);

        AuthTokenService.IssuedAuthToken token = authTokenService.createToken(savedUser, AuthTokenType.EMAIL_VERIFICATION, null);
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), token.value());

        return new AuthMessageResponse("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @Transactional
    public AuthMessageResponse verifyEmail(EmailVerificationRequest request) {
        // First, try to find the token - if it's already used, we still want to check user status
        Optional<AuthToken> existingToken = authTokenService.findTokenForVerification(request.token());
        User user;

        if (existingToken.isPresent()) {
            AuthToken token = existingToken.get();
            user = token.getUser();

            // If token is not yet used, consume it and activate user
            if (token.getUsedAt() == null) {
                boolean wasUnverified = user.getEmailVerifiedAt() == null;
                token.setUsedAt(now());
                if (wasUnverified) {
                    user.setEmailVerifiedAt(now());
                }
                if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                    user.setStatus(UserStatus.ACTIVE);
                }
                if (wasUnverified) {
                    emailService.sendAccountActivatedEmail(user);
                }
                return new AuthMessageResponse("Xác thực email thành công");
            }

            // Token was already used - check if user is already active
            if (user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null) {
                // User was already verified - return success instead of error
                return new AuthMessageResponse("Email đã được xác thực trước đó. Bạn có thể đăng nhập ngay.");
            }

            // User is not active for some reason
            if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DEACTIVATED) {
                throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng");
            }

            // Token used but user not active - try to activate
            boolean wasUnverified = user.getEmailVerifiedAt() == null;
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now());
            }
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setStatus(UserStatus.ACTIVE);
            }
            if (wasUnverified) {
                emailService.sendAccountActivatedEmail(user);
            }
            return new AuthMessageResponse("Xác thực email thành công");
        }

        // Token not found at all
        throw new AuthException(HttpStatus.BAD_REQUEST, "Token xác thực không hợp lệ");
    }

    @Transactional
    public AuthMessageResponse requestPasswordReset(PasswordResetEmailRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> optionalUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getStatus() != UserStatus.SUSPENDED && user.getStatus() != UserStatus.DEACTIVATED) {
                AuthTokenService.IssuedAuthToken token = authTokenService.createToken(user, AuthTokenType.PASSWORD_RESET, null);
                emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token.value());
            }
        }
        
        return new AuthMessageResponse("Nếu email hợp lệ, hướng dẫn khôi phục mật khẩu đã được gửi.");
    }

    @Transactional
    public AuthMessageResponse resetPassword(PasswordResetConfirmRequest request) {
        AuthToken authToken = authTokenService.consumeToken(request.token(), AuthTokenType.PASSWORD_RESET);
        User user = authToken.getUser();
        
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        
        return new AuthMessageResponse("Mật khẩu đã được đặt lại thành công");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

        ensureUserCanAuthenticate(user);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user);
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseRefreshToken(refreshToken);
        String userPublicId = claims.getSubject();
        refreshTokenService.validateRefreshToken(claims.getId(), userPublicId);
        User user = userRepository.findByPublicIdAndDeletedAtIsNull(userPublicId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"));
        ensureUserCanAuthenticate(user);
        return issueTokens(user, claims.getId());
    }

    @Transactional
    public void logout(String refreshToken) {
        Claims claims = jwtService.parseRefreshToken(refreshToken);
        refreshTokenService.revokeRefreshToken(claims.getId());
    }

    @Transactional
    public AuthResponse loginWithGoogleStub(OAuthGoogleRequest request) {
        return userSocialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE,
                request.providerUserId()
            )
            .map(UserSocialAccount::getUser)
            .map(this::activateOAuthUserIfAllowed)
            .map(this::issueTokens)
            .orElseGet(() -> createOrLinkGoogleUser(request));
    }

    /**
     * Real Google OAuth login - creates or links user account
     */
    @Transactional
    public AuthResponse loginWithGoogle(String providerUserId, String email, String fullName) {
        // Try to find existing social account
        return userSocialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE,
                providerUserId
            )
            .map(UserSocialAccount::getUser)
            .map(this::activateOAuthUserIfAllowed)
            .map(this::issueTokens)
            .orElseGet(() -> createOrLinkGoogleUser(providerUserId, email, fullName));
    }

    private AuthResponse createOrLinkGoogleUser(String providerUserId, String email, String fullName) {
        String normalizedEmail = normalizeEmail(email);

        // Find existing user by email or create new one
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
            .map(this::activateOAuthUserIfAllowed)
            .orElseGet(() -> createOAuthUserFromOAuth(normalizedEmail, fullName));

        // Check if social account already linked
        if (userSocialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId).isEmpty()) {
            UserSocialAccount socialAccount = UserSocialAccount.builder()
                .user(user)
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .providerEmail(normalizedEmail)
                .rawProfile("{\"provider\":\"google\"}")
                .linkedAt(now())
                .build();
            userSocialAccountRepository.save(socialAccount);
        }

        return issueTokens(user);
    }

    private User createOAuthUserFromOAuth(String email, String fullName) {
        User user = User.builder()
            .publicId(UUID.randomUUID().toString())
            .email(email)
            .emailVerifiedAt(now())
            .fullName(fullName != null ? fullName.trim() : email.split("@")[0])
            .status(UserStatus.ACTIVE)
            .failedLoginCount(0)
            .build();
        assignCustomerRole(user);
        User savedUser = userRepository.save(user);
        createCustomerProfile(savedUser);
        return savedUser;
    }

    // Stub method for development/testing
    private AuthResponse createOrLinkGoogleUser(OAuthGoogleRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
            .map(this::activateOAuthUserIfAllowed)
            .orElseGet(() -> createOAuthUserFromOAuth(email, request.fullName()));

        UserSocialAccount socialAccount = UserSocialAccount.builder()
            .user(user)
            .provider(OAuthProvider.GOOGLE)
            .providerUserId(request.providerUserId())
            .providerEmail(email)
            .rawProfile("{\"stub\":true}")
            .linkedAt(now())
            .build();
        userSocialAccountRepository.save(socialAccount);
        return issueTokens(user);
    }

    private User createOAuthUser(OAuthGoogleRequest request, String email) {
        User user = User.builder()
            .publicId(UUID.randomUUID().toString())
            .email(email)
            .emailVerifiedAt(now())
            .fullName(request.fullName().trim())
            .status(UserStatus.ACTIVE)
            .failedLoginCount(0)
            .build();
        assignCustomerRole(user);
        User savedUser = userRepository.save(user);
        createCustomerProfile(savedUser);
        return savedUser;
    }

    private User activateOAuthUserIfAllowed(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng");
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        return user;
    }

    private void ensureUserCanAuthenticate(User user) {
        OffsetDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now())) {
            throw new AuthException(HttpStatus.LOCKED, "Tài khoản đang bị khóa tạm thời");
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION || user.getEmailVerifiedAt() == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Vui lòng xác thực email trước khi đăng nhập");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng");
        }
    }

    private void registerFailedLogin(User user) {
        int failedLoginCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failedLoginCount);
        if (failedLoginCount >= authProperties.maxFailedLoginAttempts()) {
            user.setLockedUntil(now().plus(authProperties.lockDuration()));
        }
    }

    private AuthResponse issueTokens(User user) {
        return issueTokens(user, null);
    }

    private AuthResponse issueTokens(User user, String rotatedFromJwtId) {
        Set<String> roles = collectRoles(user);
        Set<String> permissions = collectPermissions(user);
        JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(user, roles, permissions);
        JwtService.GeneratedToken refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.storeRefreshToken(refreshToken.jwtId(), user, refreshToken.expiresAt());
        if (rotatedFromJwtId != null) {
            refreshTokenService.revokeRefreshToken(rotatedFromJwtId, refreshToken.jwtId());
        }

        return new AuthResponse(
            "Bearer",
            accessToken.value(),
            jwtService.getAccessTokenTtlSeconds(),
            refreshToken.value(),
            jwtService.getRefreshTokenTtlSeconds(),
            new AuthResponse.UserSummary(
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                roles,
                permissions
            )
        );
    }

    private void assignCustomerRole(User user) {
        Role customerRole = roleRepository.findByCode(CUSTOMER_ROLE_CODE)
            .orElseThrow(() -> new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Role CUSTOMER chưa được seed"));
        UserRole userRole = UserRole.builder()
            .user(user)
            .role(customerRole)
            .assignedAt(now())
            .build();
        user.getUserRoles().add(userRole);
    }

    private void createCustomerProfile(User user) {
        CustomerProfile profile = CustomerProfile.builder()
            .user(user)
            .build();
        customerProfileRepository.save(profile);
    }

    private Set<String> collectRoles(User user) {
        Set<String> roles = new LinkedHashSet<>();
        user.getUserRoles().forEach(userRole -> roles.add(userRole.getRole().getCode()));
        return Set.copyOf(roles);
    }

    private Set<String> collectPermissions(User user) {
        Set<String> permissions = new LinkedHashSet<>();
        user.getUserRoles().forEach(userRole ->
            userRole.getRole().getRolePermissions().forEach(rolePermission ->
                permissions.add(rolePermission.getPermission().getCode())
            )
        );
        return Set.copyOf(permissions);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
