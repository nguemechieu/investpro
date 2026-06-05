package org.investpro.broker.ibkr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.ibkr.IbkrExchange;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class IBKRConnectionManager {

    private final IbkrExchange exchange;
    private final IBKROfficialApiGateway officialApiGateway;
    private final ScheduledExecutorService scheduler;

    @Getter
    private volatile IBKRConnectionConfig config;

    private volatile Instant lastHeartbeat = Instant.EPOCH;

    public IBKRConnectionManager(IbkrExchange exchange, IBKROfficialApiGateway officialApiGateway,
            IBKRConnectionConfig config) {
        this.exchange = exchange;
        this.officialApiGateway = officialApiGateway;
        this.config = config == null ? IBKRConnectionConfig.defaults() : config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ibkr-professional-connection-monitor");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleAtFixedRate(this::healthTick, 5L, Math.max(1000L, this.config.heartbeatIntervalMs()),
                TimeUnit.MILLISECONDS);
    }

    public synchronized void updateConfig(IBKRConnectionConfig updatedConfig) {
        if (updatedConfig != null) {
            this.config = updatedConfig;
        }
    }

    public synchronized void connect() {
        IBKRConnectionMode mode = config.mode() == null ? IBKRConnectionMode.PAPER : config.mode();
        exchange.getConnectionManager().connect(mode == IBKRConnectionMode.LIVE
                ? org.investpro.exchange.ibkr.IbkrConnectionManager.Mode.LIVE
                : org.investpro.exchange.ibkr.IbkrConnectionManager.Mode.PAPER);

        officialApiGateway.connect(config.host(), config.activePort(), config.clientId());
        officialApiGateway.ensureReaderLoopRunning();
        markHeartbeat();

        log.info("Professional IBKR connection established mode={} host={} port={}", mode, config.host(),
                config.activePort());
    }

    public synchronized void disconnect() {
        exchange.disconnect();
        officialApiGateway.disconnect();
    }

    public boolean isConnected() {
        return Boolean.TRUE.equals(exchange.isConnected()) && officialApiGateway.isConnected();
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public void markHeartbeat() {
        lastHeartbeat = Instant.now();
    }

    private void healthTick() {
        try {
            if (isConnected()) {
                markHeartbeat();
                return;
            }
            if (!config.autoReconnect()) {
                return;
            }
            log.warn("IBKR connection stale, auto-reconnecting...");
            connect();
        } catch (Exception e) {
            log.error("IBKR health monitor failure", e);
        }
    }
}
