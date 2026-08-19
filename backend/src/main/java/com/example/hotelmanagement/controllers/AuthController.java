package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.auth.AuthResponse;
import com.example.hotelmanagement.dto.auth.AuthMessageResponse;
import com.example.hotelmanagement.dto.auth.EmailVerificationRequest;
import com.example.hotelmanagement.dto.auth.LoginRequest;
import com.example.hotelmanagement.dto.auth.LogoutRequest;
import com.example.hotelmanagement.dto.auth.OAuthGoogleRequest;
import com.example.hotelmanagement.dto.auth.PasswordResetConfirmRequest;
import com.example.hotelmanagement.dto.auth.PasswordResetEmailRequest;
import com.example.hotelmanagement.dto.auth.RefreshTokenRequest;
import com.example.hotelmanagement.dto.auth.RegisterRequest;
import com.example.hotelmanagement.common.error.ApiErrorResponse;
import com.example.hotelmanagement.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, refresh token, logout and Google OAuth stub")
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "Register a customer account",
        description = "Creates a new user with CUSTOMER role, hashes the password with BCrypt cost 12, "
            + "and returns JWT access/refresh tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created",
            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
        summary = "Verify registered email",
        description = "Accepts a one-time email verification token from the request body."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified",
            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body or token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "410", description = "Token expired or already used",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/verify-email")
    public AuthMessageResponse verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        return authService.verifyEmail(request);
    }

    @Operation(
        summary = "Request password reset",
        description = "Accepts an account email and sends a one-time reset token when the account is eligible."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Password reset request accepted",
            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<AuthMessageResponse> requestPasswordReset(
        @Valid @RequestBody PasswordResetEmailRequest request
    ) {
        return ResponseEntity.accepted().body(authService.requestPasswordReset(request));
    }

    @Operation(
        summary = "Confirm password reset",
        description = "Accepts a one-time password reset token and a new password from the request body."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset completed",
            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body or token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "410", description = "Token expired or already used",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/password-reset/confirm")
    public AuthMessageResponse resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return authService.resetPassword(request);
    }

    @Operation(
        summary = "Login with email and password",
        description = "Validates credentials, resets failed login count on success, stores refresh token state in DB, "
            + "and locks the account temporarily after 5 failed attempts."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login success",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "423", description = "Account is temporarily locked",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
        summary = "Refresh JWT tokens",
        description = "Validates the refresh token JWT, checks the DB token state, revokes the old refresh token, "
            + "and issues a new access/refresh token pair."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New token pair issued",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token is invalid or revoked",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(
        summary = "Logout",
        description = "Parses the refresh token and removes its Redis key so it cannot be used again."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Refresh token revoked"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token is invalid",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(
        summary = "Google OAuth login stub",
        description = "Development stub for Google social login. It does not call Google yet; it accepts provider "
            + "identity data, links or creates a user, and returns JWT tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OAuth login success",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Linked account is suspended or deactivated",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/oauth/google")
    public AuthResponse loginWithGoogleStub(@Valid @RequestBody OAuthGoogleRequest request) {
        return authService.loginWithGoogleStub(request);
    }
}
