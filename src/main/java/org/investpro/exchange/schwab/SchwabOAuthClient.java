package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One shared broker HTTP client and JSON mapper; no login credentials are accepted. */
final class SchwabOAuthClient {
    private final SchwabApiConfig config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Clock clock;
    SchwabOAuthClient(SchwabApiConfig config, HttpClient http, ObjectMapper json, Clock clock) {
        this.config = config; this.http = http; this.json = json; this.clock = clock;
    }
    URI buildAuthorizationUri(String state) {
        if (!config.hasRequiredCredentials() || state == null || state.isBlank())
            throw new IllegalArgumentException("Schwab client credentials and authorization state are required.");
        URI endpoint = config.authorizationUri();
        return URI.create(endpoint + "?response_type=code&client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri().toString()) + "&state=" + encode(state));
    }
    CompletableFuture<SchwabTokenState> exchangeAuthorizationCode(String code) {
        return request("grant_type=authorization_code&code=" + encode(code)
                + "&redirect_uri=" + encode(config.redirectUri().toString()), null);
    }
    CompletableFuture<SchwabTokenState> refresh(SchwabTokenState old) {
        return request("grant_type=refresh_token&refresh_token=" + encode(old.refreshToken()), old);
    }
    private CompletableFuture<SchwabTokenState> request(String body, SchwabTokenState old) {
        if (config.clientId().isBlank() || config.clientSecret().isBlank())
            return CompletableFuture.failedFuture(new SchwabAuthenticationException("Configure Schwab client ID and client secret."));
        URI endpoint = SchwabApiConfig.requireHttps(config.oauthTokenUrl());
        String basic = Base64.getEncoder().encodeToString((config.clientId() + ":" + config.clientSecret()).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        // Expiration is measured from request start, avoiding optimistic network-delay assumptions.
        Instant obtained = clock.instant();
        CompletableFuture<HttpResponse<String>> transport;
        try { transport = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(new java.io.IOException("Schwab token request could not start.")); }
        return transport.handle((response, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                throw new CompletionException(cause instanceof HttpTimeoutException
                        ? new HttpTimeoutException("Schwab token request timed out.")
                        : new java.io.IOException("Schwab token service is unreachable."));
            }
            return response;
        }).thenApply(response -> {
            try {
                if (response.statusCode() != 200) {
                    if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403)
                        throw new SchwabAuthenticationException("Schwab authorization rejected. Reauthorize in the browser (HTTP " + response.statusCode() + ").");
                    throw new java.io.IOException("Schwab token request unavailable (HTTP " + response.statusCode() + ").");
                }
                var payload = json.readTree(response.body());
                if (payload == null || !payload.path("expires_in").isIntegralNumber() || !payload.path("expires_in").canConvertToLong()) throw new IllegalArgumentException();
                long seconds = payload.path("expires_in").asLong();
                if (seconds <= 90) throw new IllegalArgumentException();
                String refresh = payload.path("refresh_token").asText("");
                if (refresh.isBlank() && old != null) refresh = old.refreshToken();
                Instant refreshExpiry = old == null ? null : old.refreshTokenExpiresAt();
                // Do not invent a refresh lifetime or extend authorization on rotation.
                if (payload.path("refresh_token_expires_in").canConvertToLong()) {
                    long duration = payload.path("refresh_token_expires_in").asLong();
                    if (duration <= 0) throw new IllegalArgumentException();
                    refreshExpiry = obtained.plusSeconds(duration);
                }
                return new SchwabTokenState(payload.path("access_token").asText(""), refresh,
                        obtained.plusSeconds(seconds), obtained, refreshExpiry,
                        payload.path("token_type").asText(""), payload.path("scope").asText(""));
            } catch (Exception error) {
                if (error instanceof java.io.IOException && !(error instanceof com.fasterxml.jackson.core.JsonProcessingException))
                    throw new CompletionException(error);
                throw new CompletionException(new SchwabAuthenticationException("Schwab returned an invalid token response. Reauthorize."));
            }
        });
    }
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
