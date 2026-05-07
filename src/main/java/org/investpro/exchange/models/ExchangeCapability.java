package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Value;
import java.util.Set;

/**
 * Describes the complete capability profile of an exchange.
 *
 * <p>
 * This allows the UI and trading services to query what an exchange supports
 * before attempting
 * operations, avoiding silent failures and enabling capability-aware behavior.
 */
@Value
@Builder(toBuilder = true)
public class ExchangeCapability {
    // Identity
    String exchangeName;

    // Asset types
    boolean supportsSpot;
    boolean supportsForex;
    boolean supportsCrypto;
    boolean supportsEquities;
    boolean supportsDerivatives;
    boolean supportsFutures;
    boolean supportsPerpetuals;

    // Order types
    boolean supportsMarketOrders;
    boolean supportsLimitOrders;
    boolean supportsStopOrders;
    boolean supportsStopLimitOrders;
    boolean supportsTrailingStopOrders;

    // Data & Info endpoints
    boolean supportsAccountInfo;
    boolean supportsPositions;
    boolean supportsOrderValidation;
    boolean supportsHistoricalCandles;
    boolean supportsStreamingPrices;

    // Market depth capabilities
    boolean supportsFullOrderBook;
    boolean supportsTopOfBook;
    boolean supportsDistributionBook;

    // Primary market depth type
    MarketDepthType marketDepthType;

    // Streaming capabilities
    boolean supportsWebSocketStreaming;
    boolean supportsOrderBookStreaming;
    boolean supportsTradeStreaming;
    boolean supportsCandleStreaming;

    // Advanced
    boolean supportsMarginTrading;
    boolean supportsRateLimitInfo;

    // Authentication & API info
    String authenticationType; // e.g., "API_KEY", "JWT", "OAUTH2"
    String apiBaseUrl;

    // Notes for operators
    String notes; // e.g., "OANDA supports pricing-derived synthetic order books"

    /**
     * Check if this exchange supports a specific feature.
     */
    public boolean supports(ExchangeFeature feature) {
        return switch (feature) {
            // Asset types
            case SPOT_TRADING -> supportsSpot;
            case FOREX_TRADING -> supportsForex;
            case CRYPTO_TRADING -> supportsCrypto;
            case EQUITIES_TRADING -> supportsEquities;
            case DERIVATIVES_TRADING -> supportsDerivatives;
            case FUTURES_TRADING -> supportsFutures;
            case PERPETUALS_TRADING -> supportsPerpetuals;

            // Order types
            case MARKET_ORDERS -> supportsMarketOrders;
            case LIMIT_ORDERS -> supportsLimitOrders;
            case STOP_ORDERS -> supportsStopOrders;
            case STOP_LIMIT_ORDERS -> supportsStopLimitOrders;
            case TRAILING_STOP_ORDERS -> supportsTrailingStopOrders;

            // Data & Info
            case ACCOUNT_INFO -> supportsAccountInfo;
            case POSITIONS -> supportsPositions;
            case ORDER_VALIDATION -> supportsOrderValidation;
            case HISTORICAL_CANDLES -> supportsHistoricalCandles;
            case STREAMING_PRICES -> supportsStreamingPrices;

            // Depth types
            case FULL_ORDER_BOOK -> supportsFullOrderBook;
            case TOP_OF_BOOK -> supportsTopOfBook;
            case DISTRIBUTION_BOOK -> supportsDistributionBook;

            // Streaming
            case STREAMING_ORDER_BOOK -> supportsOrderBookStreaming;
            case STREAMING_TRADES -> supportsTradeStreaming;
            case STREAMING_CANDLES -> supportsCandleStreaming;

            // Advanced
            case WEBSOCKET_SUPPORT -> supportsWebSocketStreaming;
            case MARGIN_TRADING -> supportsMarginTrading;
            case RATE_LIMITING -> supportsRateLimitInfo;
        };
    }
}
