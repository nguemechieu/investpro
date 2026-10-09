package org.investpro.exchange.schwab;

import java.time.Instant;
import java.util.Objects;

public record SchwabTokenState(String accessToken, String refreshToken, Instant accessTokenExpiresAt,
                               Instant obtainedAt, Instant refreshTokenExpiresAt, String tokenType, String scope) {
    public SchwabTokenState {
        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()
                || !"Bearer".equalsIgnoreCase(tokenType)) throw new IllegalArgumentException("Invalid Schwab token state");
        if (!accessToken.matches("[A-Za-z0-9._~+/=-]+") || refreshToken.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Invalid Schwab token encoding");
        Objects.requireNonNull(accessTokenExpiresAt); Objects.requireNonNull(obtainedAt);
        if (!accessTokenExpiresAt.isAfter(obtainedAt)) throw new IllegalArgumentException("Invalid Schwab token expiration");
        scope = Objects.toString(scope, "");
    }
    boolean accessValid(Instant now) { return now.isBefore(accessTokenExpiresAt.minusSeconds(90)); }
    boolean refreshValid(Instant now) { return refreshTokenExpiresAt == null || now.isBefore(refreshTokenExpiresAt); }
    @Override public String toString() { return "SchwabTokenState[tokens=<redacted>]"; }
}
