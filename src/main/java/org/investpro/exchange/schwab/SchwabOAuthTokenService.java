package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
@Getter
@Setter

@Slf4j
class SchwabOAuthTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchwabApiConfig config;
    private final HttpClient httpClient;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    SchwabOAuthTokenService(@NotNull SchwabApiConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    SchwabOAuthTokenService(@NotNull SchwabApiConfig config, @NotNull HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    synchronized @NotNull String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }

        if (!config.hasRequiredCredentials()) {
            throw new IllegalStateException("Missing Schwab OAuth credentials (client id, secret, or refresh token)");
        }

        String body = "grant_type=refresh_token&refresh_token="
                + URLEncoder.encode(config.refreshToken(), StandardCharsets.UTF_8);

        String basic = Base64.getEncoder().encodeToString(
                (config.clientId() + ":" + config.clientSecret()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.oauthTokenUrl()))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Schwab token refresh failed with HTTP " + response.statusCode());
        }

        JsonNode payload = MAPPER.readTree(response.body());
        String token = payload.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("Schwab token refresh returned empty access_token");
        }

        long expiresInSeconds = payload.path("expires_in").asLong(1800L);
        this.accessToken = token;
        this.accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expiresInSeconds));

        log.debug("Refreshed Schwab access token (expires in {}s)", expiresInSeconds);
        return this.accessToken;
    }
}