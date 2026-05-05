package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for broker capabilities - what features each broker/venue supports.
 */
@Getter
public enum BrokerCapability {
    // Market Data
    PUBLIC_MARKET_DATA("Public market data"),
    PRIVATE_ACCOUNT_DATA("Private account data"),
    ORDER_BOOK("Order book streaming"),
    TICKER("Ticker/price updates"),
    TRADES("Trade history"),
    CANDLES("Candlestick data"),
    
    // Trading
    REST_TRADING("REST API trading"),
    WEBSOCKET_MARKET_DATA("WebSocket market data"),
    WEBSOCKET_PRIVATE_DATA("WebSocket private account updates"),
    
    // Order Types
    MARKET_ORDER("Market orders"),
    LIMIT_ORDER("Limit orders"),
    STOP_ORDER("Stop orders"),
    STOP_LOSS("Stop loss orders"),
    TAKE_PROFIT("Take profit orders"),
    BRACKET_ORDER("Bracket orders (SL + TP)"),
    
    // Account & Positions
    LEVERAGE("Leverage/margin trading"),
    MARGIN("Margin account features"),
    POSITIONS("Position management"),
    BALANCES("Account balances"),
    FILLS("Trade fills/executions"),
    OPEN_ORDERS("Query open orders"),
    CLOSED_ORDERS("Query closed orders"),
    
    // Product Support
    FUTURES("Futures products"),
    PERPETUALS("Perpetual contracts"),
    CFD("CFD products"),
    FOREX("Forex/currency pairs"),
    STOCKS("Stock/equity products"),
    CRYPTO_SPOT("Cryptocurrency spot"),
    INDEX_DERIVATIVES("Index derivatives"),
    METAL_DERIVATIVES("Metal derivatives"),
    COMMODITY_DERIVATIVES("Commodity derivatives"),
    
    UNKNOWN("Unknown capability");
    
    private final String description;
    
    BrokerCapability(String description) {
        this.description = description;
    }

}
