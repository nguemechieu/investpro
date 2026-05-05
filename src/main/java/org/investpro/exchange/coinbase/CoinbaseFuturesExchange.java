package org.investpro.exchange.coinbase;

import  org.investpro.exchange.core.BrokerCapability;
import  org.investpro.exchange.core.BrokerCapabilityProfile;
import  org.investpro.exchange.core.BrokerVenue;

/**
 * CoinbaseFuturesExchange - US Futures trading venue for Coinbase.
 */
public class CoinbaseFuturesExchange extends CoinbaseExchange {
    
    public CoinbaseFuturesExchange(String apiKeyName, String apiSecret) {
        super(apiKeyName, apiSecret, BrokerVenue.COINBASE_US_FUTURES);
    }
    
    @Override
    protected BrokerCapabilityProfile createCapabilityProfile(BrokerVenue venue) {
        BrokerCapabilityProfile profile = new BrokerCapabilityProfile("COINBASE", venue);
        
        // Market data
        profile.addAll(
            BrokerCapability.PUBLIC_MARKET_DATA,
            BrokerCapability.WEBSOCKET_MARKET_DATA,
            BrokerCapability.ORDER_BOOK,
            BrokerCapability.TICKER,
            BrokerCapability.TRADES,
            BrokerCapability.CANDLES
        );
        
        // Trading
        profile.addAll(
            BrokerCapability.REST_TRADING,
            BrokerCapability.MARKET_ORDER,
            BrokerCapability.LIMIT_ORDER,
            BrokerCapability.STOP_ORDER
        );
        
        // Account
        profile.addAll(
            BrokerCapability.POSITIONS,
            BrokerCapability.BALANCES,
            BrokerCapability.OPEN_ORDERS,
            BrokerCapability.CLOSED_ORDERS,
            BrokerCapability.FILLS
        );
        
        // Product support (FUTURES WITH LEVERAGE)
        profile.addAll(
            BrokerCapability.FUTURES,
            BrokerCapability.LEVERAGE,
            BrokerCapability.CRYPTO_SPOT,  // Futures on crypto
            BrokerCapability.INDEX_DERIVATIVES
        );
        
        return profile;
    }
    
    @Override
    public String getVenueName() {
        return "Coinbase US Futures";
    }
}
