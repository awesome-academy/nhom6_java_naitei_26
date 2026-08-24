package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.auth.AuthResponse;
import com.example.hotelmanagement.dto.auth.OAuthCallbackRequest;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.services.AuthService;
import com.example.hotelmanagement.services.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/oauth/google")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Google OAuth", description = "Initiate and complete the Google OAuth login flow.")
public class OAuthController {

    private final OAuthService oauthService;
    private final AuthService authService;

    @Value("${app.oauth.google.redirect-uri:http://localhost:8080/api/auth/oauth/google/callback}")
    private String redirectUri;

    @Value("${app.oauth.google.frontend-callback-url:http://localhost:3000/auth/google/callback}")
    private String frontendCallbackUrl;

    /**
     * Step 1: Initiate Google OAuth flow
     * Returns the Google authorization URL for frontend to redirect
     */
    @Operation(summary = "Authorize")
    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> authorize(
        @RequestParam(defaultValue = "") String returnUrl
    ) {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Google OAuth chưa được cấu hình. Vui lòng liên hệ quản trị viên."));
        }

        String state = UUID.randomUUID().toString();
        String authorizationUrl = oauthService.buildAuthorizationUrl(state, redirectUri);

        log.info("OAuth authorization requested, state: {}, redirectUri: {}", state, redirectUri);

        return ResponseEntity.ok(Map.of(
            "authorizationUrl", authorizationUrl,
            "state", state
        ));
    }

    /**
     * Step 2: Handle OAuth callback from Google
     * This endpoint is called by the frontend after Google redirects back
     */
    @Operation(summary = "Callback")
    @PostMapping("/callback")
    public ResponseEntity<AuthResponse> callback(@RequestBody OAuthCallbackRequest request) {
        return handleOAuthCallback(request);
    }

    /**
     * GET callback for Google redirect
     * Google redirects here with ?code=xxx
     * We'll redirect to frontend callback page with the result
     */
    @Operation(summary = "Callback Get")
    @GetMapping("/callback")
    public void callbackGet(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String error_description,
        HttpServletResponse response
    ) throws IOException {
        if (error != null && !error.isBlank()) {
            String errorMsg = error_description != null ? error_description : error;
            String redirectUrl = frontendCallbackUrl + "?error=" + java.net.URLEncoder.encode(errorMsg, java.nio.charset.StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        if (code == null || code.isBlank()) {
            response.sendRedirect(frontendCallbackUrl + "?error=missing_code");
            return;
        }

        try {
            // Exchange code for token
            OAuthService.GoogleTokenResponse tokenResponse = oauthService.exchangeCodeForToken(code);
            OAuthService.GoogleUserInfo userInfo = oauthService.getUserInfo(tokenResponse.accessToken());

            if (!userInfo.emailVerified()) {
                response.sendRedirect(frontendCallbackUrl + "?error=unverified_email");
                return;
            }

            log.info("Google OAuth successful for email: {}", userInfo.email());

            // Create or link user
            AuthResponse authResponse = authService.loginWithGoogle(
                userInfo.id(),
                userInfo.email(),
                userInfo.name() != null ? userInfo.name() : userInfo.email()
            );

            // Create JSON with all auth data
            Map<String, Object> authData = Map.of(
                "accessToken", authResponse.accessToken(),
                "refreshToken", authResponse.refreshToken(),
                "user", Map.of(
                    "publicId", authResponse.user().publicId(),
                    "email", authResponse.user().email(),
                    "fullName", authResponse.user().fullName(),
                    "status", authResponse.user().status(),
                    "emailVerifiedAt", authResponse.user().emailVerifiedAt() != null ? authResponse.user().emailVerifiedAt().toString() : null,
                    "roles", authResponse.user().roles()
                )
            );

            // Encode as JSON with Base64 (URL-safe)
            String jsonData = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(authData);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String redirectUrl = frontendCallbackUrl + "?data=" + encoded;
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("OAuth callback failed: {}", e.getMessage(), e);
            response.sendRedirect(frontendCallbackUrl + "?error=oauth_failed");
        }
    }

    private ResponseEntity<AuthResponse> handleOAuthCallback(OAuthCallbackRequest request) {
        if (!oauthService.isConfigured()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google OAuth chưa được cấu hình");
        }

        if (request.error() != null && !request.error().isBlank()) {
            log.warn("OAuth error from Google: {} - {}", request.error(), request.errorDescription());
            throw new AuthException(HttpStatus.BAD_REQUEST,
                "Đăng nhập Google bị hủy hoặc thất bại: " + request.errorDescription());
        }

        try {
            // Exchange code for token
            OAuthService.GoogleTokenResponse tokenResponse = oauthService.exchangeCodeForToken(request.code());

            // Get user info from Google
            OAuthService.GoogleUserInfo userInfo = oauthService.getUserInfo(tokenResponse.accessToken());

            if (!userInfo.emailVerified()) {
                throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Email Google chưa được xác thực");
            }

            log.info("Google OAuth successful for email: {}", userInfo.email());

            // Create or link user and return auth response
            AuthResponse response = authService.loginWithGoogle(
                userInfo.id(),
                userInfo.email(),
                userInfo.name() != null ? userInfo.name() : userInfo.email()
            );

            return ResponseEntity.ok(response);

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OAuth callback failed: {}", e.getMessage(), e);
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đăng nhập Google thất bại. Vui lòng thử lại.");
        }
    }

    /**
     * Health check to verify OAuth is configured
     */
    @Operation(summary = "Status")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "configured", oauthService.isConfigured(),
            "provider", "google"
        ));
    }
}
