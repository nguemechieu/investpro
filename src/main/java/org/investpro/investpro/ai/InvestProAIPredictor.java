package org.investpro.investpro.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InvestProAIPredictor implements WebSocket.Listener {

    private static final long RETRY_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI serverUri;
    private final String host;
    private final int port;

    private volatile WebSocket webSocket;
    private final StringBuilder messageBuffer = new StringBuilder();
    private final Map<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean outageLogged = new AtomicBoolean(false);

    private volatile long unavailableUntilMillis;

    public InvestProAIPredictor(String host, int port) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port;
        this.serverUri = URI.create("ws://" + host + ":" + port + "/ai");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        if (isInBackoffWindow()) {
            throw new IllegalStateException("AI predictor is temporarily unavailable.");
        }

        try {
            this.webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(serverUri, this)
                    .join();

            markAvailable();
            logInfo("Connected to AI predictor WebSocket at " + serverUri);
        } catch (Exception ex) {
            markUnavailable("Failed to connect", ex);
            throw new RuntimeException("Unable to connect to AI predictor at " + serverUri, ex);
        }
    }

    public synchronized void shutdown() {
        try {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
                logDebug("WebSocket predictor connection closed");
            }
        } catch (Exception ex) {
            logWarn("Error during WebSocket shutdown: " + ex.getMessage(), ex);
        } finally {
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return webSocket != null && !isInBackoffWindow();
    }

    public CompletableFuture<List<PredictionResponse>> streamBatchPredict(List<MarketDataRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (isInBackoffWindow()) {
            CompletableFuture<List<PredictionResponse>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("AI predictor is temporarily unavailable."));
            return failed;
        }

        ensureConnected();

        try {
            String requestId = UUID.randomUUID().toString();

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "batch_predict");
            root.put("requestId", requestId);

            ArrayNode payload = root.putArray("requests");
            for (MarketDataRequest req : requests) {
                ObjectNode item = payload.addObject();
                item.put("symbol", req.symbol());
                item.put("timestamp", req.timestamp());
                item.put("open", req.open());
                item.put("high", req.high());
                item.put("low", req.low());
                item.put("close", req.close());
                item.put("volume", req.volume());
                item.put("timeframe", req.timeframe());
                item.put("rsi", req.rsi());
                item.put("atr", req.atr());
                item.put("macd", req.macd());
                item.put("stoch", req.stoch());
                item.put("bb_upper", req.bbUpper());
                item.put("bb_lower", req.bbLower());
            }

            CompletableFuture<JsonNode> responseFuture = sendRequest(requestId, root);

            return responseFuture.thenApply(response -> {
                List<PredictionResponse> results = new ArrayList<>();
                JsonNode predictions = response.path("predictions");
                if (predictions.isArray()) {
                    for (JsonNode item : predictions) {
                        results.add(new PredictionResponse(
                                item.path("prediction").asText("HOLD"),
                                item.path("confidence").asDouble(0.0),
                                item.path("symbol").asText(""),
                                item.path("model").asText(""),
                                item.path("reason").asText("")
                        ));
                    }
                }
                return results;
            });
        } catch (Exception ex) {
            markUnavailable("Stream prediction failed", ex);
            CompletableFuture<List<PredictionResponse>> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    public boolean checkHealth() {
        if (isInBackoffWindow()) {
            return false;
        }

        ensureConnected();

        try {
            String requestId = UUID.randomUUID().toString();

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "health_check");
            root.put("requestId", requestId);

            JsonNode response = sendRequest(requestId, root).get(3, TimeUnit.SECONDS);
            boolean ok = "ok".equalsIgnoreCase(response.path("status").asText());

            if (ok) {
                markAvailable();
            } else {
                markUnavailable("Health check failed", new RuntimeException("Health status not ok"));
            }

            return ok;
        } catch (Exception ex) {
            markUnavailable("Health check failed", ex);
            return false;
        }
    }

    public void reloadModel(String path) {
        ensureConnected();

        try {
            String requestId = UUID.randomUUID().toString();

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "reload_model");
            root.put("requestId", requestId);
            root.put("modelPath", path);

            JsonNode response = sendRequest(requestId, root).get(5, TimeUnit.SECONDS);
            markAvailable();
            logInfo("Reload status: " + response.path("status").asText("unknown")
                    + " | Details: " + response.path("details").asText(""));
        } catch (Exception ex) {
            markUnavailable("Failed to reload model", ex);
        }
        printModelInfo();
    }

    public void printModelInfo() {
        ensureConnected();

        try {
            String requestId = UUID.randomUUID().toString();

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "model_info");
            root.put("requestId", requestId);

            JsonNode info = sendRequest(requestId, root).get(5, TimeUnit.SECONDS);
            markAvailable();

            logInfo("Model info - Name: " + info.path("name").asText("")
                    + ", Version: " + info.path("version").asText("")
                    + ", Framework: " + info.path("framework").asText("")
                    + ", Last Trained: " + info.path("lastTrained").asText(""));
        } catch (Exception ex) {
            markUnavailable("Failed to retrieve model info", ex);
        }
    }

    public boolean sendTradeFeedback(String tradeId, String result, double pnl) {
        ensureConnected();

        try {
            String requestId = UUID.randomUUID().toString();

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "trade_feedback");
            root.put("requestId", requestId);
            root.put("tradeId", tradeId);
            root.put("result", result);
            root.put("pnl", pnl);

            JsonNode response = sendRequest(requestId, root).get(5, TimeUnit.SECONDS);
            markAvailable();
            logInfo("Feedback response: " + response.path("status").asText("unknown"));
            return true;
        } catch (Exception ex) {
            markUnavailable("Failed to send feedback", ex);
            return false;
        }
    }

    private @NotNull CompletableFuture<JsonNode> sendRequest(String requestId, ObjectNode payload) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            ensureConnected();
            String json = objectMapper.writeValueAsString(payload);
            webSocket.sendText(json, true);
        } catch (Exception ex) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(ex);
        }

        return future.orTimeout(10, TimeUnit.SECONDS);
    }

    private void ensureConnected() {
        if (isInBackoffWindow()) {
            throw new IllegalStateException("AI predictor is temporarily unavailable.");
        }
        if (webSocket == null) {
            connect();
        }
    }

    private boolean isInBackoffWindow() {
        return System.currentTimeMillis() < unavailableUntilMillis;
    }

    private void markAvailable() {
        unavailableUntilMillis = 0;
        if (outageLogged.getAndSet(false)) {
            logInfo("AI predictor at " + host + ":" + port + " is reachable again.");
        }
    }

    private void markUnavailable(String operation, Throwable throwable) {
        unavailableUntilMillis = System.currentTimeMillis() + RETRY_BACKOFF_MILLIS;

        String reason = throwable.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = throwable.getClass().getSimpleName();
        }

        if (outageLogged.compareAndSet(false, true)) {
            logWarn(operation + " for AI predictor at " + host + ":" + port
                    + " (" + reason + "). Pausing retries for "
                    + TimeUnit.MILLISECONDS.toSeconds(RETRY_BACKOFF_MILLIS) + " seconds.", throwable);
        } else {
            logDebug(operation + " for AI predictor at " + host + ":" + port + " (" + reason + ")");
        }
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket) {
        webSocket.request(1);
        logDebug("WebSocket opened: " + serverUri);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (messageBuffer) {
            messageBuffer.append(data);

            if (last) {
                String fullMessage = messageBuffer.toString();
                messageBuffer.setLength(0);

                try {
                    JsonNode json = objectMapper.readTree(fullMessage);
                    String requestId = json.path("requestId").asText(null);

                    if (requestId != null) {
                        CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
                        if (future != null) {
                            future.complete(json);
                        }
                    }
                } catch (Exception ex) {
                    logWarn("Failed to parse WebSocket message: " + ex.getMessage(), ex);
                }
            }
        }

        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        logWarn("WebSocket closed: [" + statusCode + "] " + reason, null);
        this.webSocket = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        markUnavailable("WebSocket error", error);
        this.webSocket = null;

        for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
            future.completeExceptionally(error);
        }
        pendingRequests.clear();
    }

    private void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    private void logDebug(String message) {
        System.out.println("[DEBUG] " + message);
    }

    private void logWarn(String message, Throwable throwable) {
        System.err.println("[WARN] " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }


    public record MarketDataRequest(
            String symbol,
            long timestamp,
            double open,
            double high,
            double low,
            double close,
            double volume,
            String timeframe,
            double rsi,
            double atr,
            double macd,
            double stoch,
            double bbUpper,
            double bbLower
    ) {
        @Contract(value = " -> new", pure = true)
        public static @NotNull Builder newBuilder() {
            return new Builder();
        }



        public double getHigh() {
            return high;
        }


        public static final class Builder {
            private String symbol;
            private long timestamp;
            private double open;
            private double high;
            private double low;
            private double close;
            private double volume;
            private String timeframe;
            private double rsi = 50.0;
            private double atr;
            private double macd;
            private double stoch = 50.0;
            private double bbUpper;
            private double bbLower;

            public Builder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder open(double open) {
                this.open = open;
                return this;
            }

            public Builder high(double high) {
                this.high = high;
                return this;
            }

            public Builder low(double low) {
                this.low = low;
                return this;
            }

            public Builder close(double close) {
                this.close = close;
                return this;
            }

            public Builder volume(double volume) {
                this.volume = volume;
                return this;
            }

            public Builder timeframe(String timeframe) {
                this.timeframe = timeframe;
                return this;
            }

            public Builder rsi(double rsi) {
                this.rsi = rsi;
                return this;
            }

            public Builder atr(double atr) {
                this.atr = atr;
                return this;
            }

            public Builder macd(double macd) {
                this.macd = macd;
                return this;
            }

            public Builder stoch(double stoch) {
                this.stoch = stoch;
                return this;
            }

            public Builder bbUpper(double bbUpper) {
                this.bbUpper = bbUpper;
                return this;
            }

            public Builder bbLower(double bbLower) {
                this.bbLower = bbLower;
                return this;
            }

            public Builder setOpen(double open) {
                return open(open);
            }

            public Builder setHigh(double high) {
                return high(high);
            }

            public Builder setLow(double low) {
                return low(low);
            }

            public Builder setClose(double close) {
                return close(close);
            }

            public Builder setVolume(double volume) {
                return volume(volume);
            }

            public Builder setRsi(double rsi) {
                return rsi(rsi);
            }

            public Builder setAtr(double atr) {
                return atr(atr);
            }

            public Builder setMacd(double macd) {
                return macd(macd);
            }

            public Builder setStoch(double stoch) {
                return stoch(stoch);
            }

            public Builder setBbUpper(double bbUpper) {
                return bbUpper(bbUpper);
            }

            public Builder setBbLower(double bbLower) {
                return bbLower(bbLower);
            }

            @Contract(" -> new")
            public @NotNull MarketDataRequest build() {
                return new MarketDataRequest(
                        symbol,
                        timestamp,
                        open,
                        high,
                        low,
                        close,
                        volume,
                        timeframe,
                        rsi,
                        atr,
                        macd,
                        stoch,
                        bbUpper,
                        bbLower
                );
            }
        }
    }
    public record PredictionResponse(
            String prediction,
            double confidence,
            String symbol,
            String model,
            String reason
    ) {
        public String getPrediction() { return prediction; }
        public double getConfidence() { return confidence; }
        public String getSymbol() { return symbol; }
        public String getModel() { return model; }
        public String getReason() { return reason; }

        public boolean isBuy() { return "BUY".equalsIgnoreCase(prediction); }
        public boolean isSell() { return "SELL".equalsIgnoreCase(prediction); }
        public boolean isHold() { return "HOLD".equalsIgnoreCase(prediction); }
    }
}
