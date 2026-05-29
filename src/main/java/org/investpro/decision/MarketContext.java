package org.investpro.decision;

import org.investpro.models.Account;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable market input for pre-trade evaluation.
 */
public record MarketContext(
        @NotNull TradePair tradePair,
        @NotNull Side side,
        @NotNull Ticker ticker,
        double signalStrength,
        @Nullable Account account,
        @NotNull Instant observedAt) {

    public static MarketContext of(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength,
            @Nullable Account account) {
        return new MarketContext(tradePair, side, ticker, signalStrength, account, Instant.now());
    }
}
