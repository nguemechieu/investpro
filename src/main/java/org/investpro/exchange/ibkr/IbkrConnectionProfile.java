package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.Objects;

public record IbkrConnectionProfile(
        IbkrConnectionMode mode,
        String host,
        int port,
        int clientId,
        boolean paper,
        boolean autoDetect,
        String connectionName,
        Instant lastSuccessfulConnectionAt) {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int TWS_PAPER_PORT = 7497;
    public static final int TWS_LIVE_PORT = 7496;
    public static final int GATEWAY_PAPER_PORT = 4002;
    public static final int GATEWAY_LIVE_PORT = 4001;
    public static final int CLIENT_PORTAL_PORT = 5000;

    public IbkrConnectionProfile {
        mode = mode == null ? IbkrConnectionMode.TWS_API : mode;
        host = normalizeHost(host);
        port = port > 0 ? port : defaultPort(mode, paper);
        clientId = clientId > 0 ? clientId : 1;
        connectionName = normalizeConnectionName(connectionName, mode, paper);
    }

    public static IbkrConnectionProfile twsPaper() {
        return new IbkrConnectionProfile(
                IbkrConnectionMode.TWS_API,
                DEFAULT_HOST,
                TWS_PAPER_PORT,
                1,
                true,
                true,
                "IBKR TWS Paper",
                null);
    }

    public IbkrConnectionProfile withEndpoint(String host, int port) {
        return new IbkrConnectionProfile(mode, host, port, clientId, paper, autoDetect, connectionName,
                lastSuccessfulConnectionAt);
    }

    public IbkrConnectionProfile withMode(IbkrConnectionMode mode) {
        return new IbkrConnectionProfile(mode, host, defaultPort(mode, paper), clientId, paper, autoDetect,
                connectionName, lastSuccessfulConnectionAt);
    }

    public IbkrConnectionProfile withPaper(boolean paper) {
        return new IbkrConnectionProfile(mode, host, defaultPort(mode, paper), clientId, paper, autoDetect,
                connectionName, lastSuccessfulConnectionAt);
    }

    public IbkrConnectionProfile markSuccessful(Instant instant) {
        return new IbkrConnectionProfile(mode, host, port, clientId, paper, autoDetect, connectionName,
                instant == null ? Instant.now() : instant);
    }

    public static int defaultPort(IbkrConnectionMode mode, boolean paper) {
        if (mode == IbkrConnectionMode.CLIENT_PORTAL_GATEWAY) {
            return CLIENT_PORTAL_PORT;
        }
        return paper ? TWS_PAPER_PORT : TWS_LIVE_PORT;
    }

    private static String normalizeHost(String host) {
        String value = Objects.requireNonNullElse(host, "").trim();
        return value.isBlank() ? DEFAULT_HOST : value;
    }

    private static String normalizeConnectionName(String name, IbkrConnectionMode mode, boolean paper) {
        String value = Objects.requireNonNullElse(name, "").trim();
        if (!value.isBlank()) {
            return value;
        }
        return switch (mode) {
            case CLIENT_PORTAL_GATEWAY -> "IBKR Client Portal Gateway";
            case CLOUD_OAUTH_FUTURE -> "IBKR Cloud OAuth";
            case TWS_API -> paper ? "IBKR TWS/Gateway Paper" : "IBKR TWS/Gateway Live";
        };
    }
}
