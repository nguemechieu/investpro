package org.investpro.exchange.coinbase;

import org.investpro.exchange.core.BrokerCapability;
import org.investpro.exchange.core.BrokerCapabilityProfile;
import org.investpro.exchange.core.BrokerVenue;

/**
 * CoinbasePerpetualExchange - International Perpetuals trading venue for Coinbase.
 */
public class CoinbasePerpetualExchange extends CoinbaseExchange {
    
    public CoinbasePerpetualExchange(String apiKeyName, String apiSecret) {
        super(apiKeyName, apiSecret, BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS);
    }
    
    @Override
    protected BrokerCapabilityProfile createCapabilityProfile(BrokerVenue venue) {
        BrokerCapabilityProfile profile = new BrokerCapabilityProfile("COINBASE", venue);
        
        // Market data
        profile.addAll(
            BrokerCapability.PUBLIC_MARKET_DATA,
            BrokerCapability.WEBSOCKET_MARKET_DATA,
            BrokerCapability.WEBSOCKET_PRIVATE_DATA,
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
            BrokerCapability.STOP_ORDER,
            BrokerCapability.STOP_LOSS,
            BrokerCapability.TAKE_PROFIT,
            BrokerCapability.BRACKET_ORDER
        );
        
        // Account with leverage
        profile.addAll(
            BrokerCapability.LEVERAGE,
            BrokerCapability.POSITIONS,
            BrokerCapability.BALANCES,
            BrokerCapability.OPEN_ORDERS,
            BrokerCapability.CLOSED_ORDERS,
            BrokerCapability.FILLS
        );
        
        // Product support (PERPETUALS WITH HIGH LEVERAGE)
        profile.addAll(
            BrokerCapability.PERPETUALS,
            BrokerCapability.CRYPTO_SPOT,  // Perpetuals on crypto
            BrokerCapability.INDEX_DERIVATIVES,
            BrokerCapability.METAL_DERIVATIVES
        );
        
        return profile;
    }
    
    @Override
    public String getVenueName() {
        return "Coinbase International Perpetuals";
    }
}
