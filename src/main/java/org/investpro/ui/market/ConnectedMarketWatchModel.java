package org.investpro.ui.market;

import org.investpro.exchange.Exchange;
import org.investpro.models.market.MarketInstrument;
import org.investpro.trading.market.MarketInstrumentService;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Keeps symbol identity tied to its source exchange, independent of the selected desk. */
public final class ConnectedMarketWatchModel {
    public record Row(String exchangeName, Exchange exchange, MarketInstrument instrument) {}
    public record Snapshot(List<Row> rows, Set<String> failedExchanges) {}
    private final MarketInstrumentService instruments;
    private final Map<String, Exchange> previousSources = new HashMap<>();

    public ConnectedMarketWatchModel(MarketInstrumentService instruments) {
        this.instruments = Objects.requireNonNull(instruments);
    }

    public synchronized CompletableFuture<Snapshot> load(Map<String, Exchange> connected) {
        connected.forEach((name, exchange) -> {
            if (previousSources.get(name) != exchange) instruments.invalidateExchange(exchange.getExchangeId());
        });
        previousSources.clear();
        previousSources.putAll(connected);
        var failures = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        List<CompletableFuture<List<Row>>> loads = connected.entrySet().stream().map(entry ->
                CompletableFuture.completedFuture(entry.getValue())
                        .thenComposeAsync(instruments::loadForExchange, org.investpro.core.concurrent.AppExecutors.MARKET_DATA)
                        .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .thenApply(products -> products.stream().filter(Objects::nonNull)
                                .filter(product -> product.tradePair() != null)
                                .map(product -> new Row(entry.getKey(), entry.getValue(), product)).toList())
                        .exceptionally(error -> { failures.add(entry.getKey()); return List.of(); }))
                .toList();
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(_ ->
                new Snapshot(loads.stream().flatMap(load -> load.join().stream())
                        .sorted(Comparator.comparing(Row::exchangeName)
                                .thenComparing(row -> row.instrument().nativeSymbol())).toList(), Set.copyOf(failures)));
    }
}
