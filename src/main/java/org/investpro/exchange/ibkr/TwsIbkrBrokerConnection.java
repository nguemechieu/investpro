package org.investpro.exchange.ibkr;


import lombok.Setter;

public final class TwsIbkrBrokerConnection implements IbkrBrokerConnection {

    private final IbkrConnectionManager connectionManager;
    @Setter
    volatile IbkrSessionState sessionState = IbkrSessionState.disconnected(
            IbkrConnectionProfile.twsPaper(),
            "TWS or IB Gateway is not connected.");

    public TwsIbkrBrokerConnection(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safe = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        connectionManager.connect(safe);
        sessionState = connectionManager.getTwsSession().state();
    }

    @Override
    public void disconnect() {
        connectionManager.disconnect();
        sessionState = IbkrSessionState.disconnected(null, "TWS or IB Gateway is not connected.");
    }

    @Override
    public IbkrSessionState getSessionState() {
        return connectionManager.getTwsSession().state();
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
