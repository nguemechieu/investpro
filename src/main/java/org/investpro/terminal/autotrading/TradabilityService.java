package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.TradingStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TradabilityService {

    private final List<TradabilityCheck> checks;

    public TradabilityService() {
        this(defaultChecks());
    }

    public TradabilityService(List<TradabilityCheck> checks) {
        this.checks = checks == null || checks.isEmpty() ? defaultChecks() : List.copyOf(checks);
    }

    public TradabilityResult evaluate(TradabilityContext context) {
        if (context == null || context.instrument() == null) {
            return new TradabilityResult(
                    null,
                    false,
                    AutoTradeSymbolState.ERROR,
                    TradabilityFailureReason.UNKNOWN,
                    "Tradability context or instrument is missing",
                    Double.POSITIVE_INFINITY,
                    0,
                    0.0,
                    Instant.now(),
                    java.util.Map.of());
        }

        List<TradabilityResult> evaluated = new ArrayList<>();
        for (TradabilityCheck check : checks) {
            Optional<TradabilityResult> result = check.evaluate(context);
            result.ifPresent(evaluated::add);
            if (result.isPresent() && !result.get().tradable()) {
                return result.get();
            }
        }
        return TradabilityResult.merge(context, evaluated);
    }

    public static List<TradabilityCheck> defaultChecks() {
        return List.of(
                context -> failIf(!context.policy().autoTradingEnabled(), context,
                        TradabilityFailureReason.AUTO_TRADING_DISABLED, "Auto-trading is disabled"),
                context -> failIf(context.connectionState() != ExchangeConnectionState.CONNECTED, context,
                        TradabilityFailureReason.EXCHANGE_DISCONNECTED, "Exchange is not connected"),
                context -> failIf(context.policy().waitForReconciliationBeforeTrading() && !context.reconciliationComplete(), context,
                        TradabilityFailureReason.RECONCILIATION_REQUIRED, "Reconciliation must complete before trading"),
                context -> failIf(context.disabledByUser(), context,
                        TradabilityFailureReason.SYMBOL_DISABLED_BY_USER, "Symbol disabled by user"),
                context -> failIf(context.policy().symbolBlocked(context.instrument().id().symbol()), context,
                        TradabilityFailureReason.SYMBOL_BLOCKLISTED, "Symbol is blocklisted"),
                TradabilityService::assetClassAllowed,
                TradabilityService::tradingStatusActive,
                TradabilityService::instrumentIncrementsKnown,
                TradabilityService::marketDataAvailable,
                new SpreadFilter(),
                new LiquidityFilter(),
                new VolumeFilter(),
                context -> failIf(!context.accountSupportsProduct(), context,
                        TradabilityFailureReason.ACCOUNT_UNSUPPORTED_PRODUCT, "Account does not support this product"),
                context -> failIf(!context.permissionsAllowProduct(), context,
                        TradabilityFailureReason.PERMISSION_DENIED, "Broker permissions do not allow this product"),
                TradabilityService::stellarChecks,
                context -> failIf(!context.strategyAssigned(), context,
                        TradabilityFailureReason.STRATEGY_NOT_ASSIGNED, "No strategy assigned or auto-selected"),
                context -> failIf(!context.strategyDataReady(), context,
                        TradabilityFailureReason.STRATEGY_DATA_INSUFFICIENT, "Strategy does not have enough data"),
                TradabilityService::signalCheck,
                context -> failIf(!context.riskAllowed(), context,
                        TradabilityFailureReason.RISK_REJECTED, "Risk engine rejected this symbol"),
                context -> failIf(context.duplicateOrderExists(), context,
                        TradabilityFailureReason.DUPLICATE_ORDER_EXISTS, "Duplicate/conflicting order exists"),
                context -> failIf(context.openOrders() >= context.policy().maxOpenOrdersPerSymbol(), context,
                        TradabilityFailureReason.OPEN_ORDER_LIMIT_REACHED, "Open order limit reached"),
                context -> failIf(context.openPositions() >= context.policy().maxOpenPositionsPerSymbol(), context,
                        TradabilityFailureReason.OPEN_POSITION_LIMIT_REACHED, "Open position limit reached"));
    }

    private static Optional<TradabilityResult> assetClassAllowed(TradabilityContext context) {
        AssetClass assetClass = context.instrument().assetClass();
        boolean allowed = context.policy().allowedAssetClasses().contains(assetClass);
        return failIf(!allowed, context, TradabilityFailureReason.ASSET_CLASS_NOT_ALLOWED,
                "Asset class is not allowed: " + assetClass);
    }

    private static Optional<TradabilityResult> tradingStatusActive(TradabilityContext context) {
        boolean active = context.instrument().active() && context.instrument().tradingStatus() == TradingStatus.ACTIVE;
        return failIf(!active, context, TradabilityFailureReason.SYMBOL_NOT_ACTIVE,
                "Symbol trading status is not active");
    }

    private static Optional<TradabilityResult> instrumentIncrementsKnown(TradabilityContext context) {
        if (context.instrument().minOrderSize().signum() <= 0) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.MIN_ORDER_SIZE_UNKNOWN));
        }
        if (context.instrument().tickSize().signum() <= 0 || context.instrument().quoteIncrement().signum() <= 0) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.TICK_SIZE_UNKNOWN));
        }
        if (context.instrument().lotSize().signum() <= 0 || context.instrument().baseIncrement().signum() <= 0) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.LOT_SIZE_UNKNOWN));
        }
        return Optional.empty();
    }

    private static Optional<TradabilityResult> marketDataAvailable(TradabilityContext context) {
        if (context.marketQuality().tick() == null) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.MARKET_DATA_MISSING));
        }
        if (context.policy().pauseOnDataStale()) {
            Duration age = Duration.between(context.marketQuality().observedAt(), context.checkedAt());
            if (age.compareTo(context.policy().marketDataStaleAfter()) > 0) {
                return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.MARKET_DATA_STALE));
            }
        }
        return Optional.empty();
    }

    private static Optional<TradabilityResult> signalCheck(TradabilityContext context) {
        if (context.latestSignal() == null || "HOLD".equalsIgnoreCase(context.latestSignal().action())) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STRATEGY_SIGNAL_HOLD));
        }
        return Optional.empty();
    }

    private static Optional<TradabilityResult> stellarChecks(TradabilityContext context) {
        if (context.instrument().assetClass() != AssetClass.CRYPTO_STELLAR) {
            return Optional.empty();
        }
        boolean issuerAware = boolMetadata(context, "stellar.issuerAware");
        if (!issuerAware && !context.policy().stellarAllowUnknownIssuers()) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_ISSUER_UNKNOWN));
        }
        boolean baseTrusted = boolMetadata(context, "stellar.baseTrusted");
        boolean quoteTrusted = boolMetadata(context, "stellar.quoteTrusted");
        if (context.policy().stellarRequireVerifiedDomain() && (!baseTrusted || !quoteTrusted)) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_ISSUER_UNVERIFIED));
        }
        boolean baseTrustlineRequired = boolMetadata(context, "stellar.baseTrustlineRequired");
        boolean quoteTrustlineRequired = boolMetadata(context, "stellar.quoteTrustlineRequired");
        boolean baseTrustlineExists = !baseTrustlineRequired || boolMetadata(context, "stellar.baseTrustlineExists");
        boolean quoteTrustlineExists = !quoteTrustlineRequired || boolMetadata(context, "stellar.quoteTrustlineExists");
        if ((baseTrustlineRequired || quoteTrustlineRequired)
                && (!baseTrustlineExists || !quoteTrustlineExists)
                && !context.policy().stellarAllowAutoTrustline()) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_TRUSTLINE_REQUIRED));
        }
        if (boolMetadata(context, "stellar.assetSuspicious")) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_ASSET_SUSPICIOUS));
        }
        if (context.marketQuality().liquidityScore() < context.policy().stellarMinLiquidityScore()) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_LIQUIDITY_TOO_LOW));
        }
        if (context.marketQuality().spreadPercent() > context.policy().stellarMaxSpreadPercent()) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_SPREAD_TOO_WIDE));
        }
        boolean routeAvailable = boolMetadata(context, "stellar.tradeable");
        if (!routeAvailable) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_ROUTE_MISSING));
        }
        boolean reversed = boolMetadata(context, "stellar.usingInvertedOrderBook");
        if (reversed && !context.policy().stellarAllowReversedPairs()) {
            return Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.STELLAR_ROUTE_MISSING,
                    "Reversed Stellar pairs are disabled"));
        }
        return Optional.empty();
    }

    private static Optional<TradabilityResult> failIf(
            boolean condition,
            TradabilityContext context,
            TradabilityFailureReason reason,
            String message
    ) {
        return condition ? Optional.of(TradabilityResult.fail(context, reason, message)) : Optional.empty();
    }

    private static boolean boolMetadata(TradabilityContext context, String key) {
        Object value = context.instrument().metadata().getOrDefault(key, context.metadata().get(key));
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value).toLowerCase(Locale.ROOT));
    }
}
