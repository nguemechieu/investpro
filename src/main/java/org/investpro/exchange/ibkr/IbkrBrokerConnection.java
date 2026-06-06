package org.investpro.exchange.ibkr;

public interface IbkrBrokerConnection {
    void connect(IbkrConnectionProfile profile);

    void disconnect();

    IbkrSessionState getSessionState();

    boolean supportsAccountSummary();

    boolean supportsMarketData();

    boolean supportsMarketDepth();

    boolean supportsOrderPlacement();
}
