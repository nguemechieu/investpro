package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.AssetClass;

import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SymbolTradingPolicy(
        boolean autoTradingEnabled,
        boolean tradeAllTradeableSymbols,
        boolean requireManualApprovalBeforeFirstLiveTrade,
        int maxSymbolsPerExchange,
        int maxConcurrentActiveSymbols,
        Duration scanInterval,
        Duration recheckTradabilityInterval,
        boolean subscribeMarketDataForEligibleSymbols,
        boolean allowNewPositions,
        boolean allowClosePositions,
        boolean allowReducePositions,
        boolean allowReversePositions,
        Set<String> blockedSymbols,
        Set<AssetClass> allowedAssetClasses,
        double minVolume24h,
        double maxSpreadPercent,
        int minLiquidityScore,
        int maxOpenOrdersPerSymbol,
        int maxOpenPositionsPerSymbol,
        boolean waitForReconciliationBeforeTrading,
        boolean pauseOnConnectionLoss,
        boolean pauseOnDataStale,
        Duration marketDataStaleAfter,
        boolean stellarAllowUnknownIssuers,
        boolean stellarRequireVerifiedDomain,
        boolean stellarAllowAutoTrustline,
        int stellarMinLiquidityScore,
        double stellarMaxSpreadPercent,
        boolean stellarAllowReversedPairs
) {
    public SymbolTradingPolicy {
        blockedSymbols = blockedSymbols == null ? Set.of() : Set.copyOf(blockedSymbols);
        allowedAssetClasses = allowedAssetClasses == null || allowedAssetClasses.isEmpty()
                ? Set.of(AssetClass.CRYPTO, AssetClass.FOREX, AssetClass.EQUITY, AssetClass.ETF)
                : Set.copyOf(allowedAssetClasses);
        scanInterval = scanInterval == null || scanInterval.isNegative() ? Duration.ofSeconds(60) : scanInterval;
        recheckTradabilityInterval = recheckTradabilityInterval == null || recheckTradabilityInterval.isNegative()
                ? Duration.ofSeconds(300)
                : recheckTradabilityInterval;
        marketDataStaleAfter = marketDataStaleAfter == null || marketDataStaleAfter.isNegative()
                ? Duration.ofSeconds(15)
                : marketDataStaleAfter;
        maxConcurrentActiveSymbols = Math.max(0, maxConcurrentActiveSymbols);
        minLiquidityScore = Math.max(0, Math.min(100, minLiquidityScore));
        stellarMinLiquidityScore = Math.max(0, Math.min(100, stellarMinLiquidityScore));
    }

    public static SymbolTradingPolicy defaults() {
        return new SymbolTradingPolicy(
                false,
                true,
                true,
                0,
                25,
                Duration.ofSeconds(60),
                Duration.ofSeconds(300),
                true,
                true,
                true,
                true,
                false,
                Set.of(),
                Set.of(AssetClass.CRYPTO, AssetClass.FOREX, AssetClass.EQUITY, AssetClass.ETF),
                100000.0,
                0.30,
                60,
                1,
                1,
                true,
                true,
                true,
                Duration.ofSeconds(15),
                false,
                true,
                false,
                70,
                0.50,
                true);
    }

    public static SymbolTradingPolicy fromProperties(Properties properties) {
        Properties p = properties == null ? new Properties() : properties;
        SymbolTradingPolicy d = defaults();
        return new SymbolTradingPolicy(
                bool(p, "investpro.autotrading.enabled", d.autoTradingEnabled),
                bool(p, "investpro.autotrading.tradeAllTradeableSymbols", d.tradeAllTradeableSymbols),
                bool(p, "investpro.autotrading.requireManualApprovalBeforeFirstLiveTrade", d.requireManualApprovalBeforeFirstLiveTrade),
                integer(p, "investpro.autotrading.maxSymbolsPerExchange", d.maxSymbolsPerExchange),
                integer(p, "investpro.autotrading.maxConcurrentActiveSymbols", d.maxConcurrentActiveSymbols),
                Duration.ofSeconds(integer(p, "investpro.autotrading.scanIntervalSeconds", (int) d.scanInterval.toSeconds())),
                Duration.ofSeconds(integer(p, "investpro.autotrading.recheckTradabilitySeconds", (int) d.recheckTradabilityInterval.toSeconds())),
                bool(p, "investpro.autotrading.subscribeMarketDataForEligibleSymbols", d.subscribeMarketDataForEligibleSymbols),
                bool(p, "investpro.autotrading.allowNewPositions", d.allowNewPositions),
                bool(p, "investpro.autotrading.allowClosePositions", d.allowClosePositions),
                bool(p, "investpro.autotrading.allowReducePositions", d.allowReducePositions),
                bool(p, "investpro.autotrading.allowReversePositions", d.allowReversePositions),
                strings(p.getProperty("investpro.autotrading.blockSymbols", "")),
                assetClasses(p.getProperty("investpro.autotrading.allowAssetClasses", "CRYPTO,FOREX,STOCK,ETF")),
                decimal(p, "investpro.autotrading.minVolume24h", d.minVolume24h),
                decimal(p, "investpro.autotrading.maxSpreadPercent", d.maxSpreadPercent),
                integer(p, "investpro.autotrading.minLiquidityScore", d.minLiquidityScore),
                integer(p, "investpro.autotrading.maxOpenOrdersPerSymbol", d.maxOpenOrdersPerSymbol),
                integer(p, "investpro.autotrading.maxOpenPositionsPerSymbol", d.maxOpenPositionsPerSymbol),
                bool(p, "investpro.autotrading.waitForReconciliationBeforeTrading", d.waitForReconciliationBeforeTrading),
                bool(p, "investpro.autotrading.pauseOnConnectionLoss", d.pauseOnConnectionLoss),
                bool(p, "investpro.autotrading.pauseOnDataStale", d.pauseOnDataStale),
                Duration.ofSeconds(integer(p, "investpro.autotrading.marketDataStaleSeconds", (int) d.marketDataStaleAfter.toSeconds())),
                bool(p, "investpro.stellar.allowUnknownIssuers", d.stellarAllowUnknownIssuers),
                bool(p, "investpro.stellar.requireVerifiedDomain", d.stellarRequireVerifiedDomain),
                bool(p, "investpro.stellar.allowAutoTrustline", d.stellarAllowAutoTrustline),
                integer(p, "investpro.stellar.minLiquidityScore", d.stellarMinLiquidityScore),
                decimal(p, "investpro.stellar.maxSpreadPercent", d.stellarMaxSpreadPercent),
                bool(p, "investpro.stellar.allowReversedPairs", d.stellarAllowReversedPairs));
    }

    public boolean symbolBlocked(String symbol) {
        String normalized = normalize(symbol);
        return blockedSymbols.stream().map(SymbolTradingPolicy::normalize).anyMatch(normalized::equals);
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static double decimal(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static Set<String> strings(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Stream.of(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<AssetClass> assetClasses(String csv) {
        return strings(csv).stream()
                .map(value -> "STOCK".equalsIgnoreCase(value) ? "EQUITY" : value)
                .map(value -> {
                    try {
                        return AssetClass.valueOf(value.trim().toUpperCase(Locale.ROOT));
                    } catch (RuntimeException exception) {
                        return AssetClass.UNKNOWN;
                    }
                })
                .filter(value -> value != AssetClass.UNKNOWN)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '/').replace('_', '/');
    }
}
