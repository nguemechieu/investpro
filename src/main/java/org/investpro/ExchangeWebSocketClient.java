package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * An abstract {@code WebSocketClient} implementation that encapsulates common functionality
 * needed by {@code Exchange} implementations to interface with Websocket APIs.
 *

 */
public abstract class ExchangeWebSocketClient extends CoinbaseWebSocketClient {

    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);

    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) throws SQLException, ClassNotFoundException {
        super(clientUri, clientDraft);
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    @Override
    public void onError(Exception exception) {
        logger.error(STR."WebSocketClient error (\{getURI().getHost()}): ", exception);
        // FIXME: throw!
    }

    @Override
    public boolean connectBlocking() throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            logger.error("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
            throw new RuntimeException("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
        }

        boolean result = super.connectBlocking();
        connectionEstablished.set(result);
        webSocketInitializedLatch.countDown();
        return result;
    }

    public boolean isConnected() {
        if (Platform.isFxApplicationThread()) {
            return connectionEstablished.get();
        }
        return false;
    }
}
