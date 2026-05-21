package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.market.MarketDataEngine;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class UniversalTradabilityService {
    private final Exchange exchange;
    private final MarketDataEngine marketDataEngine;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<TradabilityScope, Long> ttlByScopeMs = new EnumMap<>(TradabilityScope.class);

    private record CacheEntry(SymbolTradability tradability, long expiresAtMs) {
        private boolean expired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }

    public UniversalTradabilityService(Exchange exchange, MarketDataEngine marketDataEngine) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.marketDataEngine = marketDataEngine;

        ttlByScopeMs.put(TradabilityScope.MARKET_DATA, 5 * 60_000L);
        ttlByScopeMs.put(TradabilityScope.WATCHLIST, 10 * 60_000L);
        ttlByScopeMs.put(TradabilityScope.BACKTESTING, 30 * 60_000L);
        ttlByScopeMs.put(TradabilityScope.PAPER_TRADING, 2 * 60_000L);
        ttlByScopeMs.put(TradabilityScope.LIVE_TRADING, 60_000L);
        ttlByScopeMs.put(TradabilityScope.BOT_TRADING, 30_000L);
        ttlByScopeMs.put(TradabilityScope.ORDER_SUBMISSION, 10_000L);
    }

    public CompletableFuture<SymbolTradability> getTradability(TradePair pair) {
        return getTradability(pair, TradabilityScope.LIVE_TRADING, false);
    }

    public CompletableFuture<SymbolTradability> getTradability(TradePair pair, TradabilityScope scope, boolean forceRefresh) {
        if (pair == null) {
            return CompletableFuture.completedFuture(unknownStatus(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }

        SymbolTradability cached = cached(pair, scope);
        if (!forceRefresh && cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return exchange.fetchTradabilityStatus(pair)
                .exceptionally(exception -> {
                    log.warn("Tradability fetch failed for {} on {}: {}",
                            pair,
                            exchange.getName(),
                            exception.getMessage());
                    return unknownStatus(pair, TradabilityStatus.UNKNOWN, "Tradability lookup failed");
                })
                .thenApply(status -> {
                    SymbolTradability resolved = status == null
                            ? unknownStatus(pair, TradabilityStatus.UNKNOWN, "Tradability not provided by adapter")
                            : status;
                    put(pair, scope, resolved);
                    if (!resolved.isFullyTradable()) {
                        log.info("tradability.skip exchange={} symbol={} status={} reason={}",
                                exchange.getName(),
                                symbolOf(pair),
                                resolved.status(),
                                resolved.reason());
                    }
                    return resolved;
                });
    }

    public CompletableFuture<List<SymbolTradability>> getTradability(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<TradePair> sanitized = pairs.stream().filter(Objects::nonNull).distinct().toList();
        if (sanitized.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return exchange.fetchTradabilityStatus(sanitized)
                .exceptionally(exception -> {
                    log.warn("Batch tradability fetch failed for {} symbols on {}: {}",
                            sanitized.size(),
                            exchange.getName(),
                            exception.getMessage());
                    return List.of();
                })
                .thenApply(statuses -> {
                    Map<String, SymbolTradability> bySymbol = new ConcurrentHashMap<>();
                    for (SymbolTradability status : statuses) {
                        if (status == null || status.tradePair() == null) {
                            continue;
                        }
                        bySymbol.put(symbolOf(status.tradePair()), status);
                    }

                    List<SymbolTradability> resolved = new ArrayList<>(sanitized.size());
                    for (TradePair pair : sanitized) {
                        SymbolTradability status = bySymbol.getOrDefault(
                                symbolOf(pair),
                                unknownStatus(pair, TradabilityStatus.UNKNOWN, "Tradability missing in batch response"));
                        put(pair, TradabilityScope.WATCHLIST, status);
                        put(pair, TradabilityScope.LIVE_TRADING, status);
                        resolved.add(status);
                    }
                    return resolved;
                });
    }

    public List<TradePair> filterForMarketWatch(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        return pairs.stream()
                .filter(Objects::nonNull)
                .filter(pair -> {
                    SymbolTradability status = snapshot(pair, TradabilityScope.WATCHLIST);
                    return status != null ? status.canBeDisplayedInMarketWatch() : true;
                })
                .toList();
    }

    public List<TradePair> filterForBotTrading(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        return pairs.stream()
                .filter(Objects::nonNull)
                .filter(pair -> {
                    SymbolTradability status = snapshot(pair, TradabilityScope.BOT_TRADING);
                    if (status == null) {
                        return false;
                    }
                    return status.canBeUsedForBotTrading();
                })
                .toList();
    }

    public List<TradePair> filterForLiveTrading(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        return pairs.stream()
                .filter(Objects::nonNull)
                .filter(pair -> {
                    SymbolTradability status = snapshot(pair, TradabilityScope.LIVE_TRADING);
                    return status != null && status.isFullyTradable();
                })
                .toList();
    }

    public boolean canSubmitOrder(TradePair pair, OpenOrder.OrderType orderType) {
        SymbolTradability status = snapshot(pair, TradabilityScope.ORDER_SUBMISSION);
        if (status == null) {
            return false;
        }

        if (!status.orderSubmissionAllowed()) {
            return false;
        }

        return supportsOrderType(status, orderType);
    }

    public CompletableFuture<Boolean> canSubmitOrderAsync(TradePair pair, OpenOrder.OrderType orderType) {
        return getTradability(pair, TradabilityScope.ORDER_SUBMISSION, true)
                .thenApply(status -> status != null
                        && status.orderSubmissionAllowed()
                        && supportsOrderType(status, orderType));
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

    private SymbolTradability snapshot(TradePair pair, TradabilityScope scope) {
        SymbolTradability cached = cached(pair, scope);
        if (cached != null) {
            return cached;
        }

        // Trigger async refresh while returning conservative null for caller.
        getTradability(pair, scope, false);
        return null;
    }

    private SymbolTradability cached(TradePair pair, TradabilityScope scope) {
        CacheEntry entry = cache.get(cacheKey(pair, scope));
        if (entry == null || entry.expired()) {
            return null;
        }
        return entry.tradability();
    }

    private void put(TradePair pair, TradabilityScope scope, SymbolTradability status) {
        if (pair == null || status == null) {
            return;
        }
        long ttl = ttlByScopeMs.getOrDefault(scope, 60_000L);
        cache.put(cacheKey(pair, scope), new CacheEntry(status, System.currentTimeMillis() + ttl));
    }

    private String cacheKey(TradePair pair, TradabilityScope scope) {
        return symbolOf(pair) + "::" + scope.name();
    }

    private String symbolOf(TradePair pair) {
        return pair == null ? "-" : pair.toString('/').toUpperCase();
    }

    private boolean supportsOrderType(SymbolTradability status, OpenOrder.OrderType orderType) {
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

    private SymbolTradability unknownStatus(TradePair pair, TradabilityStatus status, String reason) {
        boolean marketOpen = pair == null || marketDataEngine == null || marketDataEngine.isTradableNow(pair);
        TradabilityStatus resolvedStatus = marketOpen ? status : TradabilityStatus.MARKET_CLOSED;
        String resolvedReason = marketOpen
                ? reason
                : "Market/session currently closed for " + symbolOf(pair);

        return new SymbolTradability(
                exchange.getExchangeId(),
                pair,
                pair == null ? "" : pair.toString('/'),
                resolvedStatus,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                resolvedReason,
                Instant.now(),
                Map.of("source", "universal-default"));
    }
}
