package org.investpro.monitoring;

import lombok.Getter;

/**
 * Health status of a component.
 */
@Getter
public enum ComponentStatus {
    HEALTHY("✅ Healthy", true),
    DEGRADED("⚠️ Degraded", true),
    WARNING("⚠️ Warning", true),
    FAILED("❌ Failed", false),
    DISABLED("⊘ Disabled", false),
    UNKNOWN("❓ Unknown", false);

    /**
     * -- GETTER --
     *  Returns the display name with emoji indicator.
     */
    public final String displayName;
    /**
     * -- GETTER --
     *  Returns true if this status indicates the component can function.
     */
    public final boolean operational;

    ComponentStatus(String displayName, boolean operational) {
        this.displayName = displayName;
        this.operational = operational;
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

}
