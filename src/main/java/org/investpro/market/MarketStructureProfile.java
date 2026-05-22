package org.investpro.market;

import org.investpro.models.trading.TradePair;

/**
 * Immutable market-structure enrichment record for a currency pair.
 * <p>
 * Sourced from BIS Triennial Central Bank Survey FX turnover data.
 * This enriches exchange-tradeable pairs with liquidity intelligence —
 * it does NOT replace exchange tradeability decisions.
 */
public record MarketStructureProfile(

        /** The pair this profile describes. */
        TradePair pair,

        /** BIS-derived liquidity classification. */
        LiquidityTier liquidityTier,

        /** Importance score for the base currency (0–100 scale, USD = 100). */
        double baseCurrencyImportanceScore,

        /** Importance score for the quote currency (0–100 scale, USD = 100). */
        double quoteCurrencyImportanceScore,

        /** Combined pair liquidity score (0–100). */
        double pairLiquidityScore,

        /** Estimated relative spread risk (higher = wider typical spreads). */
        double expectedSpreadRisk,

        /** Session activity score for the current market session (0–1). */
        double sessionActivityScore,

        /** Primary trading session: London, New York, Tokyo, Sydney, Overlap. */
        String primarySession,

        /** Recommended strategy style based on liquidity tier. */
        String recommendedStrategyStyle,

        /** Whether automated trading is recommended for this pair. */
        boolean autoTradingRecommended,

        /** Warning message, or empty string if no warning. */
        String warning,

        /** Name of the data source used for classification. */
        String source,

        /** Year of the source dataset. */
        int sourceYear

) {

    /**
     * Convenience: returns the risk multiplier from the liquidity tier.
     * Used by RiskManagementSystem to scale position sizes.
     */
    public double getRiskMultiplier() {
        return liquidityTier.getRiskMultiplier();
    }

    /**
     * True when auto-trading is blocked by market structure policy.
     */
    public boolean isAutoTradingBlocked() {
        return !autoTradingRecommended;
    }

    /**
     * True when a warning should be displayed in the UI.
     */
    public boolean hasWarning() {
        return warning != null && !warning.isBlank();
    }

    /**
     * Human-readable one-line summary for UI tooltips and logs.
     */
    public String getSummary() {
        return "%s | %s | Score: %.1f | %s | Auto-trade: %s".formatted(
                pair,
                liquidityTier.getDisplayName(),
                pairLiquidityScore,
                primarySession,
                autoTradingRecommended ? "Yes" : "No");
    }
}
