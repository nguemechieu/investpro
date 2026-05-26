package org.investpro.trading.tradability;

public enum InstrumentTradeStatus {

    TRADEABLE,
    SUPPORTED_BUT_MARKET_CLOSED,
    SUPPORTED_BUT_TEMPORARILY_DISABLED,
    UNSUPPORTED_BY_ACCOUNT,
    UNSUPPORTED_BY_EXCHANGE,
    INVALID_SYMBOL,
    PERMISSION_DENIED,
    RATE_LIMITED,
    API_ERROR,
    UNKNOWN;

    public boolean isTradeable() {
        return this == TRADEABLE;
    }

    public boolean isKnownButNotTradeable() {
        return switch (this) {
            case SUPPORTED_BUT_MARKET_CLOSED,
                 SUPPORTED_BUT_TEMPORARILY_DISABLED,
                 UNSUPPORTED_BY_ACCOUNT,
                 UNSUPPORTED_BY_EXCHANGE,
                 PERMISSION_DENIED -> true;
            default -> false;
        };
    }

    public static InstrumentTradeStatus from(TradabilityStatus status) {
        if (status == null) return UNKNOWN;
        return switch (status) {
            case FULLY_TRADABLE -> TRADEABLE;
            case MARKET_CLOSED -> SUPPORTED_BUT_MARKET_CLOSED;
            case HALTED, DISABLED, CANCEL_ONLY, LIMIT_ONLY, POST_ONLY,
                 REDUCE_ONLY, CLOSE_ONLY, INACTIVE,
                 LIQUIDITY_UNAVAILABLE, VIEW_ONLY, AUCTION_ONLY,
                 MIN_SIZE_INVALID, MAX_SIZE_INVALID -> SUPPORTED_BUT_TEMPORARILY_DISABLED;
            case UNSUPPORTED_PRODUCT_TYPE, REGION_RESTRICTED,
                 ACCOUNT_TYPE_RESTRICTED, MARGIN_RESTRICTED -> UNSUPPORTED_BY_ACCOUNT;
            case UNSUPPORTED_ORDER_TYPE -> UNSUPPORTED_BY_EXCHANGE;
            case PERMISSION_DENIED, API_KEY_RESTRICTED -> PERMISSION_DENIED;
            case UNKNOWN -> UNKNOWN;
        };
    }

    public TradabilityStatus toTradabilityStatus() {
        return switch (this) {
            case TRADEABLE -> TradabilityStatus.FULLY_TRADABLE;
            case SUPPORTED_BUT_MARKET_CLOSED -> TradabilityStatus.MARKET_CLOSED;
            case SUPPORTED_BUT_TEMPORARILY_DISABLED -> TradabilityStatus.DISABLED;
            case UNSUPPORTED_BY_ACCOUNT -> TradabilityStatus.ACCOUNT_TYPE_RESTRICTED;
            case UNSUPPORTED_BY_EXCHANGE -> TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE;
            case PERMISSION_DENIED -> TradabilityStatus.PERMISSION_DENIED;
            case INVALID_SYMBOL, RATE_LIMITED, API_ERROR, UNKNOWN -> TradabilityStatus.UNKNOWN;
        };
    }
}
