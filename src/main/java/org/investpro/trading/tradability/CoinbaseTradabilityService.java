package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Coinbase;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Coinbase product tradability cache and refresh service.
 */
@Slf4j
public final class CoinbaseTradabilityService implements TradabilityProvider {
    private static final long DEFAULT_TTL_MS = Duration.ofMinutes(5).toMillis();

    private final Coinbase coinbase;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(ProductTradabilityStatus status, long expiresAtMs) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }

        boolean fresh() {
            return !expired();
        }
    }

    public CoinbaseTradabilityService(@NotNull Coinbase coinbase) {
        this.coinbase = Objects.requireNonNull(coinbase, "coinbase must not be null");
    }

    @Override
    public CompletableFuture<ProductTradabilityStatus> getTradabilityStatus(String productId) {
        String normalized = normalizeProductId(productId);
        CacheEntry cached = cache.get(normalized);
        if (cached != null && cached.fresh()) {
            return CompletableFuture.completedFuture(cached.status());
        }

        return CompletableFuture.supplyAsync(() -> refreshProduct(normalized));
    }

    @Override
    public CompletableFuture<List<ProductTradabilityStatus>> getAllTradableProducts() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TradePair> pairs = coinbase.getTradePairSymbol();
                if (pairs == null || pairs.isEmpty()) {
                    return List.of();
                }

                List<ProductTradabilityStatus> statuses = coinbase.fetchTradabilityStatus(pairs)
                        .get(30, TimeUnit.SECONDS)
                        .stream()
                        .filter(Objects::nonNull)
                        .map(this::toProductStatus)
                        .peek(status -> put(status.productId(), status))
                        .sorted(Comparator.comparing(ProductTradabilityStatus::productId))
                        .toList();

                return statuses;
            } catch (Exception exception) {
                log.warn("Failed to fetch Coinbase tradability snapshot", exception);
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isTradeable(String productId) {
        return getTradabilityStatus(productId).thenApply(ProductTradabilityStatus::isTradeable);
    }

    @Override
    public CompletableFuture<Boolean> canPlaceMarketOrder(String productId) {
        return getTradabilityStatus(productId).thenApply(ProductTradabilityStatus::canPlaceMarketOrder);
    }

    @Override
    public CompletableFuture<Boolean> canPlaceLimitOrder(String productId) {
        return getTradabilityStatus(productId).thenApply(ProductTradabilityStatus::canPlaceLimitOrder);
    }

    public void invalidate(String productId) {
        cache.remove(normalizeProductId(productId));
    }

    public void invalidateAll() {
        cache.clear();
    }

    private ProductTradabilityStatus refreshProduct(String productId) {
        try {
            TradePair pair = TradePair.fromSymbol(productId);
            ProductTradabilityStatus status = coinbase.fetchTradabilityStatus(pair)
                    .thenApply(this::toProductStatus)
                    .get(30, TimeUnit.SECONDS);
            put(productId, status);
            return status;
        } catch (Exception exception) {
            log.warn("Failed to refresh Coinbase product tradability for {}", productId, exception);
            ProductTradabilityStatus status = new ProductTradabilityStatus(
                    productId,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "UNKNOWN",
                    "Tradability lookup failed",
                    Instant.now());
            put(productId, status);
            return status;
        }
    }

    private ProductTradabilityStatus toProductStatus(org.investpro.trading.tradability.SymbolTradability status) {
        if (status == null) {
            return new ProductTradabilityStatus("", false, false, false, false, false, false, false, false,
                    "UNKNOWN", "No tradability information", Instant.now());
        }

        String productId = status.nativeSymbol() == null || status.nativeSymbol().isBlank()
                ? status.tradePair() == null ? "" : status.tradePair().toString('-')
                : status.nativeSymbol();

        String upper = productId == null ? "" : productId.trim().toUpperCase(Locale.ROOT);
        String statusName = status.status() == null ? "UNKNOWN" : status.status().name();
        String reason = status.reason();

        boolean tradable = status.status() == TradabilityStatus.FULLY_TRADABLE
                || status.status() == TradabilityStatus.LIMIT_ONLY
                || status.status() == TradabilityStatus.POST_ONLY
                || status.status() == TradabilityStatus.AUCTION_ONLY;

        return new ProductTradabilityStatus(
                upper,
                tradable,
                status.status() == TradabilityStatus.VIEW_ONLY,
                status.status() == TradabilityStatus.HALTED || status.status() == TradabilityStatus.INACTIVE,
                status.status() == TradabilityStatus.DISABLED,
                status.status() == TradabilityStatus.CANCEL_ONLY,
                status.status() == TradabilityStatus.LIMIT_ONLY,
                status.status() == TradabilityStatus.POST_ONLY,
                status.status() == TradabilityStatus.AUCTION_ONLY,
                statusName,
                reason,
                status.checkedAt());
    }

    private void put(String productId, ProductTradabilityStatus status) {
        if (productId == null || productId.isBlank() || status == null) {
            return;
        }
        cache.put(normalizeProductId(productId), new CacheEntry(status, System.currentTimeMillis() + DEFAULT_TTL_MS));
    }

    private String normalizeProductId(String productId) {
        return productId == null ? "" : productId.trim().toUpperCase(Locale.ROOT);
    }
}
