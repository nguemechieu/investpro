package org.investpro.terminal.provider;

import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.OrderBookSnapshot;
import org.investpro.terminal.domain.TradePrint;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface MarketDataProvider extends ProviderCapabilities {
    CompletableFuture<MarketTick> latestTick(InstrumentId instrumentId);

    default CompletableFuture<OrderBookSnapshot> orderBook(InstrumentId instrumentId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(providerId() + " does not provide order books"));
    }

    default CompletableFuture<List<TradePrint>> recentTrades(InstrumentId instrumentId, int limit) {
        return CompletableFuture.completedFuture(List.of());
    }

    default AutoCloseable subscribeTicks(List<InstrumentId> instruments, Consumer<MarketTick> consumer) {
        return () -> { };
    }
}
