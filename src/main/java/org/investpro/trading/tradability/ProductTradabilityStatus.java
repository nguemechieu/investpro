package org.investpro.trading.tradability;

import java.time.Instant;

/**
 * Product-level tradability snapshot used by MarketWatch, execution planning,
 * and bot filtering.
 */
public record ProductTradabilityStatus(
        String productId,
        boolean tradable,
        boolean viewOnly,
        boolean tradingDisabled,
        boolean isDisabled,
        boolean cancelOnly,
        boolean limitOnly,
        boolean postOnly,
        boolean auctionMode,
        String status,
        String statusMessage,
        Instant lastUpdated) {

    public ProductTradabilityStatus {
        productId = productId == null ? "" : productId.trim().toUpperCase();
        status = status == null ? "UNKNOWN" : status.trim().toUpperCase();
        statusMessage = statusMessage == null ? "" : statusMessage;
        lastUpdated = lastUpdated == null ? Instant.now() : lastUpdated;
    }

    public boolean isTradeable() {
        return tradable && !viewOnly && !tradingDisabled && !isDisabled && !cancelOnly;
    }

    public boolean isLimitOnly() {
        return tradable && limitOnly && !postOnly && !viewOnly && !isDisabled;
    }

    public boolean isReadOnly() {
        return viewOnly || isDisabled || tradingDisabled || cancelOnly;
    }

    public boolean canPlaceMarketOrder() {
        return isTradeable() && !limitOnly && !postOnly && !auctionMode;
    }

    public boolean canPlaceLimitOrder() {
        return isTradeable() && !isDisabled && !viewOnly;
    }
}