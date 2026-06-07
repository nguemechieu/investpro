package org.investpro.strategy.auto;

public record RiskProfile(
        double maxRiskPercent,
        double maxDrawdownPercent,
        double minProfitFactor) {

    public static RiskProfile conservative() {
        return new RiskProfile(1.0, 15.0, 1.20);
    }
}
