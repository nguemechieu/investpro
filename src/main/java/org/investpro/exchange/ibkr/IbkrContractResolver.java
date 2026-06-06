package org.investpro.exchange.ibkr;

import org.investpro.models.trading.TradePair;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class IbkrContractResolver {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(12);

    private final IbkrContractSearchService searchService;
    private final IbkrContractDetailsService detailsService;
    private final IbkrContractCache cache;

    public IbkrContractResolver(IbkrContractSearchService searchService,
            IbkrContractDetailsService detailsService,
            IbkrContractCache cache) {
        this.searchService = searchService;
        this.detailsService = detailsService;
        this.cache = cache;
    }

    public CompletableFuture<List<IbkrContractCandidate>> search(String userSearchTerm) {
        String normalized = normalizeSearchTerm(userSearchTerm);
        if (normalized.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return searchService.search(normalized, DEFAULT_TIMEOUT)
                .orTimeout(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<IbkrResolvedContract> resolve(IbkrContractCandidate candidate) {
        if (candidate == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No matching contract found."));
        }
        return detailsService.requestDetails(candidate, DEFAULT_TIMEOUT)
                .orTimeout(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(cache::put);
    }

    public Optional<IbkrResolvedContract> cached(TradePair pair) {
        return cache.findByTradePair(pair);
    }

    public Optional<IbkrResolvedContract> cached(String displaySymbol) {
        return cache.findByDisplaySymbol(displaySymbol);
    }

    public IbkrResolvedContract requireResolved(TradePair pair) {
        return cached(pair).orElseThrow(() -> new IllegalStateException(
                "No resolved IBKR contract exists for %s. Please resolve the contract before requesting market data, orderbook, or orders."
                        .formatted(pair)));
    }

    public List<IbkrResolvedContract> cachedContracts() {
        return cache.all();
    }

    public static String normalizeSearchTerm(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('_', '/')
                .replace('.', '/');
        return normalized.replaceAll("\\s+", " ");
    }
}
