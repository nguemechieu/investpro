package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CountDownLatch;


public abstract class ExchangeWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);
    protected final BooleanProperty connectionEstablished;
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);

    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(clientUri, clientDraft);
        connectionEstablished = new SimpleBooleanProperty(false);
    }



    @Override
    public void onError(Exception exception) {
        logger.error("WebSocketClient error ({}): ", getURI().getHost(), exception);
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
}
