package org.investpro.trading.tradability;

import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.Map;

public record SymbolTradability(
        String exchangeId,
        TradePair tradePair,
        String nativeSymbol,
        TradabilityStatus status,
        boolean marketDataAllowed,
        boolean watchlistAllowed,
        boolean backtestingAllowed,
        boolean paperTradingAllowed,
        boolean liveTradingAllowed,
        boolean botTradingAllowed,
        boolean orderSubmissionAllowed,
        boolean marketOrderAllowed,
        boolean limitOrderAllowed,
        boolean stopOrderAllowed,
        boolean shortingAllowed,
        boolean marginAllowed,
        boolean leverageAllowed,
        String reason,
        Instant checkedAt,
        Map<String, Object> rawMetadata
) {
    public SymbolTradability {
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        rawMetadata = rawMetadata == null ? Map.of() : Map.copyOf(rawMetadata);
        reason = reason == null ? "" : reason;
    }

    public boolean isFullyTradable() {
        return status == TradabilityStatus.FULLY_TRADABLE
                && liveTradingAllowed
                && orderSubmissionAllowed;
    }

    public boolean canBeUsedForBotTrading() {
        return botTradingAllowed
                && orderSubmissionAllowed
                && status == TradabilityStatus.FULLY_TRADABLE;
    }

    public boolean canBeDisplayedInMarketWatch() {
        return watchlistAllowed || marketDataAllowed;
    }
}
