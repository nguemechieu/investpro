package org.investpro.exchange.contracts;

import org.investpro.exchange.websocket.ExchangeWebSocketClient;

public interface ExchangeConnection {

    void connect();

    void disconnect();

    void reconnect();

    Boolean isConnected();

    ExchangeWebSocketClient getWebsocketClient();

    boolean supportsWebSocket();

    boolean isWebsocketAvailable();
}