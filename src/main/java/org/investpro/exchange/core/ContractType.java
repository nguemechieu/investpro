package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for contract types - classification of trading instruments and order types.
 */
@Getter
public enum ContractType {
    SPOT("spot"),
    MARGIN("margin"),
    FUTURE("future"),
    PERPETUAL("perpetual"),
    OPTION("option"),
    CFD("cfd");
    
    private final String value;
    
    ContractType(String value) {
        this.value = value;
    }

    /**
     * Parse string to ContractType enum.
     */
    public static ContractType fromString(String value) {
        if (value == null) return null;
        for (ContractType contractType : ContractType.values()) {
            if (contractType.value.equalsIgnoreCase(value)) {
                return contractType;
            }
        }
        return null;
    }
}
