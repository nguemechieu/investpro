package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

/**
 * Describes the complete capability profile of an exchange.
 *
 * This allows the UI, trading services, risk engine, execution engine,
 * and onboarding flow to query what an exchange supports before attempting
 * operations.
 */
@Value
@Builder(toBuilder = true)
public class ExchangeCapability {

    // ---------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------

    String exchangeName;
    String exchangeId;
    String displayName;

    // ---------------------------------------------------------------------
    // API / endpoints
    // ---------------------------------------------------------------------

    String apiBaseUrl;
    String webSocketBaseUrl;
    String authenticationType; // API_KEY, JWT, OAUTH2, TOKEN, NONE

    // ---------------------------------------------------------------------
    // Asset / market types
    // ---------------------------------------------------------------------
    boolean supportsWebSocket;
    boolean supportsSpot;
    boolean supportsForex;
    boolean supportsCrypto;
    boolean supportsEquities;
    boolean supportsStocks;
    boolean supportsDerivatives;
    boolean supportsFutures;
    boolean supportsPerpetuals;
    boolean supportsOptions;
    boolean supportsIndices;
    boolean supportsCommodities;

    // ---------------------------------------------------------------------
    // Trading modes
    // ---------------------------------------------------------------------

    boolean supportsLiveTrading;
    boolean supportsPaperTradingMode;
    boolean supportsSandbox;
    boolean supportsMarginTrading;
    boolean supportsLeverage;

    // ---------------------------------------------------------------------
    // Order types
    // ---------------------------------------------------------------------

    boolean supportsMarketOrders;
    boolean supportsLimitOrders;
    boolean supportsStopOrders;
    boolean supportsStopLimitOrders;
    boolean supportsTrailingStopOrders;
    boolean supportsBracketOrders;
    boolean supportsStopLossTakeProfit;

    // ---------------------------------------------------------------------
    // Account / portfolio endpoints
    // ---------------------------------------------------------------------

    boolean supportsAccountInfo;
    boolean supportsBalances;
    boolean supportsPositions;
    boolean supportsOpenOrders;
    boolean supportsOrderHistory;
    boolean supportsAccountTrades;
    boolean supportsFills;
    boolean supportsOrderValidation;

    // ---------------------------------------------------------------------
    // Market data endpoints
    // ---------------------------------------------------------------------

    boolean supportsTicker;
    boolean supportsTickers;
    boolean supportsRecentTrades;
    boolean supportsHistoricalCandles;
    boolean supportsStreamingPrices;

    // ---------------------------------------------------------------------
    // Market depth capabilities
    // ---------------------------------------------------------------------

    boolean supportsOrderBook;
    boolean supportsFullOrderBook;
    boolean supportsTopOfBook;
    boolean supportsDistributionBook;
    MarketDepthType marketDepthType;

    // ---------------------------------------------------------------------
    // Streaming capabilities
    // ---------------------------------------------------------------------

    boolean supportsNativeWebSocket;
    boolean supportsWebSocketStreaming;
    boolean supportsHttpStreaming;
    boolean supportsPollingFallback;

    boolean supportsTickerStreaming;
    boolean supportsOrderBookStreaming;
    boolean supportsTradeStreaming;
    boolean supportsCandleStreaming;
    boolean supportsAccountStreaming;
    boolean supportsOrderStreaming;
    boolean supportsFillStreaming;
    boolean supportsPositionStreaming;
    boolean supportsBalanceStreaming;

    // ---------------------------------------------------------------------
    // Infrastructure
    // ---------------------------------------------------------------------

    boolean supportsRateLimitInfo;
    boolean requiresAuthenticationForTrading;
    boolean requiresAuthenticationForAccountInfo;
    boolean requiresAuthenticationForMarketData;

    // ---------------------------------------------------------------------
    // Extra metadata
    // ---------------------------------------------------------------------

    @Singular
    Set<String> supportedTimeframes;

    @Singular
    Set<String> supportedOrderTypes;

    @Singular
    Set<String> supportedMarketTypes;

