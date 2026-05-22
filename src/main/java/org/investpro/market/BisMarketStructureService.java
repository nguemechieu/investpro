package org.investpro.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BIS-data-backed implementation of {@link MarketStructureService}.
 * <p>
 * Uses currency importance weights from the BIS Triennial Central Bank Survey
 * to classify FX pairs into liquidity tiers and produce risk multipliers.
 * <p>
 * This service is thread-safe and uses a lazy-initialized singleton.
 * Call {@link #getInstance()} from anywhere in the application.
 */
@Slf4j
public class BisMarketStructureService implements MarketStructureService {

    // =========================================================================
    // Singleton
    // =========================================================================

    private static final AtomicReference<BisMarketStructureService> INSTANCE =
            new AtomicReference<>(new BisMarketStructureService());

    /** Returns the application-wide singleton instance. */
    public static BisMarketStructureService getInstance() {
        return INSTANCE.get();
    }

    /**
     * Replace the singleton instance (intended for testing or hot-reload).
     */
    public static void setInstance(@NotNull BisMarketStructureService service) {
        INSTANCE.set(Objects.requireNonNull(service));
    }

    // =========================================================================
    // Dataset metadata
    // =========================================================================

    private static final String BIS_SOURCE = "BIS Triennial Central Bank Survey";
    private static final int BIS_SOURCE_YEAR = 2022;

    // =========================================================================
    // BIS currency importance scores (0–100 scale, USD = 100)
    // Derived from BIS 2022 FX turnover proportions.
    // =========================================================================

    private static final Map<String, Double> CURRENCY_IMPORTANCE = new HashMap<>();

    static {
        CURRENCY_IMPORTANCE.put("USD", 100.0); // 88.5% of one side
        CURRENCY_IMPORTANCE.put("EUR",  85.0); // 30.5%
        CURRENCY_IMPORTANCE.put("JPY",  70.0); // 16.7%
        CURRENCY_IMPORTANCE.put("GBP",  65.0); // 12.9%
        CURRENCY_IMPORTANCE.put("CNY",  45.0); // 7.0% – rising importance
        CURRENCY_IMPORTANCE.put("CNH",  45.0); // offshore CNY
        CURRENCY_IMPORTANCE.put("AUD",  40.0); // 6.4%
        CURRENCY_IMPORTANCE.put("CAD",  40.0); // 6.2%
        CURRENCY_IMPORTANCE.put("CHF",  38.0); // 5.2%
        CURRENCY_IMPORTANCE.put("HKD",  30.0); // 2.7%
        CURRENCY_IMPORTANCE.put("SGD",  27.0); // 2.4%
        CURRENCY_IMPORTANCE.put("SEK",  22.0); // 2.2%
        CURRENCY_IMPORTANCE.put("NOK",  20.0); // 1.7%
        CURRENCY_IMPORTANCE.put("NZD",  20.0); // 2.6%
        CURRENCY_IMPORTANCE.put("MXN",  18.0); // 2.2%
        CURRENCY_IMPORTANCE.put("DKK",  15.0); // 1.1%
        CURRENCY_IMPORTANCE.put("ZAR",  12.0); // 1.0%
        CURRENCY_IMPORTANCE.put("TRY",  10.0); // 1.4%
        CURRENCY_IMPORTANCE.put("INR",  10.0); // 1.6%
        CURRENCY_IMPORTANCE.put("BRL",   8.0); // 1.2%
        CURRENCY_IMPORTANCE.put("KRW",   8.0); // 1.9%
        CURRENCY_IMPORTANCE.put("PLN",   7.0); // 0.7%
        CURRENCY_IMPORTANCE.put("HUF",   5.0);
        CURRENCY_IMPORTANCE.put("CZK",   5.0);
        CURRENCY_IMPORTANCE.put("CLP",   4.0);
        CURRENCY_IMPORTANCE.put("TWD",   6.0);
        CURRENCY_IMPORTANCE.put("PHP",   3.0);
        CURRENCY_IMPORTANCE.put("IDR",   3.0);
        CURRENCY_IMPORTANCE.put("THB",   3.0);
        CURRENCY_IMPORTANCE.put("MYR",   3.0);
        CURRENCY_IMPORTANCE.put("RUB",   2.0);
    }

    // =========================================================================
    // Tier 1 — G7 major pairs (USD + major currency)
    // =========================================================================

    private static final Set<String> TIER1_PAIRS = Set.of(
            "EURUSD", "USDJPY", "GBPUSD", "USDCHF",
            "AUDUSD", "USDCAD", "NZDUSD",
            // inverted forms also classified as Tier 1 via symmetric lookup
            "USDEUR", "JPYUSD", "USDGBP", "CHFUSD",
            "USDAUD", "CADUSD", "USDNZD"
    );

    // =========================================================================
    // Tier 2 — Major crosses (two major non-USD currencies)
    // =========================================================================

    private static final Set<String> TIER2_PAIRS = Set.of(
            // EUR crosses
            "EURGBP", "EURJPY", "EURAUD", "EURCAD", "EURCHF", "EURNZD",
            "GBPEUR", "JPYEUR", "AUDEUR", "CADEUR", "CHFEUR", "NZDEUR",
            // GBP crosses
            "GBPJPY", "GBPAUD", "GBPCAD", "GBPCHF", "GBPNZD",
            "JPYGBP", "AUDGBP", "CADGBP", "CHFGBP", "NZDGBP",
            // JPY crosses
            "AUDJPY", "CADJPY", "CHFJPY", "NZDJPY",
            "JPYAUD", "JPYCAD", "JPYCHF", "JPYNZD",
            // Other liquid crosses
            "AUDCAD", "AUDCHF", "AUDNZD",
            "CADCHF", "CADNZD", "NZDCAD", "NZDCHF",
            "CHFNOK", "EURNOK", "EURSEK", "EURDKK",
            "USDCNH", "USDCNY", "USDHKD", "USDSGD"
    );

    // =========================================================================
    // Session mapping by primary currency
    // =========================================================================

    private static final Map<String, String> CURRENCY_SESSION = new HashMap<>();

    static {
        // New York
        for (String c : List.of("USD", "CAD", "MXN", "BRL")) {
            CURRENCY_SESSION.put(c, "New York");
        }
        // London / European
        for (String c : List.of("EUR", "GBP", "CHF", "SEK", "NOK", "DKK", "PLN", "HUF", "CZK", "ZAR", "TRY")) {
            CURRENCY_SESSION.put(c, "London");
        }
        // Tokyo
        for (String c : List.of("JPY", "CNY", "CNH", "HKD", "SGD", "KRW", "TWD", "PHP", "IDR", "THB", "MYR")) {
            CURRENCY_SESSION.put(c, "Tokyo");
        }
        // Sydney
        for (String c : List.of("AUD", "NZD")) {
            CURRENCY_SESSION.put(c, "Sydney");
        }
        // India / Russia
        for (String c : List.of("INR", "RUB")) {
            CURRENCY_SESSION.put(c, "Other");
        }
    }

    // =========================================================================
    // Cache
    // =========================================================================

    private final Map<String, MarketStructureProfile> cache = new ConcurrentHashMap<>();
    private volatile long lastRefreshEpochMs = Instant.now().toEpochMilli();
    private volatile int unknownCount = 0;

    // =========================================================================
    // MarketStructureService implementation
    // =========================================================================

    @Override
    public @NotNull MarketStructureProfile getProfile(@NotNull TradePair pair) {
        String key = cacheKey(pair);
        return cache.computeIfAbsent(key, k -> buildProfile(pair));
    }

    @Override
    public @NotNull LiquidityTier classifyLiquidityTier(@NotNull TradePair pair) {
        return classifyByCode(baseCode(pair), quoteCode(pair));
    }

    @Override
    public double getLiquidityRiskMultiplier(@NotNull TradePair pair) {
        return classifyLiquidityTier(pair).getRiskMultiplier();
    }

    @Override
    public boolean isRecommendedForAutoTrading(@NotNull TradePair pair) {
        return classifyLiquidityTier(pair).isAutoTradingAllowed();
    }

    @Override
    public @NotNull List<TradePair> rankPairsByMarketStructure(@NotNull List<TradePair> tradeablePairs) {
        return tradeablePairs.stream()
                .sorted(Comparator.comparingDouble(
                        (TradePair p) -> getProfile(p).pairLiquidityScore()).reversed())
                .toList();
    }

    @Override
    public @NotNull MarketStructureStats getStats() {
        return new MarketStructureStats(
                BIS_SOURCE,
                BIS_SOURCE_YEAR,
                cache.size(),
                unknownCount,
                lastRefreshEpochMs);
    }

    // =========================================================================
    // Classification logic — package-private for unit testing
    // =========================================================================

    /**
     * Classify by raw currency codes. Package-private to enable direct unit testing
     * without needing a TradePair (which requires DB access).
     */
    LiquidityTier classifyByCode(@NotNull String base, @NotNull String quote) {
        String normalized = (base.toUpperCase() + quote.toUpperCase());
        if (TIER1_PAIRS.contains(normalized)) {
            return LiquidityTier.TIER_1_MAJOR;
        }
        if (TIER2_PAIRS.contains(normalized)) {
            return LiquidityTier.TIER_2_MAJOR_CROSS;
        }

        // Score-based fallback classification
        double baseScore = importanceScore(base);
        double quoteScore = importanceScore(quote);
        double combinedScore = (baseScore + quoteScore) / 2.0;

        if (combinedScore >= 55.0) {
            return LiquidityTier.TIER_2_MAJOR_CROSS;
        } else if (combinedScore >= 30.0) {
            return LiquidityTier.TIER_3_MINOR;
        } else if (combinedScore >= 5.0) {
            return LiquidityTier.TIER_4_EXOTIC;
        } else {
            return LiquidityTier.UNKNOWN;
        }
    }

    /**
     * Get currency importance score. Package-private for unit testing.
     */
    double importanceScore(@Nullable String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return 0.0;
        }
        return CURRENCY_IMPORTANCE.getOrDefault(currencyCode.toUpperCase().trim(), 0.0);
    }

    /**
     * Calculate pair liquidity score (0–100). Package-private for unit testing.
     */
    double pairLiquidityScore(@NotNull String base, @NotNull String quote) {
        double baseScore = importanceScore(base);
        double quoteScore = importanceScore(quote);
        double combined = (baseScore + quoteScore) / 2.0;

        // Tier 1 bonus
        String normalized = base.toUpperCase() + quote.toUpperCase();
        if (TIER1_PAIRS.contains(normalized)) {
            combined = Math.min(100.0, combined + 20.0);
        } else if (TIER2_PAIRS.contains(normalized)) {
            combined = Math.min(100.0, combined + 8.0);
        }

        return Math.max(0.0, Math.min(100.0, combined));
    }

    // =========================================================================
    // Profile builder
    // =========================================================================

    private @NotNull MarketStructureProfile buildProfile(@NotNull TradePair pair) {
        String base = baseCode(pair);
        String quote = quoteCode(pair);

        LiquidityTier tier = classifyByCode(base, quote);
        double baseScore = importanceScore(base);
        double quoteScore = importanceScore(quote);
        double pairScore = pairLiquidityScore(base, quote);
        double spreadRisk = computeSpreadRisk(tier);
        String session = resolveSession(base, quote);
        String strategy = tier.getRecommendedStrategyStyle();
        boolean autoTrade = tier.isAutoTradingAllowed();
        String warning = buildWarning(tier, base, quote);

        if (tier == LiquidityTier.UNKNOWN) {
            unknownCount++;
        }

        log.debug("MarketStructure: {} \u2192 {} score={} session={} auto={}",
                pair, tier.getDisplayName(), String.format("%.1f", pairScore), session, autoTrade);

        return new MarketStructureProfile(
                pair,
                tier,
                baseScore,
                quoteScore,
                pairScore,
                spreadRisk,
                sessionActivityScore(session),
                session,
                strategy,
                autoTrade,
                warning,
                BIS_SOURCE,
                BIS_SOURCE_YEAR
        );
    }

    private double computeSpreadRisk(@NotNull LiquidityTier tier) {
        return switch (tier) {
            case TIER_1_MAJOR -> 0.10;
            case TIER_2_MAJOR_CROSS -> 0.25;
            case TIER_3_MINOR -> 0.50;
            case TIER_4_EXOTIC -> 0.80;
            case UNKNOWN -> 1.00;
        };
    }

    private double sessionActivityScore(@NotNull String session) {
        return switch (session) {
            case "London", "New York" -> 1.00;
            case "Overlap" -> 1.00;
            case "Tokyo" -> 0.80;
            case "Sydney" -> 0.60;
            default -> 0.40;
        };
    }

    private @NotNull String resolveSession(@NotNull String base, @NotNull String quote) {
        String baseSession = CURRENCY_SESSION.getOrDefault(base.toUpperCase(), "Other");
        String quoteSession = CURRENCY_SESSION.getOrDefault(quote.toUpperCase(), "Other");

        if (baseSession.equals(quoteSession)) {
            return baseSession;
        }
        if (Set.of(baseSession, quoteSession).containsAll(Set.of("London", "New York"))) {
            return "London/New York Overlap";
        }
        if ("USD".equalsIgnoreCase(base) || "USD".equalsIgnoreCase(quote)) {
            return "New York";
        }
        if (Set.of("EUR", "GBP", "CHF").contains(base.toUpperCase())
                || Set.of("EUR", "GBP", "CHF").contains(quote.toUpperCase())) {
            return "London";
        }
        return baseSession.equals("Other") ? quoteSession : baseSession;
    }

    private @NotNull String buildWarning(@NotNull LiquidityTier tier,
                                         @NotNull String base, @NotNull String quote) {
        return switch (tier) {
            case TIER_4_EXOTIC -> "Exotic pair %s/%s: thin liquidity, high spread and gap risk. Reduce size.".formatted(base, quote);
            case UNKNOWN -> "No market structure data for %s/%s. Paper trade only.".formatted(base, quote);
            default -> "";
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static @NotNull String cacheKey(@NotNull TradePair pair) {
        return baseCode(pair) + "_" + quoteCode(pair);
    }

    private static @NotNull String baseCode(@NotNull TradePair pair) {
        String code = pair.getBaseCode();
        return code != null ? code.toUpperCase() : "???";
    }

    private static @NotNull String quoteCode(@NotNull TradePair pair) {
        String code = pair.getCounterCode();
        return code != null ? code.toUpperCase() : "???";
    }
}
