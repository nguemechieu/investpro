package org.investpro.broker.ibkr;

public record IBKRConnectionConfig(
        String host,
        int paperPort,
        int livePort,
        int clientId,
        long heartbeatIntervalMs,
        boolean autoReconnect,
        IBKRConnectionMode mode) {

    public static IBKRConnectionConfig defaults() {
        return new IBKRConnectionConfig(
                "127.0.0.1",
                4002,
                4001,
                1,
                5000L,
                true,
                IBKRConnectionMode.PAPER);
    }

    public int activePort() {
        return mode == IBKRConnectionMode.LIVE ? livePort : paperPort;
    }
}
