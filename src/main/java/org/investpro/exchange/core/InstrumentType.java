package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for instrument types - specific product classification combining asset class and market structure.
 */
@Getter
public enum InstrumentType {
    CRYPTO_SPOT("Crypto Spot", "Digital asset spot trading"),
    CRYPTO_FUTURE("Crypto Future", "Exchange-traded crypto futures"),
    CRYPTO_PERPETUAL("Crypto Perpetual", "Non-expiring crypto derivative"),
    CRYPTO_CFD("Crypto CFD", "OTC crypto derivative"),
    
    STOCK_SPOT("Stock Spot", "Equity share ownership"),
    STOCK_FUTURE("Stock Future", "Stock index or single stock futures"),
    STOCK_PERPETUAL("Stock Perpetual", "Leveraged stock perpetual (not ownership)"),
    STOCK_CFD("Stock CFD", "OTC stock/equity CFD"),
    
    INDEX_FUTURE("Index Future", "Exchange-traded index futures"),
    INDEX_PERPETUAL("Index Perpetual", "Non-expiring index derivative"),
    INDEX_CFD("Index CFD", "OTC index CFD"),
    
    FOREX_SPOT("Forex Spot", "Spot currency exchange"),
    FOREX_SPOT_MARGIN("Forex Spot Margin", "Leveraged spot forex"),
    FOREX_CFD("Forex CFD", "OTC forex CFD"),
    
    METAL_SPOT("Metal Spot", "Precious metal spot trading"),
    METAL_FUTURE("Metal Future", "Precious metal futures"),
    METAL_PERPETUAL("Metal Perpetual", "Leveraged metal perpetual"),
    METAL_CFD("Metal CFD", "OTC metal CFD"),
    
    COMMODITY_FUTURE("Commodity Future", "Agricultural/energy futures"),
    COMMODITY_PERPETUAL("Commodity Perpetual", "Leveraged commodity perpetual"),
    COMMODITY_CFD("Commodity CFD", "OTC commodity CFD"),
    
    UNKNOWN("Unknown", "Unknown instrument type");
    
    private final String displayName;
    private final String description;
    
    InstrumentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Check if this is a derivative (not spot/margin).
     */
    public boolean isDerivative() {
        return this.name().contains("FUTURE") || this.name().contains("PERPETUAL") || this.name().contains("CFD");
    }
    
    /**
     * Check if this is leveraged trading.
     */
    public boolean isLeveraged() {
        return this.name().contains("MARGIN") || this.name().contains("PERPETUAL") || this.name().contains("CFD") || this.name().contains("FUTURE");
    }
}
