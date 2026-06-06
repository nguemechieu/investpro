package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.List;

public final class TwsIbkrBrokerConnection implements IbkrBrokerConnection {

    private final IbkrConnectionManager connectionManager;
    private volatile IbkrSessionState sessionState = IbkrSessionState.disconnected(
            IbkrConnectionProfile.twsPaper(),
            "TWS or IB Gateway is not connected.");

    public TwsIbkrBrokerConnection(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safe = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        connectionManager.connect(safe);
        sessionState = new IbkrSessionState(
                IbkrConnectionMode.TWS_API,
                safe.host(),
                safe.port(),
                safe.clientId(),
                safe.paper(),
                true,
                true,
                true,
                true,
                connectionManager.isMarketDataAvailable(),
                false,
                safe.paper(),
                "Connected to TWS / IB Gateway. Orderbook permission is evaluated separately.",
                Instant.now(),
                List.of("IBKR-" + (safe.paper() ? "PAPER" : "LIVE")));
    }

    @Override
    public void disconnect() {
        connectionManager.disconnect();
        sessionState = IbkrSessionState.disconnected(null, "TWS or IB Gateway is not connected.");
    }

    @Override
    public IbkrSessionState getSessionState() {
        return sessionState;
    }

    @Override
    public boolean supportsAccountSummary() {
        return true;
    }

    @Override
    public boolean supportsMarketData() {
        return true;
    }

    @Override
    public boolean supportsMarketDepth() {
        return true;
    }

    @Override
    public boolean supportsOrderPlacement() {
        return true;
    }
}
