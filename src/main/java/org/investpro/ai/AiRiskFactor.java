package org.investpro.ai;

import lombok.Getter;

/**
 * Risk factors evaluated by the AI reasoning layer.
 * Each factor represents a dimension of risk that affects trade approval decisions.
 */
@Getter
public enum AiRiskFactor {
    /**
     * Market conditions unfavorable (high volatility, poor liquidity, adverse trend).
     * Severity: Can cause WAIT or REJECT.
     */
    UNFAVORABLE_MARKET_CONDITIONS("Market conditions are not favorable", true),

    /**
     * Account is in drawdown. New trades compound recovery risk.
     * Severity: High drawdown reduces position size or causes WAIT.
     */
    ACCOUNT_IN_DRAWDOWN("Account is in drawdown", true),

    /**
     * Portfolio heat (total risk exposure) is high.
     * Severity: May cause position size reduction or WAIT.
     */
    HIGH_PORTFOLIO_HEAT("Portfolio risk exposure is elevated", true),

    /**
     * Liquidity is thin. Execution may be poor.
     * Severity: May cause execution strategy change or position size reduction.
     */
    THIN_LIQUIDITY("Market liquidity is thin", true),

    /**
     * High volatility increases slippage and drawdown risk.
     * Severity: May cause position size reduction or WAIT.
     */
    HIGH_VOLATILITY("Volatility is elevated", true),

    /**
     * Open positions already exist in this symbol or correlated symbols.
     * Severity: May cause position size reduction or WAIT.
     */
    EXISTING_POSITIONS("Account has existing positions", true),

    /**
     * Recent trade history shows losses. Pattern matching may apply.
     * Severity: May cause WAIT or REJECT.
     */
    RECENT_LOSSES("Recent trade history shows losses", true),

    /**
     * Capital protection is disabled (NONE strategy).
     * Severity: May cause position size reduction.
     */
    NO_CAPITAL_PROTECTION("Capital protection strategy is NONE", true),

    /**
     * Psychology profile is IMPULSIVE or FEARFUL without protection.
     * Severity: May cause position size reduction or WAIT.
     */
    PSYCHOLOGY_RISK("Psychology profile indicates elevated emotional risk", true),

    /**
     * Setup has very low probability of success.
     * Severity: Causes WAIT or REJECT.
     */
    LOW_PROBABILITY_SETUP("Trade setup has low success probability", true);

    private final String description;
    private final boolean isNegative;

    AiRiskFactor(String description, boolean isNegative) {
        this.description = description;
        this.isNegative = isNegative;
    }

}
