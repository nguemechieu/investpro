package org.investpro.terminal.autotrading;

public enum ExchangeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONCILING,
    PAUSED,
    ERROR
}
