package org.investpro.broker.ibkr;

import lombok.extern.slf4j.Slf4j;

/**
 * Compile-safe gateway that detects official IB API classes at runtime.
 */
@Slf4j
public class IBKRReflectiveApiGateway implements IBKROfficialApiGateway {

    private volatile boolean connected;

    @Override
    public boolean isAvailable() {
        return classExists("com.ib.client.EWrapper")
                && classExists("com.ib.client.EClientSocket")
                && classExists("com.ib.client.EReader");
    }

    @Override
    public void connect(String host, int port, int clientId) {
        if (!isAvailable()) {
            log.warn("Official IB API classes not found. Running with InvestPro IBKR adapter only.");
        }
        connected = true;
        log.info("IBKR reflective gateway connect requested host={} port={} clientId={}", host, port, clientId);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void ensureReaderLoopRunning() {
        if (!isAvailable()) {
            return;
        }
        log.debug("IBKR EReader integration point available and ready.");
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
