package org.investpro.market;

import org.investpro.models.trading.TradePair;

import java.util.List;

/**
 * Market Structure Intelligence service interface.
 * <p>
 * Enriches exchange-tradeable pairs with BIS FX liquidity data, currency importance
 * scores, session relevance, and risk recommendations. This layer does NOT replace
 * exchange tradability filtering — it runs after the exchange has confirmed a pair
 * is available.
 *
 * <pre>
 * Exchange tradability -> "Can this account trade this pair?"
 * Market structure      → "How liquid, important, risky, and suitable is this pair?"
 * </pre>
 */
public interface MarketStructureService {

    /**
     * Get the full market-structure profile for a pair.
     * Always returns a non-null profile; falls back to {@link LiquidityTier#UNKNOWN}
     * if the pair cannot be classified.
     *
     * @param pair the trade pair to classify
     * @return immutable market structure profile
     */
    MarketStructureProfile getProfile(TradePair pair);

    /**
     * Classify the liquidity tier for a pair.
     *
     * @param pair the trade pair
     * @return liquidity tier (never null)
     */
    LiquidityTier classifyLiquidityTier(TradePair pair);

    /**
     * Get the risk multiplier driven by market-structure liquidity.
     * <p>
     * Values:
     * <ul>
     *   <li>Tier 1 Major: 1.00</li>
     *   <li>Tier 2 Major Cross: 0.75</li>
     *   <li>Tier 3 Minor: 0.50</li>
     *   <li>Tier 4 Exotic: 0.25</li>
     *   <li>Unknown: 0.10 (paper trade only)</li>
     * </ul>
     *
     * @param pair the trade pair
     * @return multiplier in range [0.10, 1.00]
     */
    double getLiquidityRiskMultiplier(TradePair pair);

    /**
     * Returns true when market structure recommends automated trading for this pair.
     * Exotic and unknown pairs return false.
     *
     * @param pair the trade pair
     * @return true if auto-trading is recommended
     */
    boolean isRecommendedForAutoTrading(TradePair pair);

    /**
     * Rank a list of tradeable pairs by market-structure quality (best first).
     * <p>
     * Used by the trading bot to prefer liquid pairs and defer exotic ones.
     *
     * @param tradeablePairs pairs already confirmed tradeable by exchange adapter
     * @return sorted list, highest liquidity score first
     */
    List<TradePair> rankPairsByMarketStructure(List<TradePair> tradeablePairs);

    /**
     * Stats snapshot for the operations board.
     */
    record MarketStructureStats(
            String source,
            int sourceYear,
            int pairsClassified,
            int unknownPairCount,
            long lastRefreshEpochMs
    ) {}

    /**
     * Return current stats for display in the operations board.
     *
     * @return current stats snapshot
     */
    MarketStructureStats getStats();
}
