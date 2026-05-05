package org.investpro.exchange.core;

import lombok.Getter;

/**
 * Enum for market types - the kind of trading venue or contract structure.
 */
@Getter
public enum MarketType {
    SPOT("Spot", "Immediate delivery/settlement"),
    MARGIN("Margin", "Leveraged spot trading with borrowed funds"),
    FUTURE("Future", "Exchange-traded derivatives with expiration"),
    PERPETUAL("Perpetual", "Non-expiring derivative contracts"),
    CFD("CFD", "Contract for Difference (OTC derivative)"),
    DERIVATIVE("Derivative", "Generic derivative or levered contract"),
    UNKNOWN("Unknown", "Unknown market type");
    
    private final String displayName;
    private final String description;
    
    MarketType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
