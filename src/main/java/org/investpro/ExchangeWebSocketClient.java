package org.investpro;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public abstract class ExchangeWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    protected final Map<TradePair, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<>();
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);
    private List<Trade> trade=new ArrayList<>();

    public ExchangeWebSocketClient(URI serverUri) {
        super(serverUri);
        logger.info("ExchangeWebSocketClient created");

    }

    public ExchangeWebSocketClient(URI serverUri, Draft protocolDraft) {
        super(serverUri, protocolDraft);
        logger.info("ExchangeWebSocketClient created");
    }

    public ExchangeWebSocketClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
    }

    public ExchangeWebSocketClient(URI serverUri, Draft protocolDraft, Map<String, String> httpHeaders) {
        super(serverUri, protocolDraft, httpHeaders);
    }

    public ExchangeWebSocketClient(URI serverUri, Draft protocolDraft, Map<String, String> httpHeaders, int connectTimeout) {
        super(serverUri, protocolDraft, httpHeaders, connectTimeout);
    }



    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);


    public abstract boolean isStreamingTradesSupported(TradePair tradePair);

    public abstract boolean isStreamingTradesEnabled(TradePair tradePair);

    public abstract void request(long n);

    public abstract CompletableFuture<WebSocket> sendText(CharSequence data, boolean last);


    public abstract CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last);

    public abstract CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    public abstract CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    public abstract CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    public abstract String getSubprotocol();

    public abstract boolean isOutputClosed();

    public abstract boolean isInputClosed();

    public abstract void abort();


    public abstract long getDefaultAsyncSendTimeout();

    public abstract void setAsyncSendTimeout(long timeout);

    public abstract Session connectToServer(Object endpoint, ClientEndpointConfig path);

    public abstract Session connectToServer(Class<?> annotatedEndpointClass, URI path);

    public abstract Session connectToServer(Endpoint endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path) throws URISyntaxException;


    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract Session connectToServer(Class<? extends Endpoint> endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path);

    public abstract long getDefaultMaxSessionIdleTimeout();

    public abstract void setDefaultMaxSessionIdleTimeout(long timeout);

    public abstract int getDefaultMaxBinaryMessageBufferSize();

    public abstract void setDefaultMaxBinaryMessageBufferSize(int max);

    public abstract int getDefaultMaxTextMessageBufferSize();

    public abstract void setDefaultMaxTextMessageBufferSize(int max);

    public abstract Set<Extension> getInstalledExtensions();

    public abstract double getPrice(TradePair tradePair) throws IOException, InterruptedException;

    public void streamLiveTrades(TradePair tradePair, Object o) {
        LiveTradesConsumer liveTradesConsumer = liveTradeConsumers.get(tradePair);
        if (liveTradesConsumer == null) {
            liveTradesConsumer = new LiveTradesConsumer() {
                @Override
                public void acceptTrades(List<Trade> trades) {

                }

                @Override
                public void onConnectionEstablished() throws IOException, InterruptedException, ParseException {

                }

                @Override
                public void onConnectionFailed() throws IOException, InterruptedException {

                }

                @Override
                public void onMessage(String message) throws IOException, InterruptedException {

                }

                @Override
                public void accept(Trade trade) {

                }

                @Override
                public void close() {

                }
            };
            liveTradeConsumers.put(tradePair, liveTradesConsumer);
        }
        liveTradesConsumer.acceptTrades(trade);
    }
}