package com.example.hotelmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.oauth.google")
@Getter
@Setter
public class OAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri = "http://localhost:8080/api/auth/oauth/google/callback";
    private String frontendCallbackUrl = "http://localhost:3000/auth/google/callback";
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;

    public void initDefaults() {
        if (authorizationUri == null || authorizationUri.isBlank()) {
            authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";
        }
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = "https://oauth2.googleapis.com/token";
        }
        if (userInfoUri == null || userInfoUri.isBlank()) {
            userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }
}
