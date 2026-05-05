package org.investpro.exchange.coinbase;

import  org.investpro.exchange.core.*;
import  org.investpro.models.trading.TradePair;
import  org.investpro.utils.CandleDataSupplier;
import java.util.logging.Logger;

/**
 * CoinbaseExchange - Abstract base for all Coinbase venue implementations.
 */
public abstract class CoinbaseExchange implements VenueAwareExchange {
    protected static final Logger logger = Logger.getLogger(CoinbaseExchange.class.getName());
    
    protected final CoinbaseAuthProvider authProvider;
    protected final CoinbaseProductMetadataService metadataService;
    protected final CoinbaseOrderPayloadFactory orderFactory;
    protected final BrokerCapabilityProfile capabilityProfile;
    protected final BrokerVenue venue;
    
    protected boolean connected;
    
    protected CoinbaseExchange(String apiKeyName, String apiSecret, BrokerVenue venue) {
        this.authProvider = new CoinbaseAuthProvider(apiKeyName, apiSecret);
        this.metadataService = new CoinbaseProductMetadataService(authProvider);
        this.orderFactory = new CoinbaseOrderPayloadFactory();
        this.capabilityProfile = createCapabilityProfile(venue);
        this.venue = venue;
        this.connected = false;
    }
    
    protected abstract BrokerCapabilityProfile createCapabilityProfile(BrokerVenue venue);
    
    @Override
    public boolean connect() {
        // Verify JWT generation works
        if (!authProvider.isValid()) {
            logger.severe("Coinbase auth provider not valid");
            return connected = false;
        }
        
        // Test WebSocket JWT generation
        String jwt = authProvider.generateWebSocketToken();
        if (jwt == null) {
            logger.severe("Failed to generate WebSocket JWT");
            return connected = false;
        }
        
        logger.info("Successfully connected to %s".formatted(venue.getDisplayName()));
        return connected = true;
    }
    
    @Override
    public void disconnect() {
        connected = false;
        logger.info("Disconnected from %s".formatted(venue.getDisplayName()));
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public BrokerCapabilityProfile getCapabilities() {
        return capabilityProfile;
    }
    
    @Override
    public String getName() {
        return "Coinbase";
    }
    
    @Override
    public BrokerVenue getVenue() {
        return venue;
    }
    
    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }
    
    /**
     * Place an order using the normalized request.
     */
    public String placeOrder(NormalizedOrderRequest order) {
        if (!connected) {
            throw new IllegalStateException("Not connected to %s".formatted(venue.getDisplayName()));
        }
        
        // Verify capability
        if (!capabilityProfile.supportsInstrumentType(order.getProductId().contains("PERP") ? 
            InstrumentType.CRYPTO_PERPETUAL : InstrumentType.CRYPTO_SPOT)) {
            throw new UnsupportedOperationException(
                    "Venue %s does not support this instrument type".formatted(venue.getDisplayName()));
        }
        
        String payload = orderFactory.createOrderPayload(order);
        logger.info("Placing order on %s: %s".formatted(venue.getDisplayName(), payload));
        
        // Would execute REST API call here
        return "ORDER_ID_STUB";
    }
}
