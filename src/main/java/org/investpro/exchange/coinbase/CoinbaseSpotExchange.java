package org.investpro.exchange.coinbase;

import org.investpro.exchange.core.BrokerCapability;
import org.investpro.exchange.core.BrokerCapabilityProfile;
import org.investpro.exchange.core.BrokerVenue;

/**
 * CoinbaseSpotExchange - Spot trading venue for Coinbase (no leverage, crypto only).
 */
public class CoinbaseSpotExchange extends CoinbaseExchange {
    
    public CoinbaseSpotExchange(String apiKeyName, String apiSecret) {
        super(apiKeyName, apiSecret, BrokerVenue.COINBASE_SPOT);
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
            BrokerCapability.LIMIT_ORDER
        );
        
        // Account
        profile.addAll(
            BrokerCapability.BALANCES,
            BrokerCapability.OPEN_ORDERS,
            BrokerCapability.CLOSED_ORDERS,
            BrokerCapability.FILLS
        );
        
        // Product support (SPOT ONLY, NO LEVERAGE)
        profile.add(BrokerCapability.CRYPTO_SPOT);
        
        return profile;
    }
    
    @Override
    public String getVenueName() {
        return "Coinbase Spot";
    }
}
