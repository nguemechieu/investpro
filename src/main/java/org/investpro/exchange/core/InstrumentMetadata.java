package org.investpro.exchange.core;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * InstrumentMetadata - Contains all metadata about a tradable instrument on a broker/venue.
 * This includes pricing, sizing rules, leverage limits, tradability, and venue information.
 */
@Getter
@Setter
public class InstrumentMetadata {
    private final String brokerId;
    private final String productId;
    private final String displayName;
    private final BrokerVenue venue;
    private final AssetClass assetClass;
    private final MarketType marketType;
    private final InstrumentType instrumentType;
    
    private final String baseAsset;
    private final String quoteAsset;
    private final String settlementAsset;
    
    private final BigDecimal priceIncrement;
    private final BigDecimal quantityIncrement;
    private final BigDecimal minQuantity;
    private final BigDecimal minNotional;
    private final BigDecimal maxLeverage;
    
    private final boolean tradable;
    private final boolean expired;
    
    private final Map<String, Object> metadata;
    
    public InstrumentMetadata(String brokerId, String productId, String displayName,
                             BrokerVenue venue, AssetClass assetClass,
                             MarketType marketType, InstrumentType instrumentType,
                             String baseAsset, String quoteAsset, String settlementAsset,
                             BigDecimal priceIncrement, BigDecimal quantityIncrement,
                             BigDecimal minQuantity, BigDecimal minNotional,
                             BigDecimal maxLeverage, boolean tradable, boolean expired) {
        this.brokerId = Objects.requireNonNull(brokerId, "brokerId required");
        this.productId = Objects.requireNonNull(productId, "productId required");
        this.displayName = Objects.requireNonNull(displayName, "displayName required");
        this.venue = Objects.requireNonNull(venue, "venue required");
        this.assetClass = Objects.requireNonNull(assetClass, "assetClass required");
        this.marketType = Objects.requireNonNull(marketType, "marketType required");
        this.instrumentType = Objects.requireNonNull(instrumentType, "instrumentType required");
        
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.settlementAsset = settlementAsset;
        
        this.priceIncrement = priceIncrement != null ? priceIncrement : BigDecimal.ZERO;
        this.quantityIncrement = quantityIncrement != null ? quantityIncrement : BigDecimal.ZERO;
        this.minQuantity = minQuantity != null ? minQuantity : BigDecimal.ZERO;
        this.minNotional = minNotional != null ? minNotional : BigDecimal.ZERO;
        this.maxLeverage = maxLeverage != null ? maxLeverage : BigDecimal.ONE;
        
        this.tradable = tradable;
        this.expired = expired;
        
        this.metadata = new HashMap<>();
    }
    
    // Getters
    public String getBrokerId() { return brokerId; }
    public String getProductId() { return productId; }
    public String getDisplayName() { return displayName; }
    public BrokerVenue getVenue() { return venue; }
    public AssetClass getAssetClass() { return assetClass; }
    public MarketType getMarketType() { return marketType; }
    public InstrumentType getInstrumentType() { return instrumentType; }
    
    public String getBaseAsset() { return baseAsset; }
    public String getQuoteAsset() { return quoteAsset; }
    public String getSettlementAsset() { return settlementAsset; }
    
    public BigDecimal getPriceIncrement() { return priceIncrement; }
    public BigDecimal getQuantityIncrement() { return quantityIncrement; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public BigDecimal getMinNotional() { return minNotional; }
    public BigDecimal getMaxLeverage() { return maxLeverage; }
    
    public boolean isTradable() { return tradable; }
    public boolean isExpired() { return expired; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        return "InstrumentMetadata{" +
                "brokerId='" + brokerId + '\'' +
                ", productId='" + productId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", venue=" + venue +
                ", instrumentType=" + instrumentType +
                ", tradable=" + tradable +
                ", maxLeverage=" + maxLeverage +
                '}';
    }
}
