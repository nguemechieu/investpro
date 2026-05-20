package org.investpro.exchange.infrastructure;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.coinbase.CoinbaseExchange;
import org.investpro.exchange.coinbase.CoinbaseFuturesExchange;
import org.investpro.exchange.coinbase.CoinbasePerpetualExchange;
import org.investpro.exchange.coinbase.CoinbaseSpotExchange;
import org.investpro.exchange.core.*;
import org.investpro.exchange.oanda.OandaFxCfdExchange;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * BrokerRouter - Central routing logic for selecting the right broker/venue implementation
 * based on broker, venue, and instrument type.
 */
@Slf4j
@Getter
@Setter
public class BrokerRouter {

    
    private final Map<String, VenueAwareExchange> activeExchanges;
    
    public BrokerRouter() {
        this.activeExchanges = new HashMap<>();
    }
    
    /**
     * Create and get the appropriate exchange implementation.
     */
    public VenueAwareExchange getExchange(String brokerName, @NonNull BrokerVenue venue, String apiKey, String apiSecret) {
        String cacheKey = brokerName + ":" + venue.name();
        
        // Check cache
        if (activeExchanges.containsKey(cacheKey)) {
            VenueAwareExchange cached = activeExchanges.get(cacheKey);
            if (cached.isConnected()) {
                return cached;
            }
        }
        
        // Create new instance
        VenueAwareExchange exchange = createExchange(brokerName, venue, apiKey, apiSecret);
        if (exchange != null) {
            activeExchanges.put(cacheKey, exchange);
        }
        return exchange;
    }
    
    private VenueAwareExchange createExchange(String brokerName, BrokerVenue venue, String apiKey, String apiSecret) {
        if (brokerName == null || venue == null) {
            log.error("Broker name and venue required");
            return null;
        }
        
        try {
            // Route to Coinbase venues
            if (brokerName.equalsIgnoreCase("COINBASE")) {
                return switch (venue) {
                    case COINBASE_SPOT -> new CoinbaseSpotExchange(apiKey, apiSecret);
                    case COINBASE_US_FUTURES -> new CoinbaseFuturesExchange(apiKey, apiSecret);
                    case COINBASE_INTERNATIONAL_PERPETUALS -> new CoinbasePerpetualExchange(apiKey, apiSecret);
                    default -> {
                        log.error("Unsupported Coinbase venue: " + venue);
                        yield null;
                    }
                };
            }
            
            // Route to OANDA
            else if (brokerName.equalsIgnoreCase("OANDA")) {
                if (venue != BrokerVenue.OANDA_FX_CFD) {
                    log.error("OANDA only supports FX_CFD venue, got: " + venue);
                    return null;
                }
                return new OandaFxCfdExchange(apiKey);
            }
            
            // Other brokers not yet implemented
            else {
                log.error("Broker not yet implemented: " + brokerName);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to create exchange: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validate if a broker/venue combination supports a particular instrument type.
     */
    public boolean supports(String brokerName, BrokerVenue venue, InstrumentType instrumentType) {
        VenueAwareExchange exchange = getExchange(brokerName, venue, "", "");
        if (exchange == null) {
            return false;
        }
        
        BrokerCapabilityProfile capabilities = exchange.getCapabilities();
        return capabilities.supportsInstrumentType(instrumentType);
    }
    
    /**
     * Get the recommended venue for a specific instrument type on a broker.
     */
    public BrokerVenue getRecommendedVenue(String brokerName, InstrumentType instrumentType) {
        return switch (brokerName.toUpperCase()) {
            case "COINBASE" -> {
                if (instrumentType.isLeveraged() && instrumentType.name().contains("PERPETUAL")) {
                    yield BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS;
                } else if (instrumentType.isLeveraged() && instrumentType.name().contains("FUTURE")) {
                    yield BrokerVenue.COINBASE_US_FUTURES;
                } else {
                    yield BrokerVenue.COINBASE_SPOT;
                }
            }
            case "OANDA" -> BrokerVenue.OANDA_FX_CFD;
            default -> BrokerVenue.UNKNOWN;
        };
    }
    
    /**
     * Place an order through the router.
     */
    public String placeOrder(String brokerName, BrokerVenue venue, NormalizedOrderRequest order,
                             String apiKey, String apiSecret) {
        VenueAwareExchange exchange = getExchange(brokerName, venue, apiKey, apiSecret);
        if (exchange == null) {
            throw new IllegalArgumentException("Could not create exchange for " + brokerName + "/" + venue);
        }
        
        if (!exchange.isConnected()) {
            if (!exchange.connect()) {
                throw new IllegalStateException("Failed to connect to " + brokerName + "/" + venue);
            }
        }
        
        // Check if venue supports this order type
        if (!exchange.getCapabilities().supportsInstrumentType(order.getProductId().contains("PERP") ?
            InstrumentType.CRYPTO_PERPETUAL : InstrumentType.CRYPTO_SPOT)) {
            throw new UnsupportedOperationException(
                venue.getDisplayName() + " does not support this instrument type");
        }
        
        // Route to appropriate implementation
        if (exchange instanceof CoinbaseExchange coinbaseExch) {
            return coinbaseExch.placeOrder(order);
        } else if (exchange instanceof OandaFxCfdExchange oandaExch) {
            return oandaExch.placeOrder(order);
        } else {
            throw new UnsupportedOperationException("Order placement not implemented for " + brokerName);
        }
    }
    
    public void closeAll() {
        for (VenueAwareExchange exchange : activeExchanges.values()) {
            if (exchange.isConnected()) {
                exchange.disconnect();
            }
        }
        activeExchanges.clear();
    }
}
