package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Generic tradability provider backed by an exchange adapter.
 */
@Slf4j
public final class ExchangeTradabilityProvider implements TradabilityProvider {
    private final Exchange exchange;
    private final UniversalTradabilityService tradabilityService;

    public ExchangeTradabilityProvider(Exchange exchange, UniversalTradabilityService tradabilityService) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.tradabilityService = tradabilityService;
    }

    @Override
    public CompletableFuture<ProductTradabilityStatus> getTradabilityStatus(String productId) {
        TradePair pair = toTradePair(productId);
        if (pair == null) {
            return CompletableFuture.completedFuture(unknown(productId, "Unable to resolve product"));
        }

        if (tradabilityService != null) {
            return tradabilityService.getTradability(pair, TradabilityScope.ORDER_SUBMISSION, true)
                    .thenApply(this::toProductStatus);
        }

        return exchange.fetchTradabilityStatus(pair)
                .thenApply(this::toProductStatus)
                .exceptionally(exception -> {
                    log.warn("Tradability lookup failed for {} on {}", productId, exchange.getName(), exception);
                    return unknown(productId, "Tradability lookup failed");
                });
    }

    @Override
    public CompletableFuture<List<ProductTradabilityStatus>> getAllTradableProducts() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TradePair> pairs = exchange.getTradePairSymbol();
                if (pairs == null || pairs.isEmpty()) {
                    return List.of();
                }
                List<SymbolTradability> statuses = exchange.fetchTradabilityStatus(pairs).join();
                return statuses.stream().map(this::toProductStatus).toList();
            } catch (Exception exception) {
                log.warn("Unable to enumerate tradable products for {}", exchange.getName(), exception);
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

    private ProductTradabilityStatus toProductStatus(SymbolTradability status) {
        if (status == null) {
            return unknown("", "No tradability data");
        }

        String productId = status.nativeSymbol() == null || status.nativeSymbol().isBlank()
                ? status.tradePair() == null ? "" : status.tradePair().toString('-')
                : status.nativeSymbol();
        String statusName = status.status() == null ? "UNKNOWN" : status.status().name();
        String reason = status.reason();
        boolean tradable = status.status() == TradabilityStatus.FULLY_TRADABLE
                || status.status() == TradabilityStatus.LIMIT_ONLY
                || status.status() == TradabilityStatus.POST_ONLY
                || status.status() == TradabilityStatus.AUCTION_ONLY;

        return new ProductTradabilityStatus(
                productId,
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
                status.checkedAt() == null ? Instant.now() : status.checkedAt());
    }

    private ProductTradabilityStatus unknown(String productId, String message) {
        return new ProductTradabilityStatus(productId, false, false, false, false, false, false, false, false,
                "UNKNOWN", message, Instant.now());
    }

    private TradePair toTradePair(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        String normalized = productId.trim().toUpperCase(Locale.ROOT).replace('-', '/');
        try {
            return TradePair.fromSymbol(normalized);
        } catch (Exception exception) {
            log.debug("Unable to parse product id {}", productId, exception);
            return null;
        }
    }
}