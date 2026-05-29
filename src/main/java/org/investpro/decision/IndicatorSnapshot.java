package org.investpro.decision;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight indicator snapshot attached to a market observation.
 */
public record IndicatorSnapshot(
        @NotNull String symbol,
        @NotNull String timeframe,
        @NotNull Instant computedAt,
        @NotNull Map<String, Double> values) {
}
