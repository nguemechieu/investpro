package org.investpro.exchange.ibkr;

import java.util.EnumMap;
import java.util.Map;

public final class IbkrConnectionService {

    private final Map<IbkrConnectionMode, IbkrBrokerConnection> connections =
            new EnumMap<>(IbkrConnectionMode.class);
    private volatile IbkrBrokerConnection activeConnection;

    public IbkrConnectionService(
            TwsIbkrBrokerConnection twsConnection,
            ClientPortalIbkrBrokerConnection clientPortalConnection) {
        connections.put(IbkrConnectionMode.TWS_API, twsConnection);
        connections.put(IbkrConnectionMode.CLIENT_PORTAL_GATEWAY, clientPortalConnection);
    }

    public synchronized IbkrSessionState connect(IbkrConnectionProfile profile) {
        IbkrConnectionProfile safe = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        if (safe.paper()) {
            throw new IllegalArgumentException("IBKR remote paper connections are disabled; use InvestPro local paper simulation.");
        }
        if (safe.mode() == IbkrConnectionMode.CLOUD_OAUTH_FUTURE) {
            throw new UnsupportedOperationException(
                    "IBKR cloud/OAuth mode is reserved for a future IBKR-approved authorization flow.");
        }
        IbkrBrokerConnection connection = connections.get(safe.mode());
        if (connection == null) {
            throw new IllegalStateException("No IBKR connection adapter is registered for " + safe.mode());
        }
        IbkrSessionState state = getSessionState();
        if (activeConnection == connection && state.connectionSuccessful()
                && state.mode() == safe.mode() && state.host().equals(safe.host())
                && state.port() == safe.port() && state.clientId() == safe.clientId()
                && state.paper() == safe.paper()) {
            return state;
        }
        if (activeConnection != null) activeConnection.disconnect();
        activeConnection = null;
        connection.connect(safe);
        activeConnection = connection;
        return connection.getSessionState();
    }

    public void disconnect() {
        if (activeConnection != null) {
            activeConnection.disconnect();
        }
    }

    public IbkrSessionState getSessionState() {
        return activeConnection == null
                ? IbkrSessionState.disconnected(IbkrConnectionProfile.twsPaper(), "No IBKR connection is active.")
                : activeConnection.getSessionState();
    }

    public IbkrBrokerConnection activeConnection() {
        return activeConnection;
    }
}
