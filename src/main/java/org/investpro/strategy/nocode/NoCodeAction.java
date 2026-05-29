package org.investpro.strategy.nocode;

/**
 * The trading action emitted by a no-code strategy rule when its conditions are satisfied.
 *
 * <p><strong>CRITICAL:</strong> These actions represent signal intent only.
 * No {@code NoCodeAction} causes any order to be placed directly.
 * All actions are translated to a {@link org.investpro.strategy.StrategySignal}
 * which then flows through the AI review, risk engine, and execution pipeline.</p>
 */
public enum NoCodeAction {

    /** Open or add to a long position. Produces a BUY StrategySignal. */
    BUY("BUY", "Enter or add to long position"),

    /** Open or add to a short position. Produces a SELL StrategySignal. */
    SELL("SELL", "Enter or add to short position"),

    /** Do nothing this bar. Produces a HOLD StrategySignal. */
    HOLD("HOLD", "No action - wait for better conditions"),

    /** Close any open long position. Produces a SELL StrategySignal for exit. */
    CLOSE_LONG("CLOSE LONG", "Exit and close long position"),

    /** Close any open short position. Produces a BUY StrategySignal for exit. */
    CLOSE_SHORT("CLOSE SHORT", "Exit and close short position");

    /** Short display name shown in the UI action selector. */
    public final String displayName;

    /** Description shown in tooltips. */
    public final String description;

    NoCodeAction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** @return true if this action results in a long (BUY) signal. */
    public boolean isBuySignal() {
        return this == BUY || this == CLOSE_SHORT;
    }

    /** @return true if this action results in a sell (SELL) signal. */
    public boolean isSellSignal() {
        return this == SELL || this == CLOSE_LONG;
    }
}
