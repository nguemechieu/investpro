package org.investpro.exchange.ibkr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class IbkrConnectionManager {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int PAPER_PORT = IbkrConnectionProfile.GATEWAY_PAPER_PORT;
    public static final int LIVE_PORT = IbkrConnectionProfile.GATEWAY_LIVE_PORT;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ibkr-health-monitor");
        thread.setDaemon(true);
        return thread;
    });

    @Getter
    private volatile String host = DEFAULT_HOST;
    @Getter
    private volatile int port = PAPER_PORT;
    @Getter
    private volatile Mode mode = Mode.PAPER;
    @Getter
    private volatile int clientId = 1;
    @Getter
    private volatile IbkrConnectionMode connectionMode = IbkrConnectionMode.TWS_API;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean marketDataAvailable = new AtomicBoolean(false);
    private final AtomicLong lastHeartbeatEpochMs = new AtomicLong(0L);
    private final AtomicLong latencyMs = new AtomicLong(0L);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public synchronized void connect(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.host = DEFAULT_HOST;
        this.port = mode == Mode.PAPER ? PAPER_PORT : LIVE_PORT;
        this.clientId = 1;
        this.connectionMode = IbkrConnectionMode.TWS_API;
        this.connected.set(true);
        this.lastHeartbeatEpochMs.set(System.currentTimeMillis());
        this.reconnectAttempts.set(0);
        startHeartbeatMonitor();
        log.info("IBKR connected to IB Gateway {}:{} ({})", host, port, mode);
    }

    public synchronized void connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safeProfile = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        this.connectionMode = safeProfile.mode();
        this.mode = safeProfile.paper() ? Mode.PAPER : Mode.LIVE;
        this.host = safeProfile.host();
        this.port = safeProfile.port();
        this.clientId = safeProfile.clientId();
        this.connected.set(true);
        this.lastHeartbeatEpochMs.set(System.currentTimeMillis());
        this.reconnectAttempts.set(0);
        startHeartbeatMonitor();
        log.info("IBKR connected to {} {}:{} clientId={} ({})",
                connectionMode,
                host,
                port,
                clientId,
                mode);
    }

    public synchronized void disconnect() {
        connected.set(false);
        marketDataAvailable.set(false);
        log.info("IBKR disconnected from IB Gateway");
    }

    public synchronized void reconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        log.warn("IBKR reconnect attempt {}", attempt);
        connect(mode);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void markMarketDataAvailable(boolean available) {
        marketDataAvailable.set(available);
        lastHeartbeatEpochMs.set(System.currentTimeMillis());
    }

    public boolean isMarketDataAvailable() {
        return marketDataAvailable.get();
    }

    public long currentLatencyMs() {
        return latencyMs.get();
    }

    public ConnectionHealth snapshotHealth() {
        long lastHeartbeat = lastHeartbeatEpochMs.get();
        boolean staleHeartbeat = lastHeartbeat == 0L || (System.currentTimeMillis() - lastHeartbeat) > 15000L;
        return new ConnectionHealth(
                connected.get(),
                marketDataAvailable.get(),
                staleHeartbeat,
                latencyMs.get(),
                reconnectAttempts.get(),
                lastHeartbeat == 0L ? null : Instant.ofEpochMilli(lastHeartbeat));
    }

    public void shutdown() {
        scheduler.shutdownNow();
        disconnect();
    }

    private void startHeartbeatMonitor() {
        scheduler.scheduleAtFixedRate(this::heartbeatTick, 0, 5, TimeUnit.SECONDS);
    }

    private void heartbeatTick() {
        if (!connected.get()) {
            return;
        }

        long start = System.nanoTime();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        latencyMs.set(Math.max(1L, elapsedMs));

        long now = System.currentTimeMillis();
        long last = lastHeartbeatEpochMs.get();
        if (last > 0L && now - last > 15000L) {
            reconnect();
            return;
        }

        lastHeartbeatEpochMs.set(now);
    }

    public enum Mode {
        PAPER,
        LIVE
    }

    public record ConnectionHealth(
            boolean connected,
            boolean marketDataAvailable,
            boolean heartbeatStale,
            long latencyMs,
            int reconnectAttempts,
            Instant lastHeartbeat) {
    }
}
