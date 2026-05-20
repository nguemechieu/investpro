package org.investpro.exchange.coinbase;

import lombok.Getter;
import org.investpro.exchange.core.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * CoinbaseProductMetadataService - Fetches and caches Coinbase product metadata.
 */
@Getter
public class CoinbaseProductMetadataService {
    private final CoinbaseAuthProvider authProvider;
    private final Map<String, InstrumentMetadata> metadataCache;
    private final CoinbaseInstrumentClassifier classifier;
    
    public CoinbaseProductMetadataService(CoinbaseAuthProvider authProvider) {
        this.authProvider = authProvider;
        this.metadataCache = new HashMap<>();
        this.classifier = new CoinbaseInstrumentClassifier();
    }
    
    /**
     * Get metadata for a product, fetching from API if not cached.
     */
    public InstrumentMetadata getMetadata(String productId) {
        if (metadataCache.containsKey(productId)) {
            return metadataCache.get(productId);
        }
        
        // Fetch from API (stub for now - would call Coinbase REST API)
        InstrumentMetadata metadata = fetchFromApi(productId);
        if (metadata != null) {
            metadataCache.put(productId, metadata);
        }
        return metadata;
    }
    
    private InstrumentMetadata fetchFromApi(String productId) {
        // Classify the product first
        CoinbaseInstrumentClassifier.Classification classification = 
            classifier.classify(productId, new HashMap<>());
        
        if (classification.instrumentType() == InstrumentType.UNKNOWN) {
            return null;
        }
        
        // Build metadata based on classification
        return new InstrumentMetadata(
            "COINBASE",
            productId,
            productId,
                classification.venue(),
            determineAssetClass(classification.instrumentType()),
            determineMarketType(classification.venue()),
                classification.instrumentType(),
                classification.baseAsset(),
                classification.quoteAsset(),
                classification.settlementAsset(),
            getPriceIncrement(productId),
            getQuantityIncrement(productId),
            getMinQuantity(productId),
            getMinNotional(productId),
            getMaxLeverage(classification.venue()),
            true,
            false
        );
    }
    
    private AssetClass determineAssetClass(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case CRYPTO_SPOT, CRYPTO_FUTURE, CRYPTO_PERPETUAL, CRYPTO_CFD -> AssetClass.CRYPTO;
            case STOCK_SPOT, STOCK_FUTURE, STOCK_PERPETUAL, STOCK_CFD -> AssetClass.EQUITY;
            case INDEX_FUTURE, INDEX_PERPETUAL, INDEX_CFD -> AssetClass.INDEX;
            case METAL_SPOT, METAL_FUTURE, METAL_PERPETUAL, METAL_CFD -> AssetClass.COMMODITY;
            case FOREX_SPOT, FOREX_SPOT_MARGIN, FOREX_CFD -> AssetClass.FOREX;
            case COMMODITY_FUTURE, COMMODITY_PERPETUAL, COMMODITY_CFD -> AssetClass.COMMODITY;
            default -> AssetClass.UNKNOWN;
        };
    }
    
    private MarketType determineMarketType(BrokerVenue venue) {
        return switch (venue) {
            case COINBASE_SPOT -> MarketType.SPOT;
            case COINBASE_US_FUTURES -> MarketType.FUTURE;
            case COINBASE_INTERNATIONAL_PERPETUALS -> MarketType.PERPETUAL;
            default -> MarketType.UNKNOWN;
        };
    }
    
    private BigDecimal getPriceIncrement(String productId) {
        // Stub - would parse from API response
        return BigDecimal.valueOf(0.01);
    }
    
    private BigDecimal getQuantityIncrement(String productId) {
        // Stub - would parse from API response
        return BigDecimal.valueOf(0.0001);
    }
    
    private java.math.BigDecimal getMinQuantity(String productId) {
        // Stub - would parse from API response
        return java.math.BigDecimal.valueOf(0.001);
    }
    
    private BigDecimal getMinNotional(String productId) {
        // Stub - would parse from API response
        return BigDecimal.TEN;
    }
    
    private java.math.BigDecimal getMaxLeverage(BrokerVenue venue) {
        return switch (venue) {
            case COINBASE_SPOT -> java.math.BigDecimal.ONE; // No leverage on spot
            case COINBASE_US_FUTURES -> java.math.BigDecimal.valueOf(10);
            case COINBASE_INTERNATIONAL_PERPETUALS -> java.math.BigDecimal.valueOf(20);
            default -> java.math.BigDecimal.ONE;
        };
    }
    
    public void clearCache() {
        metadataCache.clear();
    }
}
