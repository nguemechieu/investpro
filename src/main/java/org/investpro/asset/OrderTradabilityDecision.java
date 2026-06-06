package org.investpro.asset;

public record OrderTradabilityDecision(boolean allowed, String reason) {
    public static OrderTradabilityDecision allow() {
        return new OrderTradabilityDecision(true, "Order tradability validation passed");
    }

    public static OrderTradabilityDecision block(String reason) {
        return new OrderTradabilityDecision(false, reason == null || reason.isBlank() ? "Order blocked" : reason);
    }
}
