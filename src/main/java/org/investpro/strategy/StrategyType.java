package org.investpro.strategy;

/**
 * Classification of a trading strategy by its origin and authoring mechanism.
 *
 * <p>Both {@link #USER_PLUGIN} and {@link #NO_CODE} strategies flow through the
 * identical platform pipeline (backtest → AI review → paper trading → live
 * approval). No strategy type may execute orders directly.</p>
 */
public enum StrategyType {

    /** Strategies built into the InvestPro platform codebase (e.g. TrendFollowingStrategy). */
    BUILT_IN("Built-in", "Platform-provided strategy, included in the InvestPro distribution"),

    /** Strategies loaded from developer JAR plugins placed in the strategies/ directory. */
    USER_PLUGIN("Developer Plugin", "Java JAR strategy developed by a third-party or institutional developer"),

    /**
     * Strategies created via the visual no-code strategy builder — no Java coding required.
     * These are compiled at runtime from a {@code NoCodeStrategyDefinition} JSON descriptor.
     */
    NO_CODE("No-Code Builder", "Visual strategy created without coding using the Strategy Builder UI"),

    /** Strategies generated or suggested by the InvestPro AI system. */
    AI_GENERATED("AI Generated", "Strategy generated or synthesised by the AI reasoning layer");

    /** Short human-readable label for display in UI components. */
    public final String displayName;

    /** Longer description shown in tooltips and documentation. */
    public final String description;

    StrategyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** @return true if this strategy was externally authored (not shipped with InvestPro). */
    public boolean isExternal() {
        return this == USER_PLUGIN || this == NO_CODE || this == AI_GENERATED;
    }
}
