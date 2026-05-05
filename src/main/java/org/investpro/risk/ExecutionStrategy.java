package org.investpro.risk;

import lombok.Getter;

/**
 * Defines order placement strategy and execution style.
 * Determines how aggressively or carefully an order is placed.
 */
@Getter
public enum ExecutionStrategy {
    MARKET_ORDER("Market Order", "Immediate execution", 0.10, "IMMEDIATE"),
    LIMIT_ORDER("Limit Order", "Controlled entry at specific price", 0.01, "CONTROLLED"),
    ICEBERG_ORDER("Iceberg Order", "Stealth execution with hidden size", 0.02, "STEALTH"),
    VWAP_EXECUTION("VWAP", "Volume-weighted average price execution", 0.03, "ALGORITHMIC"),
    TWAP_EXECUTION("TWAP", "Time-weighted average price execution", 0.02, "ALGORITHMIC"),
    SCALED_ENTRY("Scaled Entry", "Split order into multiple fills", 0.04, "GRADUAL"),
    SCALED_EXIT("Scaled Exit", "Exit position in stages", 0.04, "GRADUAL"),
    ALGORITHMIC("Algorithmic", "Smart routing and execution", 0.05, "SMART");

    private final String displayName;
    private final String description;
    private final double estimatedSlippage;
    private final String executionStyle;

    ExecutionStrategy(String displayName, String description, double estimatedSlippage, String executionStyle) {
        this.displayName = displayName;
        this.description = description;
        this.estimatedSlippage = estimatedSlippage;
        this.executionStyle = executionStyle;
    }

    public static ExecutionStrategy fromString(String value) {
        if (value == null || value.isEmpty()) return LIMIT_ORDER;
        try {
            return ExecutionStrategy.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return LIMIT_ORDER;
        }
    }
}
