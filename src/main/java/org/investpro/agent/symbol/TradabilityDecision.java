package org.investpro.agent.symbol;

public record TradabilityDecision(
        boolean connected,
        boolean marketDataAllowed,
        boolean orderSubmissionAllowed,
        boolean tradable,
        String reason) {

    public static TradabilityDecision allowed() {
        return new TradabilityDecision(true, true, true, true, "Tradable");
    }

    public static TradabilityDecision notTradable(String reason) {
        return new TradabilityDecision(true, true, false, false, reason == null ? "Not tradable" : reason);
    }
}
