package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for asset classes - fundamental classification of instruments.
 */
@Getter
public enum AssetClass {
    CRYPTO("crypto"),
    FOREX("forex"),
    EQUITY("equity"),
    INDEX("index"),
    COMMODITY("commodity"),
    UNKNOWN("unknown");
    
    private final String value;
    
    AssetClass(String value) {
        this.value = value;
    }

    /**
     * Parse string to AssetClass enum.
     */
    public static AssetClass fromString(String value) {
        if (value == null) return null;
        for (AssetClass assetClass : AssetClass.values()) {
            if (assetClass.value.equalsIgnoreCase(value)) {
                return assetClass;
            }
        }
        return null;
    }
}
