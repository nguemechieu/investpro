package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.MarketTick;

import java.time.Instant;

public record MarketQualitySnapshot(
        MarketTick tick,
        double spreadPercent,
        int liquidityScore,
        double volume24h,
        Instant observedAt
) {
    public MarketQualitySnapshot {
        spreadPercent = Double.isFinite(spreadPercent) ? spreadPercent : Double.POSITIVE_INFINITY;
        liquidityScore = Math.max(0, Math.min(100, liquidityScore));
        volume24h = Double.isFinite(volume24h) && volume24h > 0 ? volume24h : 0.0;
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }

    public static MarketQualitySnapshot missing() {
        return new MarketQualitySnapshot(null, Double.POSITIVE_INFINITY, 0, 0.0, Instant.EPOCH);
    }
}
