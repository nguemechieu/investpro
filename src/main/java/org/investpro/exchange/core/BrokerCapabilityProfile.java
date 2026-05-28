package org.investpro.exchange.core;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * BrokerCapabilityProfile - Describes what capabilities a broker/venue supports.
 * Used for capability-based routing and UI presentation.
 */
public class BrokerCapabilityProfile {
    @Getter
    private final String brokerId;
    @Getter
    private final BrokerVenue venue;
    private final Set<BrokerCapability> capabilities;
    
    public BrokerCapabilityProfile(String brokerId, BrokerVenue venue) {
        this.brokerId = brokerId;
        this.venue = venue;
        this.capabilities = new HashSet<>();
    }
    
    public void add(BrokerCapability capability) {
        capabilities.add(capability);
    }
    
    public void addAll(BrokerCapability... capabilities) {
        this.capabilities.addAll(Arrays.asList(capabilities));
    }
    
    public boolean supports(BrokerCapability capability) {
        return capabilities.contains(capability);
    }
    
    public boolean supportsMarketType(MarketType marketType) {
        return switch (marketType) {
            case SPOT -> supports(BrokerCapability.CRYPTO_SPOT) || 
                       (supports(BrokerCapability.STOCKS) && supports(BrokerCapability.FOREX));
            case MARGIN -> supports(BrokerCapability.MARGIN) || supports(BrokerCapability.LEVERAGE);
            case FUTURE -> supports(BrokerCapability.FUTURES);
            case PERPETUAL -> supports(BrokerCapability.PERPETUALS);
            case CFD -> supports(BrokerCapability.CFD);
            case DERIVATIVE -> supports(BrokerCapability.FUTURES) || 
                              supports(BrokerCapability.PERPETUALS) || 
                              supports(BrokerCapability.CFD);
            case UNKNOWN -> false;
        };
    }
    
    public boolean supportsInstrumentType(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case CRYPTO_SPOT -> supports(BrokerCapability.CRYPTO_SPOT);
            case CRYPTO_FUTURE -> supports(BrokerCapability.FUTURES) && 
                                 supports(BrokerCapability.CRYPTO_SPOT);
            case CRYPTO_PERPETUAL -> supports(BrokerCapability.PERPETUALS) && 
                                    supports(BrokerCapability.CRYPTO_SPOT);
            case CRYPTO_CFD -> supports(BrokerCapability.CFD) && 
                              supports(BrokerCapability.CRYPTO_SPOT);
            
            case STOCK_SPOT -> supports(BrokerCapability.STOCKS);
            case STOCK_FUTURE -> supports(BrokerCapability.STOCKS) && 
                                supports(BrokerCapability.FUTURES);
            case STOCK_PERPETUAL, INDEX_PERPETUAL -> supports(BrokerCapability.PERPETUALS) &&
                                   supports(BrokerCapability.INDEX_DERIVATIVES);
            case STOCK_CFD, INDEX_CFD -> supports(BrokerCapability.CFD) &&
                             supports(BrokerCapability.INDEX_DERIVATIVES);
            
            case INDEX_FUTURE -> supports(BrokerCapability.FUTURES) && 
                                supports(BrokerCapability.INDEX_DERIVATIVES);

            case FOREX_SPOT -> supports(BrokerCapability.FOREX);
            case FOREX_SPOT_MARGIN -> supports(BrokerCapability.FOREX) && 
                                      supports(BrokerCapability.MARGIN);
            case FOREX_CFD -> supports(BrokerCapability.FOREX) && 
                             supports(BrokerCapability.CFD);
            
            case METAL_SPOT -> supports(BrokerCapability.METAL_DERIVATIVES);
            case METAL_FUTURE -> supports(BrokerCapability.FUTURES) && 
                                supports(BrokerCapability.METAL_DERIVATIVES);
            case METAL_PERPETUAL -> supports(BrokerCapability.PERPETUALS) && 
                                   supports(BrokerCapability.METAL_DERIVATIVES);
            case METAL_CFD -> supports(BrokerCapability.CFD) && 
                             supports(BrokerCapability.METAL_DERIVATIVES);
            
            case COMMODITY_FUTURE -> supports(BrokerCapability.COMMODITY_DERIVATIVES) && 
                                    supports(BrokerCapability.FUTURES);
            case COMMODITY_PERPETUAL -> supports(BrokerCapability.COMMODITY_DERIVATIVES) && 
                                       supports(BrokerCapability.PERPETUALS);
            case COMMODITY_CFD -> supports(BrokerCapability.COMMODITY_DERIVATIVES) && 
                                supports(BrokerCapability.CFD);
            
            case UNKNOWN -> false;
        };
    }
    
    public Set<BrokerCapability> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    @Override
    public String toString() {
        return "BrokerCapabilityProfile{" +
                "brokerId='" + brokerId + '\'' +
                ", venue=" + venue +
                ", capabilities=" + capabilities.size() + " cap(s)" +
                '}';
    }
}
