package org.investpro.monitoring;

/**
 * Health status of a component.
 */
public enum ComponentStatus {
    HEALTHY("✅ Healthy", true),
    DEGRADED("⚠️ Degraded", true),
    WARNING("⚠️ Warning", true),
    FAILED("❌ Failed", false),
    DISABLED("⊘ Disabled", false),
    UNKNOWN("❓ Unknown", false);

    public final String displayName;
    public final boolean operational;

    ComponentStatus(String displayName, boolean operational) {
        this.displayName = displayName;
        this.operational = operational;
    }

    /**
     * Returns true if this status indicates the component can function.
     */
    public boolean isOperational() {
        return operational;
    }

    /**
     * Returns true if this status indicates an issue.
     */
    public boolean hasIssue() {
        return this == DEGRADED || this == WARNING || this == FAILED;
    }

    /**
     * Returns true if this is a critical failure.
     */
    public boolean isCritical() {
        return this == FAILED;
    }

    /**
     * Returns the display name with emoji indicator.
     */
    public String getDisplayName() {
        return displayName;
    }
}
