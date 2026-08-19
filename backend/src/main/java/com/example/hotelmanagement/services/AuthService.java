package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.auth.AuthResponse;
import com.example.hotelmanagement.dto.auth.LoginRequest;
import com.example.hotelmanagement.dto.auth.OAuthGoogleRequest;
import com.example.hotelmanagement.dto.auth.RegisterRequest;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.UserSocialAccount;
import com.example.hotelmanagement.entity.enums.OAuthProvider;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import com.example.hotelmanagement.repositories.UserSocialAccountRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final com.example.hotelmanagement.config.AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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
        return issueTokens(savedUser);
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

    private AuthResponse createOrLinkGoogleUser(OAuthGoogleRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
            .map(this::activateOAuthUserIfAllowed)
            .orElseGet(() -> createOAuthUser(request, email));

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
        return userRepository.save(user);
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
