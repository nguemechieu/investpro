package org.investpro.exchange.models;

/**
 * Individual features/capabilities that an exchange may support.
 *
 * <p>
 * Used to query and describe exchange capabilities with fine-grained control.
 */
public enum ExchangeFeature {
    // Asset types
    SPOT_TRADING,
    FOREX_TRADING,
    CRYPTO_TRADING,
    EQUITIES_TRADING,
    DERIVATIVES_TRADING,
    FUTURES_TRADING,
    PERPETUALS_TRADING,

    // Order types
    MARKET_ORDERS,
    LIMIT_ORDERS,
    STOP_ORDERS,
    STOP_LIMIT_ORDERS,
    TRAILING_STOP_ORDERS,

    // Data & Info
    ACCOUNT_INFO,
    POSITIONS,
    ORDER_VALIDATION,
    HISTORICAL_CANDLES,
    STREAMING_PRICES,

    // Depth types
    FULL_ORDER_BOOK,
    TOP_OF_BOOK,
    DISTRIBUTION_BOOK,

    // Streaming
    STREAMING_ORDER_BOOK,
    STREAMING_TRADES,
    STREAMING_CANDLES,

    // Advanced
    WEBSOCKET_SUPPORT,
    RATE_LIMITING,
    MARGIN_TRADING
}
