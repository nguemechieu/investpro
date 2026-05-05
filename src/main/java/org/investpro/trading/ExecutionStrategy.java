package org.investpro.trading;

/**
 * Enum for execution strategies - defines how orders are placed and managed.
 */
public enum ExecutionStrategy {
    MARKET_ORDER("Market Order", "Execute immediately at current price", 0.0, "IMMEDIATE"),
    LIMIT_ORDER("Limit Order", "Execute only at specified price or better", 0.02, "CONTROLLED"),
    ICEBERG_ORDER("Iceberg Order", "Hide most of order, reveal in small batches", 0.01, "STEALTH"),
    VWAP_EXECUTION("VWAP", "Execute near volume-weighted average price", 0.005, "OPTIMIZED"),
    TWAP_EXECUTION("TWAP", "Execute at time-weighted average price", 0.007, "STEADY"),
    SCALED_ENTRY("Scaled Entry", "Build position gradually over time", 0.008, "PATIENT"),
    SCALED_EXIT("Scaled Exit", "Exit position gradually to minimize slippage", 0.009, "METHODICAL"),
    ALGORITHMIC("Algorithmic", "Use algorithmic execution for optimal fill", 0.003, "SMART");

    private final String displayName;
    private final String description;
    private final double estimatedSlippage;  // Expected slippage as % of price
    private final String executionStyle;

    ExecutionStrategy(String displayName, String description, double estimatedSlippage, String executionStyle) {
        this.displayName = displayName;
        this.description = description;
        this.estimatedSlippage = estimatedSlippage;
        this.executionStyle = executionStyle;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getEstimatedSlippage() { return estimatedSlippage; }
    public String getExecutionStyle() { return executionStyle; }
}
