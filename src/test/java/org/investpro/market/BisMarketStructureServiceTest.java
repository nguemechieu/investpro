package org.investpro.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link BisMarketStructureService}.
 * Tests classification, scoring, and ranking using raw currency codes
 * to avoid TradePair constructor's DB dependency.
 */
class BisMarketStructureServiceTest {

    private BisMarketStructureService service;

    @BeforeEach
    void setup() {
        service = new BisMarketStructureService();
    }

    // =========================================================================
    // Tier 1 major pair classification
    // =========================================================================

    @Test
    void eurUsdIsTier1Major() {
        assertThat(service.classifyByCode("EUR", "USD")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void usdJpyIsTier1Major() {
        assertThat(service.classifyByCode("USD", "JPY")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void gbpUsdIsTier1Major() {
        assertThat(service.classifyByCode("GBP", "USD")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void usdChfIsTier1Major() {
        assertThat(service.classifyByCode("USD", "CHF")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void audUsdIsTier1Major() {
        assertThat(service.classifyByCode("AUD", "USD")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void usdCadIsTier1Major() {
        assertThat(service.classifyByCode("USD", "CAD")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    @Test
    void nzdUsdIsTier1Major() {
        assertThat(service.classifyByCode("NZD", "USD")).isEqualTo(LiquidityTier.TIER_1_MAJOR);
    }

    // =========================================================================
    // Tier 2 major cross classification
    // =========================================================================

    @Test
    void eurGbpIsTier2Cross() {
        assertThat(service.classifyByCode("EUR", "GBP")).isEqualTo(LiquidityTier.TIER_2_MAJOR_CROSS);
    }

    @Test
    void eurJpyIsTier2Cross() {
        assertThat(service.classifyByCode("EUR", "JPY")).isEqualTo(LiquidityTier.TIER_2_MAJOR_CROSS);
    }

    @Test
    void gbpJpyIsTier2Cross() {
        assertThat(service.classifyByCode("GBP", "JPY")).isEqualTo(LiquidityTier.TIER_2_MAJOR_CROSS);
    }

    @Test
    void audJpyIsTier2Cross() {
        assertThat(service.classifyByCode("AUD", "JPY")).isEqualTo(LiquidityTier.TIER_2_MAJOR_CROSS);
    }

    @Test
    void audCadIsTier2Cross() {
        assertThat(service.classifyByCode("AUD", "CAD")).isEqualTo(LiquidityTier.TIER_2_MAJOR_CROSS);
    }

    // =========================================================================
    // Exotic and unknown classification
    // =========================================================================

    @Test
    void unknownCurrencyCodeReturnsUnknownTier() {
        LiquidityTier tier = service.classifyByCode("XYZ", "ABC");
        assertThat(tier).isEqualTo(LiquidityTier.UNKNOWN);
    }

    @Test
    void bothUnknownCurrenciesReturnUnknown() {
        assertThat(service.classifyByCode("QQQ", "ZZZ")).isEqualTo(LiquidityTier.UNKNOWN);
    }

    // =========================================================================
    // Liquidity scoring
    // =========================================================================

    @Test
    void eurUsdHasHighLiquidityScore() {
        double score = service.pairLiquidityScore("EUR", "USD");
        assertThat(score).isGreaterThan(80.0);
    }

    @Test
    void unknownPairHasZeroLiquidityScore() {
        double score = service.pairLiquidityScore("XYZ", "ABC");
        assertThat(score).isCloseTo(0.0, within(0.01));
    }

    @Test
    void liquidityScoreBoundedBetween0And100() {
        double score = service.pairLiquidityScore("EUR", "USD");
        assertThat(score).isBetween(0.0, 100.0);
    }

    @Test
    void tier1PairScoreHigherThanTier2() {
        double major = service.pairLiquidityScore("EUR", "USD");
        double cross = service.pairLiquidityScore("EUR", "GBP");
        assertThat(major).isGreaterThan(cross);
    }

    // =========================================================================
    // Currency importance scores
    // =========================================================================

    @Test
    void usdHasHighestImportanceScore() {
        double usd = service.importanceScore("USD");
        double eur = service.importanceScore("EUR");
        double zar = service.importanceScore("ZAR");

        assertThat(usd).isGreaterThan(eur);
        assertThat(eur).isGreaterThan(zar);
    }

    @Test
    void unknownCurrencyHasZeroImportance() {
        assertThat(service.importanceScore("XYZ")).isCloseTo(0.0, within(0.01));
    }

    @Test
    void nullCurrencyHasZeroImportance() {
        assertThat(service.importanceScore(null)).isCloseTo(0.0, within(0.01));
    }

    @Test
    void emptyCurrencyHasZeroImportance() {
        assertThat(service.importanceScore("")).isCloseTo(0.0, within(0.01));
    }

    // =========================================================================
    // Risk multiplier per tier
    // =========================================================================

    @Test
    void tier1HasFullRiskMultiplier() {
        assertThat(LiquidityTier.TIER_1_MAJOR.getRiskMultiplier()).isCloseTo(1.00, within(0.001));
    }

    @Test
    void tier2HasReducedRiskMultiplier() {
        assertThat(LiquidityTier.TIER_2_MAJOR_CROSS.getRiskMultiplier()).isCloseTo(0.75, within(0.001));
    }

    @Test
    void tier3HasHalfRiskMultiplier() {
        assertThat(LiquidityTier.TIER_3_MINOR.getRiskMultiplier()).isCloseTo(0.50, within(0.001));
    }

    @Test
    void tier4HasStronglyReducedRiskMultiplier() {
        assertThat(LiquidityTier.TIER_4_EXOTIC.getRiskMultiplier()).isCloseTo(0.25, within(0.001));
    }

    @Test
    void unknownTierHasLowestRiskMultiplier() {
        assertThat(LiquidityTier.UNKNOWN.getRiskMultiplier()).isCloseTo(0.10, within(0.001));
    }

    // =========================================================================
    // Auto-trading eligibility
    // =========================================================================

    @Test
    void tier1AllowsAutoTrading() {
        assertThat(LiquidityTier.TIER_1_MAJOR.isAutoTradingAllowed()).isTrue();
    }

    @Test
    void tier2AllowsAutoTrading() {
        assertThat(LiquidityTier.TIER_2_MAJOR_CROSS.isAutoTradingAllowed()).isTrue();
    }

    @Test
    void tier3AllowsAutoTrading() {
        assertThat(LiquidityTier.TIER_3_MINOR.isAutoTradingAllowed()).isTrue();
    }

    @Test
    void tier4BlocksAutoTrading() {
        assertThat(LiquidityTier.TIER_4_EXOTIC.isAutoTradingAllowed()).isFalse();
    }

    @Test
    void unknownTierBlocksAutoTrading() {
        assertThat(LiquidityTier.UNKNOWN.isAutoTradingAllowed()).isFalse();
    }

    // =========================================================================
    // Stats
    // =========================================================================

    @Test
    void statsContainsBisSource() {
        MarketStructureService.MarketStructureStats stats = service.getStats();
        assertThat(stats.source()).contains("BIS");
        assertThat(stats.sourceYear()).isEqualTo(2022);
        assertThat(stats.lastRefreshEpochMs()).isPositive();
    }

    // =========================================================================
    // Singleton
    // =========================================================================

    @Test
    void singletonInstanceIsNotNull() {
        assertThat(BisMarketStructureService.getInstance()).isNotNull();
    }

    @Test
    void setInstanceReplacesSingleton() {
        BisMarketStructureService original = BisMarketStructureService.getInstance();
        BisMarketStructureService custom = new BisMarketStructureService();
        BisMarketStructureService.setInstance(custom);
        assertThat(BisMarketStructureService.getInstance()).isSameAs(custom);
        // Restore original for other tests
        BisMarketStructureService.setInstance(original);
    }

    // =========================================================================
    // LiquidityTier enum completeness
    // =========================================================================

    @Test
    void allTiersHaveDisplayName() {
        for (LiquidityTier tier : LiquidityTier.values()) {
            assertThat(tier.getDisplayName()).isNotBlank();
        }
    }

    @Test
    void allTiersHaveDescription() {
        for (LiquidityTier tier : LiquidityTier.values()) {
            assertThat(tier.getDescription()).isNotBlank();
        }
    }

    @Test
    void allTiersHaveStrategyStyle() {
        for (LiquidityTier tier : LiquidityTier.values()) {
            assertThat(tier.getRecommendedStrategyStyle()).isNotBlank();
        }
    }

    @Test
    void riskMultipliersAreInDescendingOrder() {
        assertThat(LiquidityTier.TIER_1_MAJOR.getRiskMultiplier())
                .isGreaterThan(LiquidityTier.TIER_2_MAJOR_CROSS.getRiskMultiplier());
        assertThat(LiquidityTier.TIER_2_MAJOR_CROSS.getRiskMultiplier())
                .isGreaterThan(LiquidityTier.TIER_3_MINOR.getRiskMultiplier());
        assertThat(LiquidityTier.TIER_3_MINOR.getRiskMultiplier())
                .isGreaterThan(LiquidityTier.TIER_4_EXOTIC.getRiskMultiplier());
        assertThat(LiquidityTier.TIER_4_EXOTIC.getRiskMultiplier())
                .isGreaterThan(LiquidityTier.UNKNOWN.getRiskMultiplier());
    }
}
