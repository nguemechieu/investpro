package org.investpro.exchange.oanda;

import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentMetadata;
import org.investpro.exchange.core.InstrumentType;
import org.investpro.exchange.core.MarketType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * OandaProductMetadataService - Fetches and caches OANDA instrument metadata.
 */
public class OandaProductMetadataService {
    private static final Logger logger = Logger.getLogger(OandaProductMetadataService.class.getName());
    private final Map<String, InstrumentMetadata> metadataCache;
    private final OandaInstrumentClassifier classifier;
    
    public OandaProductMetadataService() {
        this.metadataCache = new HashMap<>();
        this.classifier = new OandaInstrumentClassifier();
    }
    
    /**
     * Get metadata for an instrument, fetching from API if not cached.
     */
    public InstrumentMetadata getMetadata(String instrumentName) {
        if (metadataCache.containsKey(instrumentName)) {
            return metadataCache.get(instrumentName);
        }
        
        InstrumentMetadata metadata = fetchFromApi(instrumentName);
        if (metadata != null) {
            metadataCache.put(instrumentName, metadata);
        }
        return metadata;
    }
    
    private InstrumentMetadata fetchFromApi(String instrumentName) {
        // Classify the instrument
        OandaInstrumentClassifier.Classification classification = 
            classifier.classify(instrumentName, new HashMap<>());
        
        if (classification.instrumentType == InstrumentType.UNKNOWN) {
            return null;
        }
        
        // All OANDA instruments are CFD/Margin (single venue)
        return new InstrumentMetadata(
            "OANDA",
            instrumentName,
            instrumentName,
            BrokerVenue.OANDA_FX_CFD,
            classification.assetClass,
            MarketType.CFD,
            classification.instrumentType,
            classification.baseAsset,
            classification.quoteAsset,
            classification.settlementAsset,
            getPriceIncrement(instrumentName),
            getQuantityIncrement(instrumentName),
            getMinQuantity(instrumentName),
            getMinNotional(instrumentName),
            getMaxLeverage(classification.instrumentType),
            true,
            false
        );
    }
    
    private BigDecimal getPriceIncrement(String instrumentName) {
        // OANDA uses different pip increments per instrument
        if (instrumentName.startsWith("X")) {
            return BigDecimal.valueOf(0.01); // Metals: 0.01
        } else if (instrumentName.contains("_USD")) {
            return BigDecimal.valueOf(0.0001); // USD pairs: 0.0001
        } else {
            return BigDecimal.valueOf(0.00001); // Other: 0.00001
        }
    }
    
    private BigDecimal getQuantityIncrement(String instrumentName) {
        return BigDecimal.ONE; // 1 unit (not fractional)
    }
    
    private BigDecimal getMinQuantity(String instrumentName) {
        return BigDecimal.valueOf(1); // Minimum 1 unit
    }
    
    private BigDecimal getMinNotional(String instrumentName) {
        // Typical minimum notional value
        return BigDecimal.valueOf(100);
    }
    
    private BigDecimal getMaxLeverage(InstrumentType instrumentType) {
        // OANDA leverage limits (typical)
        return switch (instrumentType) {
            case FOREX_CFD -> BigDecimal.valueOf(50);
            case METAL_CFD -> BigDecimal.valueOf(20);
            case INDEX_CFD -> BigDecimal.valueOf(20);
            case COMMODITY_CFD -> BigDecimal.valueOf(10);
            case STOCK_CFD -> BigDecimal.valueOf(20);
            default -> BigDecimal.ONE;
        };
    }
    
    public void clearCache() {
        metadataCache.clear();
    }
}
