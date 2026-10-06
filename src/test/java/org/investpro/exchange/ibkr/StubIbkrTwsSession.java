package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Offline broker session; tests never connect to or submit orders to a real account. */
class StubIbkrTwsSession extends IbkrTwsSession {
    IbkrConnectionProfile profile;
    boolean connected;
    int connections;

    @Override public void connect(IbkrConnectionProfile profile, String accountId) {
        this.profile = profile;
        connected = true;
        connections++;
    }
    @Override public void disconnect() { connected = false; }
    @Override public boolean socketConnected() { return connected; }
    @Override public IbkrSessionState state() {
        if (!connected) return IbkrSessionState.disconnected(profile, "Disconnected");
        return new IbkrSessionState(profile.mode(), profile.host(), profile.port(), profile.clientId(),
                profile.paper(), true, true, true, true, false, false, true,
                "API handshake complete", Instant.now(), List.of("DU123456"));
    }
    @Override public CompletableFuture<org.investpro.models.trading.Ticker> ticker(IbkrResolvedContract contract) {
        return CompletableFuture.completedFuture(new org.investpro.models.trading.Ticker(1.10, 1.09, 1.11, 1000.0, System.currentTimeMillis()));
    }
    @Override public CompletableFuture<java.util.List<org.investpro.models.trading.Position>> positions() {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
    @Override public CompletableFuture<java.util.List<org.investpro.models.trading.OpenOrder>> openOrders() {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
    @Override public CompletableFuture<IbkrAccountSnapshot> accountSnapshot() {
        return CompletableFuture.completedFuture(new IbkrAccountSnapshot("DU123456", "Interactive Brokers",
                profile.paper(), 100000, 80000, 20000, 160000, Map.of("USD", 100000.0), Instant.now()));
    }
}