    String notes;
 boolean supportsTrailingStop;
    /**
     * Check if this exchange supports a specific feature.
     */
    public boolean supports(ExchangeFeature feature) {
        if (feature == null) {
            return false;
        }

        return switch (feature) {
            // Asset types
            case SPOT_TRADING -> supportsSpot;
            case FOREX_TRADING -> supportsForex;
            case CRYPTO_TRADING -> supportsCrypto;
            case EQUITIES_TRADING -> supportsEquities || supportsStocks;
            case STOCKS_TRADING -> supportsStocks || supportsEquities;
            case DERIVATIVES_TRADING -> supportsDerivatives;
            case FUTURES_TRADING -> supportsFutures;
            case PERPETUALS_TRADING -> supportsPerpetuals;
            case OPTIONS_TRADING -> supportsOptions;
            case INDICES_TRADING -> supportsIndices;
            case COMMODITIES_TRADING -> supportsCommodities;

            // Trading modes
            case LIVE_TRADING -> supportsLiveTrading;
            case PAPER_TRADING -> supportsPaperTradingMode;
            case SANDBOX -> supportsSandbox;
            case MARGIN_TRADING -> supportsMarginTrading;
            case LEVERAGE -> supportsLeverage;

            // Order types
            case MARKET_ORDERS -> supportsMarketOrders;
            case LIMIT_ORDERS -> supportsLimitOrders;
            case STOP_ORDERS -> supportsStopOrders;
            case STOP_LIMIT_ORDERS -> supportsStopLimitOrders;
            case TRAILING_STOP_ORDERS -> supportsTrailingStopOrders;
            case BRACKET_ORDERS -> supportsBracketOrders;
            case STOP_LOSS_TAKE_PROFIT -> supportsStopLossTakeProfit;

            // Account / portfolio
            case ACCOUNT_INFO -> supportsAccountInfo;
            case BALANCES -> supportsBalances;
            case POSITIONS -> supportsPositions;
            case OPEN_ORDERS -> supportsOpenOrders;
            case ORDER_HISTORY -> supportsOrderHistory;
            case ACCOUNT_TRADES -> supportsAccountTrades;
            case FILLS -> supportsFills;
            case ORDER_VALIDATION -> supportsOrderValidation;

            // Market data
            case TICKER -> supportsTicker;
            case TICKERS -> supportsTickers;
            case RECENT_TRADES -> supportsRecentTrades;
            case HISTORICAL_CANDLES -> supportsHistoricalCandles;
            case STREAMING_PRICES -> supportsStreamingPrices;

            // Depth types
            case ORDER_BOOK -> supportsOrderBook;
            case FULL_ORDER_BOOK -> supportsFullOrderBook;
            case TOP_OF_BOOK -> supportsTopOfBook;
            case DISTRIBUTION_BOOK -> supportsDistributionBook;

            // Streaming
            case NATIVE_WEBSOCKET -> supportsNativeWebSocket;
            case WEBSOCKET_SUPPORT -> supportsWebSocketStreaming;
            case HTTP_STREAMING -> supportsHttpStreaming;
            case POLLING_FALLBACK -> supportsPollingFallback;
            case STREAMING_TICKER -> supportsTickerStreaming;
            case STREAMING_ORDER_BOOK -> supportsOrderBookStreaming;
            case STREAMING_TRADES -> supportsTradeStreaming;
            case STREAMING_CANDLES -> supportsCandleStreaming;
            case STREAMING_ACCOUNT -> supportsAccountStreaming;
            case STREAMING_ORDERS -> supportsOrderStreaming;
            case STREAMING_FILLS -> supportsFillStreaming;
            case STREAMING_POSITIONS -> supportsPositionStreaming;
            case STREAMING_BALANCES -> supportsBalanceStreaming;

            // Infrastructure
            case RATE_LIMITING -> supportsRateLimitInfo;
            case AUTH_REQUIRED_FOR_TRADING -> requiresAuthenticationForTrading;
            case AUTH_REQUIRED_FOR_ACCOUNT_INFO -> requiresAuthenticationForAccountInfo;
            case AUTH_REQUIRED_FOR_MARKET_DATA -> requiresAuthenticationForMarketData;
        };
    }

    /**
     * Safe getter for supported timeframes.
     */
    public Set<String> getSupportedTimeframes() {
        return supportedTimeframes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(supportedTimeframes);
    }

    /**
     * Safe getter for supported order types.
     */
    public Set<String> getSupportedOrderTypes() {
        return supportedOrderTypes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(supportedOrderTypes);
    }

    /**
     * Safe getter for supported market types.
     */
    public Set<String> getSupportedMarketTypes() {
        return supportedMarketTypes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(supportedMarketTypes);
    }

    /**
     * Useful for UI display.
     */
    public boolean hasAnyStreamingSupport() {
        return supportsWebSocketStreaming
                || supportsNativeWebSocket
                || supportsHttpStreaming
                || supportsTickerStreaming
                || supportsOrderBookStreaming
                || supportsTradeStreaming
                || supportsCandleStreaming
                || supportsAccountStreaming
                || supportsOrderStreaming
                || supportsFillStreaming
                || supportsPositionStreaming
                || supportsBalanceStreaming;
    }

    /**
     * Useful for execution engine checks.
     */
    public boolean hasAnyTradingSupport() {
        return supportsLiveTrading
                || supportsPaperTradingMode
                || supportsMarketOrders
                || supportsLimitOrders
                || supportsStopOrders
                || supportsBracketOrders;
    }

    /**
     * Useful for market selector UI.
     */
    public boolean hasAnyMarketSupport() {
        return supportsSpot
                || supportsForex
                || supportsCrypto
                || supportsEquities
                || supportsStocks
                || supportsDerivatives
                || supportsFutures
                || supportsPerpetuals
                || supportsOptions
                || supportsIndices
                || supportsCommodities;
    }
}