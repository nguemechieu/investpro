package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.util.Map;

public record Instrument(
        InstrumentId id,
        Asset baseAsset,
        Asset quoteAsset,
        String displayName,
        AssetClass assetClass,
        String venue,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal minOrderSize,
        BigDecimal quoteIncrement,
        BigDecimal baseIncrement,
        TradingStatus tradingStatus,
        boolean marginable,
        boolean shortable,
        boolean active,
        boolean reversible,
        Map<String, Object> metadata
) {
    public Instrument {
        if (id == null) {
            throw new IllegalArgumentException("instrument id is required");
        }
        assetClass = assetClass == null ? AssetClass.UNKNOWN : assetClass;
        venue = venue == null ? "" : venue.trim();
        tickSize = positiveOrZero(tickSize);
        lotSize = positiveOrZero(lotSize);
        minOrderSize = positiveOrZero(minOrderSize);
        quoteIncrement = positiveOrZero(quoteIncrement);
        baseIncrement = positiveOrZero(baseIncrement);
        tradingStatus = tradingStatus == null ? TradingStatus.UNKNOWN : tradingStatus;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        displayName = displayName == null || displayName.isBlank() ? id.symbol() : displayName.trim();
    }

    public boolean tradableNow() {
        return active && tradingStatus == TradingStatus.ACTIVE;
    }

    private static BigDecimal positiveOrZero(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
