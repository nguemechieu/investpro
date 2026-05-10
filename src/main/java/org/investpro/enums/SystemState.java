package org.investpro.enums;

/**
 * Trading system operational states.
 */
public enum SystemState {
    STARTING("Starting", "#38bdf8"), // Blue - initialization
    READY("Ready", "#10b981"), // Green - ready for trading
    PAPER_TRADING("Paper Trading", "#f59e0b"), // Orange - paper mode
    LIVE_TRADING("Live Trading", "#ef4444"), // Red - live mode
    PAUSED("Paused", "#94a3b8"), // Gray - paused
    DEGRADED("Degraded", "#f59e0b"), // Orange - degraded performance
    ERROR("Error", "#ef4444"), // Red - error state
    SHUTDOWN("Shutdown", "#6b7280"); // Dark gray - shutdown

    private final String label;
    private final String colorHex;

    SystemState(String label, String colorHex) {
        this.label = label;
        this.colorHex = colorHex;
    }

    public String getLabel() {
        return label;
    }

    public String getColorHex() {
        return colorHex;
    }
}
