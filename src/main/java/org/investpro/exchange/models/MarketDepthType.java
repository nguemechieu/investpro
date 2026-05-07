package org.investpro.exchange.models;

/**
 * Describes the market depth/order book type supported by an exchange.
 *
 * <ul>
 * <li>FULL_ORDER_BOOK: Complete bid/ask ladder with all orders (e.g., Coinbase,
 * Binance)</li>
 * <li>TOP_OF_BOOK: Only best bid/ask prices (e.g., OANDA)</li>
 * <li>DISTRIBUTION_BOOK: Aggregated order distribution without individual
 * orders (e.g., OANDA positionBook)</li>
 * <li>NONE: No order book data available</li>
 * </ul>
 */
public enum MarketDepthType {
    /** Complete order book with all orders at all price levels */
    FULL_ORDER_BOOK,

    /** Only the best bid/ask price available */
    TOP_OF_BOOK,

    /** Aggregated distribution of orders (price ranges with sizes) */
    DISTRIBUTION_BOOK,

    /** No order book data available */
    NONE
}
