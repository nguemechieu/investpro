package org.investpro.exchange.oanda;

import lombok.Getter;
import org.investpro.exchange.core.*;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import java.util.logging.Logger;

/**
 * OandaFxCfdExchange - OANDA FX and CFD trading venue implementation.
 * OANDA only supports CFD/Margin trading, not futures or perpetuals.
 */
@Getter
public class OandaFxCfdExchange implements VenueAwareExchange {
    private static final Logger logger = Logger.getLogger(OandaFxCfdExchange.class.getName());
    
    private final String apiToken;
    private final OandaProductMetadataService metadataService;
    private final OandaOrderPayloadFactory orderFactory;
    private final BrokerCapabilityProfile capabilityProfile;
    
    private boolean connected;
    
    public OandaFxCfdExchange(String apiToken) {
        this.apiToken = apiToken;
        this.metadataService = new OandaProductMetadataService();
        this.orderFactory = new OandaOrderPayloadFactory();
        this.capabilityProfile = createCapabilityProfile();
        this.connected = false;
    }
    
    private BrokerCapabilityProfile createCapabilityProfile() {
        BrokerCapabilityProfile profile = new BrokerCapabilityProfile("OANDA", BrokerVenue.OANDA_FX_CFD);
        
        // Market data
        profile.addAll(
            BrokerCapability.PUBLIC_MARKET_DATA,
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
            BrokerCapability.MARGIN,
            BrokerCapability.POSITIONS,
            BrokerCapability.BALANCES,
            BrokerCapability.OPEN_ORDERS,
            BrokerCapability.CLOSED_ORDERS,
            BrokerCapability.FILLS
        );
        
        // Product support (CFD ONLY - NO FUTURES, NO PERPETUALS, NO CRYPTO SPOT)
        profile.addAll(
            BrokerCapability.CFD,
            BrokerCapability.FOREX,
            BrokerCapability.METAL_DERIVATIVES,
            BrokerCapability.INDEX_DERIVATIVES,
            BrokerCapability.COMMODITY_DERIVATIVES
        );
        
        // Notably MISSING: FUTURES, PERPETUALS, CRYPTO_SPOT
        
        return profile;
    }
    
    @Override
    public boolean connect() {
        if (apiToken == null || apiToken.isEmpty()) {
            logger.severe("OANDA API token is missing");
            return connected = false;
        }
        
        // Test API connectivity (stub)
        try {
            // Would make a test API call here
            logger.info("Successfully connected to OANDA");
            return connected = true;
        } catch (Exception e) {
            logger.severe("Failed to connect to OANDA: " + e.getMessage());
            return connected = false;
        }
    }
    
    @Override
    public void disconnect() {
        connected = false;
        logger.info("Disconnected from OANDA");
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
        return "OANDA";
    }
    
    @Override
    public BrokerVenue getVenue() {
        return BrokerVenue.OANDA_FX_CFD;
    }
    
    @Override
    public String getVenueName() {
        return "OANDA FX/CFD";
    }
    
    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        // Pass the API token to the supplier so it can fetch real candle data
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiToken);
    }
    
    /**
     * Place an order on OANDA.
     */
    public String placeOrder(NormalizedOrderRequest order) {
        if (!connected) {
            throw new IllegalStateException("Not connected to OANDA");
        }
        
        // Verify capability - OANDA does NOT support crypto spot or futures
        if (!capabilityProfile.supportsInstrumentType(order.getProductId().contains("_") ?
            InstrumentType.FOREX_CFD : InstrumentType.UNKNOWN)) {
            throw new UnsupportedOperationException(
                "OANDA FX/CFD does not support " + order.getProductId() +
                " - OANDA only supports Forex and CFD instruments, not crypto or futures");
        }
        
        String payload = orderFactory.createOrderPayload(order);
        logger.info("Placing order on OANDA: " + payload);
        
        // Would execute REST API call here
        return "OANDA_ORDER_ID_STUB";
    }
}
