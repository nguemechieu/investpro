package org.investpro.exchange.core;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;

/**
 * VenueAwareExchange - Interface for broker implementations using the venue-aware architecture.
 * This is separate from the legacy Exchange class to avoid breaking existing code.
 */
public interface VenueAwareExchange {
    
    /**
     * Connect to the broker.
     */
    boolean connect();
    
    /**
     * Disconnect from the broker.
     */
    void disconnect();
    
    /**
     * Check if currently connected.
     */
    boolean isConnected();
    
    /**
     * Get the broker name (e.g., "Coinbase", "OANDA").
     */
    String getName();
    
    /**
     * Get the specific venue (e.g., COINBASE_SPOT, OANDA_FX_CFD).
     */
    BrokerVenue getVenue();
    
    /**
     * Get human-readable venue name.
     */
    String getVenueName();
    
    /**
     * Get the capability profile for this venue.
     */
    BrokerCapabilityProfile getCapabilities();
    
    /**
     * Get a candle data supplier for fetching OHLCV candlestick data.
     * 
     * @param secondsPerCandle the timeframe in seconds (60 = 1M, 3600 = 1H, 86400 = 1D, etc.)
     * @param tradePair the trading pair to fetch candles for
     * @return a CandleDataSupplier configured for this venue and trade pair
     */
    CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);
}
