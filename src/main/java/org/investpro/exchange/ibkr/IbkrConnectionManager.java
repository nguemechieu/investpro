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
    @Getter
    private final IbkrTwsSession twsSession;
    @Getter
    private final String requestedAccountId;

    public IbkrConnectionManager() { this(new IbkrTwsSession(), ""); }

    public IbkrConnectionManager(IbkrTwsSession session, String accountId) {
        twsSession = Objects.requireNonNull(session);
        requestedAccountId = accountId == null ? "" : accountId;
    }

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
    private final AtomicBoolean heartbeatMonitorStarted = new AtomicBoolean(false);

    public synchronized void connect(Mode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        connect(new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, DEFAULT_HOST,
                mode == Mode.PAPER ? PAPER_PORT : LIVE_PORT, 1, mode == Mode.PAPER, false, null, null));
    }
    public synchronized void connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safeProfile = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        if (safeProfile.paper()) {
            throw new IllegalArgumentException("IBKR remote paper connections are disabled; use InvestPro local paper simulation.");
        }
        if (safeProfile.mode() == IbkrConnectionMode.TWS_API) {
            twsSession.connect(safeProfile, requestedAccountId);
        }
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
        twsSession.disconnect();
        connected.set(false);
        marketDataAvailable.set(false);
        log.info("IBKR disconnected from IB Gateway");
    }

    public synchronized void reconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        log.warn("IBKR reconnect attempt {}", attempt);
        IbkrConnectionProfile profile = new IbkrConnectionProfile(connectionMode, host, port, clientId,
                mode == Mode.PAPER, false, null, null);
        connect(profile);
    }

    public boolean isConnected() {
        return connected.get() && (connectionMode != IbkrConnectionMode.TWS_API || twsSession.state().connectionSuccessful());
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
                isConnected(),
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
        if (!heartbeatMonitorStarted.compareAndSet(false, true)) return;
        scheduler.scheduleAtFixedRate(this::heartbeatTick, 0, 5, TimeUnit.SECONDS);
    }

    private void heartbeatTick() {
        if (!connected.get()) {
            return;
        }

        // Readiness is driven by the SDK reader and connectivity callbacks, not a local timer.
        if (!isConnected()) {
            marketDataAvailable.set(false);
            return;
        }
        lastHeartbeatEpochMs.set(System.currentTimeMillis());
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
