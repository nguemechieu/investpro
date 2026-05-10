package org.investpro.decision;

/**
 * Source of the trade setup decision.
 * Indicates whether the setup came from a registered strategy or an indicator
 * composite.
 */
public enum SetupSource {
    /**
     * Setup from a registered strategy in StrategyCatalog.
     * Strategy class, name, and parameters are known.
     */
    STRATEGY("Registered strategy selection"),

    /**
     * Setup from a composite of technical indicators.
     * No single strategy matched, so indicator composite was scored instead.
     */
    INDICATOR_COMPOSITE("Indicator composite setup"),

    /**
     * No suitable setup found. Trade should be skipped.
     */
    NONE("No suitable setup found");

    public final String description;

    SetupSource(String description) {
        this.description = description;
    }
}
