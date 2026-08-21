package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.OAuthProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final OAuthProperties oauthProperties;

    /**
     * Build Google OAuth authorization URL
     */
    public String buildAuthorizationUrl(String state) {
        return buildAuthorizationUrl(state, oauthProperties.getRedirectUri());
    }

    /**
     * Build Google OAuth authorization URL with custom redirect
     */
    public String buildAuthorizationUrl(String state, String redirectUri) {
        oauthProperties.initDefaults();

        return UriComponentsBuilder.fromUriString(oauthProperties.getAuthorizationUri())
            .queryParam("client_id", oauthProperties.getClientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "select_account")
            .build()
            .toUriString();
    }

    /**
     * Exchange authorization code for access token
     */
    public GoogleTokenResponse exchangeCodeForToken(String code) {
        oauthProperties.initDefaults();

        log.info("Exchanging code for token with redirect_uri: {}", oauthProperties.getRedirectUri());

        String credentials = oauthProperties.getClientId() + ":" + oauthProperties.getClientSecret();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

        // Use MultiValueMap for form data
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", oauthProperties.getRedirectUri());

        try {
            String response = WebClient.create(oauthProperties.getTokenUri())
                .post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", basicAuth)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.info("Token exchange response: {}", response);

            return parseTokenResponse(response);
        } catch (Exception e) {
            log.error("Failed to exchange code for token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to exchange authorization code for token: " + e.getMessage(), e);
        }
    }

    /**
     * Get user info from Google
     */
    public GoogleUserInfo getUserInfo(String accessToken) {
        oauthProperties.initDefaults();

        try {
            return WebClient.create(oauthProperties.getUserInfoUri())
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfo.class)
                .block();
        } catch (Exception e) {
            log.error("Failed to get user info: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user info from Google", e);
        }
    }

    private GoogleTokenResponse parseTokenResponse(String responseJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(responseJson, GoogleTokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse token response: {}", responseJson);
            throw new RuntimeException("Failed to parse token response", e);
        }
    }

    /**
     * Check if OAuth is properly configured
     */
    public boolean isConfigured() {
        return oauthProperties.isConfigured();
    }

    public String getClientId() {
        return oauthProperties.getClientId();
    }

    public String getRedirectUri() {
        oauthProperties.initDefaults();
        return oauthProperties.getRedirectUri();
    }

    // Response DTOs
    public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("scope") String scope
    ) {}

    public record GoogleUserInfo(
        @JsonProperty("sub") String id,
        @JsonProperty("email") String email,
        @JsonProperty("email_verified") boolean emailVerified,
        @JsonProperty("name") String name,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        @JsonProperty("picture") String picture,
        @JsonProperty("locale") String locale
    ) {}
}
