package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.List;

public final class ClientPortalIbkrBrokerConnection implements IbkrBrokerConnection {

    private final IbkrClientPortalClient clientPortalClient;
    private volatile IbkrSessionState sessionState = IbkrSessionState.disconnected(
            new IbkrConnectionProfile(IbkrConnectionMode.CLIENT_PORTAL_GATEWAY, "localhost", 5000, 1, true,
                    true, "IBKR Client Portal Gateway", null),
            "Client Portal Gateway session is not connected.");

    public ClientPortalIbkrBrokerConnection(IbkrClientPortalClient clientPortalClient) {
        this.clientPortalClient = clientPortalClient;
    }

    @Override
    public void connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safe = profile == null
                ? new IbkrConnectionProfile(IbkrConnectionMode.CLIENT_PORTAL_GATEWAY, "localhost", 5000, 1,
                        true, true, "IBKR Client Portal Gateway", null)
                : profile;
        boolean authenticated = clientPortalClient != null && clientPortalClient.isAuthenticated();
        String message = authenticated
                ? "Client Portal Gateway session is authenticated."
                : clientPortalClient == null
                        ? "Client Portal Gateway client is unavailable."
                        : clientPortalClient.authenticationFailureReason();
        sessionState = new IbkrSessionState(
                IbkrConnectionMode.CLIENT_PORTAL_GATEWAY,
                safe.host(),
                safe.port(),
                safe.clientId(),
                safe.paper(),
                authenticated,
                authenticated,
                authenticated,
                authenticated,
                authenticated,
                false,
                authenticated && !safe.paper(),
                message,
                authenticated ? Instant.now() : null,
                authenticated ? List.of("IBKR-CLIENT-PORTAL") : List.of());
    }

    @Override
    public void disconnect() {
        sessionState = IbkrSessionState.disconnected(null, "Client Portal Gateway session is not connected.");
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
        return false;
    }

    @Override
    public boolean supportsOrderPlacement() {
        return true;
    }
}
