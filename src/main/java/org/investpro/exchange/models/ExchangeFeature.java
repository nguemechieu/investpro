package org.investpro.exchange.models;

/**
 * Individual features/capabilities that an exchange may support.
 *
 * Used to query and describe exchange capabilities with fine-grained control.
 */
public enum ExchangeFeature {

    // ---------------------------------------------------------------------
    // Asset / market types
    // ---------------------------------------------------------------------

    SPOT_TRADING,
    FOREX_TRADING,
    CRYPTO_TRADING,
    EQUITIES_TRADING,
    STOCKS_TRADING,
    DERIVATIVES_TRADING,
    FUTURES_TRADING,
    PERPETUALS_TRADING,
    OPTIONS_TRADING,
    INDICES_TRADING,
    COMMODITIES_TRADING,

    // ---------------------------------------------------------------------
    // Trading modes
    // ---------------------------------------------------------------------

    LIVE_TRADING,
    PAPER_TRADING,
    SANDBOX,
    MARGIN_TRADING,
    LEVERAGE,

    // ---------------------------------------------------------------------
    // Order types
    // ---------------------------------------------------------------------

    MARKET_ORDERS,
    LIMIT_ORDERS,
    STOP_ORDERS,
    STOP_LIMIT_ORDERS,
    TRAILING_STOP_ORDERS,
    BRACKET_ORDERS,
    STOP_LOSS_TAKE_PROFIT,

    // ---------------------------------------------------------------------
    // Account / portfolio
    // ---------------------------------------------------------------------

    ACCOUNT_INFO,
    BALANCES,
    POSITIONS,
    OPEN_ORDERS,
    ORDER_HISTORY,
    ACCOUNT_TRADES,
    FILLS,
    ORDER_VALIDATION,

    // ---------------------------------------------------------------------
    // Market data
    // ---------------------------------------------------------------------

    TICKER,
    TICKERS,
    RECENT_TRADES,
    HISTORICAL_CANDLES,
    STREAMING_PRICES,

    // ---------------------------------------------------------------------
    // Market depth
    // ---------------------------------------------------------------------

    ORDER_BOOK,
    FULL_ORDER_BOOK,
    TOP_OF_BOOK,
    DISTRIBUTION_BOOK,

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

    NATIVE_WEBSOCKET,
    WEBSOCKET_SUPPORT,
    HTTP_STREAMING,
    POLLING_FALLBACK,

    STREAMING_TICKER,
    STREAMING_ORDER_BOOK,
    STREAMING_TRADES,
    STREAMING_CANDLES,
    STREAMING_ACCOUNT,
    STREAMING_ORDERS,
    STREAMING_FILLS,
    STREAMING_POSITIONS,
    STREAMING_BALANCES,

    // ---------------------------------------------------------------------
    // Infrastructure / authentication
    // ---------------------------------------------------------------------

    RATE_LIMITING,
    AUTH_REQUIRED_FOR_TRADING,
    AUTH_REQUIRED_FOR_ACCOUNT_INFO,
    AUTH_REQUIRED_FOR_MARKET_DATA
}