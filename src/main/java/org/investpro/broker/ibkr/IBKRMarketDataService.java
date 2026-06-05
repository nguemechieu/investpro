package org.investpro.broker.ibkr;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class IBKRMarketDataService {

    private final org.investpro.exchange.ibkr.IbkrExchange exchange;
    private final IBKREventBridge eventBridge;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> subscriptions = new ConcurrentHashMap<>();

    public IBKRMarketDataService(org.investpro.exchange.ibkr.IbkrExchange exchange, IBKREventBridge eventBridge) {
        this.exchange = exchange;
        this.eventBridge = eventBridge;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "ibkr-market-data-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void subscribe(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }
        String key = tradePair.toString('/');
        subscriptions.computeIfAbsent(key,
                ignored -> scheduler.scheduleAtFixedRate(() -> publishTick(tradePair), 0L, 1L, TimeUnit.SECONDS));
    }

    public void stopAll() {
        for (ScheduledFuture<?> future : subscriptions.values()) {
            future.cancel(true);
        }
        subscriptions.clear();
        scheduler.shutdownNow();
    }

    private void publishTick(TradePair tradePair) {
        try {
            Ticker ticker = exchange.fetchTicker(tradePair).join();
            eventBridge.onTicker(ticker);
        } catch (Exception e) {
            log.warn("IBKR market data subscription tick failed for {}", tradePair, e);
        }
    }
}
