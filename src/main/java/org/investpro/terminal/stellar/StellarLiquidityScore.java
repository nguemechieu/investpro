package org.investpro.terminal.stellar;

public record StellarLiquidityScore(
        boolean orderBookAvailable,
        boolean reversible,
        boolean pathPaymentSupported,
        double spreadPercent,
        double bidDepth,
        double askDepth,
        int score,
        String reason
) {
    public StellarLiquidityScore {
        score = Math.max(0, Math.min(100, score));
        reason = reason == null ? "" : reason.trim();
    }

    public boolean liquidEnoughForDisplay() {
        return orderBookAvailable && score >= 40;
    }

    public boolean liquidEnoughForTrading() {
        return orderBookAvailable && score >= 70 && spreadPercent >= 0.0 && spreadPercent <= 1.0;
    }
}
