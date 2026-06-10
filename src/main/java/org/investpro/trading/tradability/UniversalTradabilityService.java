package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.stellar.StellarNetwork;
import org.investpro.exchange.stellar.StellarPairIdentity;
import org.investpro.exchange.stellar.StellarTradabilityEvaluator;
import org.investpro.market.MarketDataEngine;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class UniversalTradabilityService {

    private static final long MARKET_DATA_TTL_MS = 5 * 60_000L;
    private static final long WATCHLIST_TTL_MS = 10 * 60_000L;
    private static final long BACKTESTING_TTL_MS = 30 * 60_000L;
    private static final long PAPER_TRADING_TTL_MS = 2 * 60_000L;
    private static final long LIVE_TRADING_TTL_MS = 60_000L;
    private static final long BOT_TRADING_TTL_MS = 30_000L;
    private static final long ORDER_SUBMISSION_TTL_MS = 10_000L;

    private final Exchange exchange;
    private final MarketDataEngine marketDataEngine;
    private final StellarTradabilityEvaluator stellarTradabilityEvaluator;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<TradabilityScope, Long> ttlByScopeMs = new EnumMap<>(TradabilityScope.class);

    private record CacheEntry(SymbolTradability tradability, long expiresAtMs) {

        private boolean fresh() {
            return System.currentTimeMillis() < expiresAtMs;
        }
    }

    public UniversalTradabilityService(Exchange exchange, MarketDataEngine marketDataEngine) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.marketDataEngine = marketDataEngine;
        this.stellarTradabilityEvaluator = exchange instanceof StellarNetwork stellarNetwork
                ? new StellarTradabilityEvaluator(stellarNetwork)
                : null;

        configureTtls();
    }

    private void configureTtls() {
        ttlByScopeMs.put(TradabilityScope.MARKET_DATA, MARKET_DATA_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.WATCHLIST, WATCHLIST_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.BACKTESTING, BACKTESTING_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.PAPER_TRADING, PAPER_TRADING_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.LIVE_TRADING, LIVE_TRADING_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.BOT_TRADING, BOT_TRADING_TTL_MS);
        ttlByScopeMs.put(TradabilityScope.ORDER_SUBMISSION, ORDER_SUBMISSION_TTL_MS);
    }

    public CompletableFuture<SymbolTradability> getTradability(TradePair pair) {
        return getTradability(pair, TradabilityScope.LIVE_TRADING, false);
    }

    public CompletableFuture<SymbolTradability> getTradability(
            TradePair pair,
            TradabilityScope scope,
            boolean forceRefresh
    ) {
        if (pair == null) {
            return CompletableFuture.completedFuture(
                    unknownStatus(null, TradabilityStatus.UNKNOWN, "Trade pair is null")
            );
        }

        TradabilityScope safeScope = normalizeScope(scope);

        SymbolTradability cached = getCached(pair, safeScope);
        if (!forceRefresh && cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<SymbolTradability> lookup = stellarTradabilityEvaluator != null
                ? stellarTradabilityEvaluator.evaluate(pair, safeScope, forceRefresh)
                : exchange.fetchTradabilityStatus(pair);

        return lookup
                .exceptionally(exception -> {
                    log.warn(
                            "Tradability fetch failed for {} on {}: {}",
                            symbolOf(pair),
                            exchange.getName(),
                            exception.getMessage()
                    );

                    return unknownStatus(
                            pair,
                            TradabilityStatus.UNKNOWN,
                            "Tradability lookup failed"
                    );
                })
                .thenApply(status -> {
                    SymbolTradability resolved = resolveStatus(
                            pair,
                            status,
                            "Tradability not provided by adapter"
                    );

                    cacheStatus(pair, safeScope, resolved);
                    logSkipIfNeeded(resolved);

                    return resolved;
                });
    }

    public CompletableFuture<List<SymbolTradability>> getTradability(List<TradePair> pairs) {
        List<TradePair> sanitized = sanitizePairs(pairs);
        if (sanitized.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (stellarTradabilityEvaluator != null) {
            List<CompletableFuture<SymbolTradability>> futures = sanitized.stream()
                    .map(pair -> getTradability(pair, TradabilityScope.WATCHLIST, false))
                    .toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(unused -> futures.stream().map(CompletableFuture::join).toList());
        }

        return exchange.fetchTradabilityStatus(sanitized)
                .exceptionally(exception -> {
                    log.warn(
                            "Batch tradability fetch failed for {} symbols on {}: {}",
                            sanitized.size(),
                            exchange.getName(),
                            exception.getMessage()
                    );

                    return List.of();
                })
                .thenApply(statuses -> resolveBatchStatuses(sanitized, statuses));
    }

    public @NonNull @Unmodifiable List<TradePair> filterForMarketWatch(List<TradePair> pairs) {
        return sanitizePairs(pairs)
                .stream()
                .filter(this::canDisplayInMarketWatch)
                .toList();
    }

    public List<TradePair> filterForBotTrading(List<TradePair> pairs) {
        return sanitizePairs(pairs)
                .stream()
                .filter(this::canUseForBotTrading)
                .toList();
    }

    public List<TradePair> filterForLiveTrading(List<TradePair> pairs) {
        return sanitizePairs(pairs)
                .stream()
                .filter(this::canUseForLiveTrading)
                .toList();
    }

    public boolean canSubmitOrder(TradePair pair, OpenOrder.OrderType orderType) {
        if (pair == null) {
            return false;
        }

        SymbolTradability status = getCached(pair, TradabilityScope.ORDER_SUBMISSION);
        return canSubmitOrder(status, orderType);
    }

    public CompletableFuture<Boolean> canSubmitOrderAsync(TradePair pair, OpenOrder.OrderType orderType) {
        return getTradability(pair, TradabilityScope.ORDER_SUBMISSION, true)
                .thenApply(status -> canSubmitOrder(status, orderType));
    }

    public CompletableFuture<SymbolTradability> recheckForOrderSubmission(TradePair pair) {
        return getTradability(pair, TradabilityScope.ORDER_SUBMISSION, true);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public void invalidate(TradePair pair) {
        if (pair == null) {
            return;
        }

        String symbol = symbolOf(pair);
        cache.keySet().removeIf(key -> key.startsWith(symbol + "::"));
    }

    private List<SymbolTradability> resolveBatchStatuses(
            List<TradePair> requestedPairs,
            List<SymbolTradability> statuses
    ) {
        Map<String, SymbolTradability> bySymbol = new ConcurrentHashMap<>();

        if (statuses != null) {
            for (SymbolTradability status : statuses) {
                if (status == null || status.tradePair() == null) {
                    continue;
                }

                bySymbol.put(symbolOf(status.tradePair()), status);
            }
        }

        List<SymbolTradability> resolved = new ArrayList<>(requestedPairs.size());

        for (TradePair pair : requestedPairs) {
            SymbolTradability status = bySymbol.get(symbolOf(pair));

            SymbolTradability finalStatus = resolveStatus(
                    pair,
                    status,
                    "Tradability missing in batch response"
            );

            cacheBatchStatus(pair, finalStatus);
            logSkipIfNeeded(finalStatus);
            resolved.add(finalStatus);
        }

        return resolved;
    }

    private boolean canDisplayInMarketWatch(TradePair pair) {
        SymbolTradability status = snapshot(pair, TradabilityScope.WATCHLIST);

        /*
         * Market Watch is intentionally permissive:
         * if status is still loading, show the symbol temporarily.
         */
        return status == null || status.canBeDisplayedInMarketWatch();
    }

    private boolean canUseForBotTrading(TradePair pair) {
        SymbolTradability status = snapshot(pair, TradabilityScope.BOT_TRADING);

        /*
         * Bot trading is intentionally conservative:
         * no confirmed tradability means no bot trading.
         */
        return status != null && status.canBeUsedForBotTrading();
    }

    private boolean canUseForLiveTrading(TradePair pair) {
        SymbolTradability status = snapshot(pair, TradabilityScope.LIVE_TRADING);

        /*
         * Live trading is intentionally conservative.
         */
        return status != null && status.isFullyTradable();
    }

    private boolean canSubmitOrder(SymbolTradability status, OpenOrder.OrderType orderType) {
        if (status == null || !status.orderSubmissionAllowed()) {
            return false;
        }

        return supportsOrderType(status, orderType);
    }

    private SymbolTradability snapshot(TradePair pair, TradabilityScope scope) {
        if (pair == null) {
            return null;
        }

        TradabilityScope safeScope = normalizeScope(scope);
        SymbolTradability cached = getCached(pair, safeScope);

        if (cached != null) {
            return cached;
        }

        refreshAsync(pair, safeScope);
        return null;
    }

    private void refreshAsync(TradePair pair, TradabilityScope scope) {
        getTradability(pair, scope, false)
                .exceptionally(exception -> {
                    log.debug(
                            "Async tradability refresh failed for {} scope={}: {}",
                            symbolOf(pair),
                            scope,
                            exception.getMessage()
                    );
                    return null;
                });
    }

    private SymbolTradability getCached(TradePair pair, TradabilityScope scope) {
        if (pair == null) {
            return null;
        }

        CacheEntry entry = cache.get(cacheKey(pair, normalizeScope(scope)));
        if (entry == null || !entry.fresh()) {
            return null;
        }

        return entry.tradability();
    }

    private void cacheStatus(TradePair pair, TradabilityScope scope, SymbolTradability status) {
        if (pair == null || status == null) {
            return;
        }

        TradabilityScope safeScope = normalizeScope(scope);
        long ttlMs = ttlByScopeMs.getOrDefault(safeScope, LIVE_TRADING_TTL_MS);
        long expiresAtMs = System.currentTimeMillis() + ttlMs;

        cache.put(cacheKey(pair, safeScope), new CacheEntry(status, expiresAtMs));
    }

    private void cacheBatchStatus(TradePair pair, SymbolTradability status) {
        cacheStatus(pair, TradabilityScope.WATCHLIST, status);
        cacheStatus(pair, TradabilityScope.LIVE_TRADING, status);
        cacheStatus(pair, TradabilityScope.BOT_TRADING, status);
        cacheStatus(pair, TradabilityScope.PAPER_TRADING, status);
        cacheStatus(pair, TradabilityScope.MARKET_DATA, status);
    }

    private SymbolTradability resolveStatus(
            TradePair pair,
            SymbolTradability status,
            String fallbackReason
    ) {
        if (status == null) {
            return unknownStatus(pair, TradabilityStatus.UNKNOWN, fallbackReason);
        }

        return status;
    }

    private @NonNull SymbolTradability unknownStatus(
            TradePair pair,
            TradabilityStatus status,
            String reason
    ) {
        boolean marketOpen = isMarketOpen(pair);

        TradabilityStatus resolvedStatus = marketOpen
                ? status
                : TradabilityStatus.MARKET_CLOSED;

        String resolvedReason = marketOpen
                ? reason
                : "Market/session currently closed for " + symbolOf(pair);

        return new SymbolTradability(
                exchange.getExchangeId(),
                pair,
                pair == null ? "" : pair.toString('/'),
                resolvedStatus,

                /*
                 * These values are intentionally display-friendly but trading-conservative.
                 * Unknown symbols may still appear in Market Watch while loading,
                 * but order/bot/live permissions remain disabled.
                 */
                true,   // marketDataAllowed
                true,   // watchlistAllowed
                true,   // backtestingAllowed

                false,  // paperTradingAllowed
                false,  // liveTradingAllowed
                false,  // botTradingAllowed
                false,  // orderSubmissionAllowed
                false,  // marketOrderAllowed
                false,  // limitOrderAllowed
                false,  // stopOrderAllowed
                false,  // shortingAllowed
                false,  // marginAllowed
                false,  // leverageAllowed

                resolvedReason,
                Instant.now(),
                Map.of("source", "universal-default")
        );
    }

    private boolean isMarketOpen(TradePair pair) {
        return pair == null
                || marketDataEngine == null
                || marketDataEngine.isTradableNow(pair);
    }

    private List<TradePair> sanitizePairs(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        return pairs.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private TradabilityScope normalizeScope(TradabilityScope scope) {
        return scope == null ? TradabilityScope.LIVE_TRADING : scope;
    }

    private @NonNull String cacheKey(TradePair pair, TradabilityScope scope) {
        if (exchange instanceof StellarNetwork stellarNetwork) {
            Optional<StellarPairIdentity> resolved = stellarNetwork.resolvePairIdentity(pair);
            if (resolved.isPresent()) {
                return resolved.get().canonicalKey() + "::" + normalizeScope(scope).name();
            }
        }
        return symbolOf(pair) + "::" + normalizeScope(scope).name();
    }

    private @NonNull String symbolOf(TradePair pair) {
        return pair == null ? "-" : pair.toString('/').toUpperCase();
    }

    private boolean supportsOrderType(SymbolTradability status, OpenOrder.OrderType orderType) {
        if (status == null) {
            return false;
        }

        if (orderType == null) {
            return true;
        }

        return switch (orderType) {
            case MARKET -> status.marketOrderAllowed();
            case LIMIT -> status.limitOrderAllowed();
            case STOP_LOSS, TAKE_PROFIT, TRAILING_STOP -> status.stopOrderAllowed();
            default -> status.orderSubmissionAllowed();
        };
    }

    private void logSkipIfNeeded(SymbolTradability status) {
        if (status == null || status.isFullyTradable()) {
            return;
        }

        log.info(
                "tradability.skip exchange={} symbol={} status={} reason={}",
                exchange.getName(),
                symbolOf(status.tradePair()),
                status.status(),
                status.reason()
        );
    }
}
