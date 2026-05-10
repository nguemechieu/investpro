package org.investpro.enums;

/**
 * Risk engine status states.
 */
public enum RiskStatus {
    PASSING("Passing", "#10b981"), // Green - risk within limits
    WARNING("Warning", "#f59e0b"), // Orange - risk elevated
    BLOCKING("Blocking", "#ef4444"), // Red - trades blocked by risk
    EMERGENCY_STOP("Emergency Stop", "#dc2626"); // Dark red - full stop

    private final String label;
    private final String colorHex;

    RiskStatus(String label, String colorHex) {
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
