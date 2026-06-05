package org.investpro.broker.ibkr;

/**
 * Integration point for the official Interactive Brokers Java API.
 * <p>
 * Production implementations should wire:
 * - com.ib.client.EWrapper callback handler
 * - com.ib.client.EClientSocket request/response transport
 * - com.ib.client.EReader reader loop
 */
public interface IBKROfficialApiGateway {

    boolean isAvailable();

    void connect(String host, int port, int clientId);

    void disconnect();

    boolean isConnected();

    void ensureReaderLoopRunning();
}
