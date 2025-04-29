package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

@Getter
@Setter
public abstract class CustomWebSocketClient implements WebSocket.Listener {

    private static final Logger logger = LoggerFactory.getLogger(CustomWebSocketClient.class);
    private WebSocket webSocket;
    private final URI uri;
    private final CountDownLatch initializationLatch = new CountDownLatch(1);
    private final HttpClient httpClient;

    public CustomWebSocketClient(String url) {
        this.uri = URI.create(url);
        this.httpClient = HttpClient.newHttpClient();
    }

    // Establish a WebSocket or HTTPS connection based on the URL scheme
    public void connect(@NotNull Map<String, String> headers) {
        if (uri.getScheme().equalsIgnoreCase("wss") || uri.getScheme().equalsIgnoreCase("ws")) {
            connectWebSocket(headers);
        } else if (uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http")) {
            sendHttpRequest(headers);
        } else {
            logger.error("Unsupported URL scheme: {}", uri.getScheme());
            throw new IllegalArgumentException("Unsupported protocol: " + uri.getScheme());
        }
    }

    // WebSocket Connection
    private void connectWebSocket(Map<String, String> headers) {
        WebSocket.Builder webSocketBuilder = httpClient.newWebSocketBuilder();
        headers.forEach(webSocketBuilder::header);

        webSocketBuilder.buildAsync(uri, this)
                .thenApply(webSocket -> {
                    this.webSocket = webSocket;
                    logger.info("Connected to WebSocket server: {}", uri);
                    onOpen();
                    return webSocket;
                })
                .exceptionally(ex -> {
                    logger.error("WebSocket connection failed: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    // HTTPS Request Handler (Used when URL starts with https://)
    private void sendHttpRequest(Map<String, String> headers) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.GET().build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> {
                    logger.info("Received HTTP response: {}", response);
                    onMessage(response);
                })
                .exceptionally(ex -> {
                    logger.error("HTTP request failed: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    // Send a message to the WebSocket server
    public CompletableFuture<WebSocket> sendMessage(String message) {
        if (webSocket == null) {
            logger.warn("WebSocket is not connected!");
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket not connected"));
        }
        logger.info("Sending WebSocket message: {}", message);
        return webSocket.sendText(message, true);
    }

    // WebSocket listener methods
    @Override
    public void onOpen(WebSocket webSocket) {
        logger.info("WebSocket connection established.");
        initializationLatch.countDown();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        logger.info("Received WebSocket message: {}", data);
        onMessage(data.toString());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        logger.warn("Received unexpected binary data.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        logger.info("WebSocket closed with status: {} Reason: {}", statusCode, reason);
        onClose(statusCode, reason, true);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        logger.error("WebSocket error: {}", error.getMessage(), error);
        onError(new Exception(error));
    }

    // Close WebSocket connection
    public void closeWebSocket() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client requested closure")
                    .thenRun(() -> logger.info("WebSocket connection closed."));
        }
    }

    // Abstract methods for custom implementation
    public abstract void onMessage(String message);

    public abstract void onOpen();

    public abstract void onClose(int code, String reason, boolean remote);

    public abstract void onError(Exception ex);


    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    public abstract Collection<? extends Trade> streamLiveTrades(TradePair tradePair, int secondPerCandle, UpdateInProgressCandleTask updateInProgressCandleTask);



    public abstract void subscribe(TradePair tradePair, Consumer<List<Trade>> tradeConsumer) ;


}


