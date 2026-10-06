package org.investpro.broker.ibkr;

import org.investpro.exchange.ibkr.IbkrApiRuntime;
import org.investpro.exchange.ibkr.IbkrConnectionMode;
import org.investpro.exchange.ibkr.IbkrConnectionProfile;
import org.investpro.exchange.ibkr.IbkrTwsSession;

/** Compatibility facade over the same official session implementation used by IbkrExchange. */
public class IBKRReflectiveApiGateway implements IBKROfficialApiGateway {
    private final IbkrTwsSession session;
    public IBKRReflectiveApiGateway() { this(new IbkrTwsSession()); }
    public IBKRReflectiveApiGateway(IbkrTwsSession session) { this.session = session; }
    @Override public boolean isAvailable() {
        try { IbkrApiRuntime.type("com.ib.client.EClientSocket"); return true; }
        catch (ClassNotFoundException error) { return false; }
    }
    @Override public void connect(String host, int port, int clientId) {
        session.connect(new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, host, port, clientId,
                port == 4002 || port == 7497, false, null, null), "");
    }
    @Override public void disconnect() { session.disconnect(); }
    @Override public boolean isConnected() { return session.state().connectionSuccessful(); }
    @Override public void ensureReaderLoopRunning() {
        if (!isConnected()) throw new IllegalStateException(session.state().message());
    }
}