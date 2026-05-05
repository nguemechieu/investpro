package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for broker venue - different trading venues offered by brokers.
 * Each venue has different products, order types, leverage rules, and capabilities.
 */
@Getter
public enum BrokerVenue {
    COINBASE_SPOT("Coinbase Spot", "Cryptocurrency spot trading"),
    COINBASE_US_FUTURES("Coinbase US Futures", "US-regulated cryptocurrency and index futures"),
    COINBASE_INTERNATIONAL_PERPETUALS("Coinbase Perpetuals", "International cryptocurrency and derivative perpetuals"),
    
    OANDA_FX_CFD("OANDA FX/CFD", "Leveraged forex and CFD trading"),
    
    BINANCE_SPOT("Binance Spot", "Binance spot trading"),
    BINANCE_FUTURES("Binance Futures", "Binance perpetual futures"),
    
    BITFINEX_SPOT("Bitfinex Spot", "Bitfinex spot trading"),
    BITFINEX_DERIVATIVES("Bitfinex Derivatives", "Bitfinex perpetual derivatives"),
    
    ALPACA_STOCKS("Alpaca Stocks", "US stock trading"),
    ALPACA_CRYPTO("Alpaca Crypto", "Cryptocurrency spot trading"),
    
    INTERACTIVE_BROKERS_STOCKS("Interactive Brokers Stocks", "Global stock trading"),
    INTERACTIVE_BROKERS_FOREX("Interactive Brokers Forex", "Forex trading"),
    
    UNKNOWN("Unknown", "Unknown venue");
    
    private final String displayName;
    private final String description;
    
    BrokerVenue(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
