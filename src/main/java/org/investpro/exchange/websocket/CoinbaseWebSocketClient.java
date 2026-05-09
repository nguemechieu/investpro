package org.investpro.exchange.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Coinbase Advanced Trade WebSocket client.
 * <p>
 * Endpoint examples:
 * - Public market data: wss://advanced-trade-ws.coinbase.com
 * - User/authenticated data: wss://advanced-trade-ws-user.coinbase.com
 * <p>
 * Main public trade channel:
 * - market_trades
 */
@Setter
@Getter
@Slf4j
@ToString
public class CoinbaseWebSocketClient extends ExchangeWebSocketClient {

    public static final String MARKET_TRADES_CHANNEL = "market_trades";
    public static final String HEARTBEATS_CHANNEL = "heartbeats";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String jwt;

    /**
     * Coinbase sends product_id such as BTC-USD.
     * This maps TradePair -> app consumer.
     */
    protected final Map<TradePair, ExchangeStreamConsumer> liveTradeConsumers =
            Collections.synchronizedMap(new HashMap<>());

    /**
     * Generic raw stream handlers for subscribeStream(...).
     */
    private final Map<String, Consumer<String>> rawStreamHandlers = new ConcurrentHashMap<>();

    /**
     * Pairs waiting for socket open/resubscribe.
     */
    private final Set<TradePair> pendingSubscriptions =
            Collections.synchronizedSet(new HashSet<>());

    private volatile @Nullable TradePair defaultTradePair;

    public CoinbaseWebSocketClient(@NotNull URI uri, @NotNull Draft draft, @Nullable String jwt) {
        super(uri, draft);
        this.jwt = jwt == null ? "" : jwt.trim();
    }

    /**
     * Called by the neutral ExchangeWebSocketClient after socket opens.
     */
    @Override
    protected void onConnected() {
        try {
            sendSubscribe(null, HEARTBEATS_CHANNEL);
        } catch (Exception exception) {
            log.debug("Unable to subscribe Coinbase heartbeats", exception);
        }

        synchronized (liveTradeConsumers) {
            for (TradePair pair : liveTradeConsumers.keySet()) {
                try {
                    sendSubscribe(pair, MARKET_TRADES_CHANNEL);
                    pendingSubscriptions.remove(pair);
                    log.info("Resubscribed Coinbase market trades for {}", pair);
                } catch (Exception exception) {
                    pendingSubscriptions.add(pair);
                    log.warn("Unable to resubscribe Coinbase trades for {}", pair, exception);
                }
            }
        }

        synchronized (rawStreamHandlers) {
            for (String streamKey : rawStreamHandlers.keySet()) {
                CoinbaseStream stream = parseCoinbaseStream(streamKey);
                if (stream == null) {
                    continue;
                }

                try {
                    sendSubscribe(stream.tradePair(), stream.channel());
                    log.info("Resubscribed Coinbase raw stream {}", stream.key());
                } catch (Exception exception) {
                    log.warn("Unable to resubscribe Coinbase raw stream {}", stream.key(), exception);
                }
            }
        }
    }

    /**
     * Called by the neutral ExchangeWebSocketClient before generic raw dispatch.
     */
    @Override
    protected void onRawMessage(@NotNull String message) {
        JsonNode messageJson;

        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException exception) {
            log.error("Failed to parse Coinbase WebSocket message: {}", exception.getMessage(), exception);
            return;
        }

        String type = messageJson.path("type").asText("");
        String channel = messageJson.path("channel").asText("");

        log.debug("Coinbase WS message type={} channel={}", type, channel);

        if ("error".equalsIgnoreCase(type)) {
            handleErrorMessage(messageJson);
            return;
        }

        if (messageJson.has("channel") || messageJson.has("events")) {
            processAdvancedTradeMessage(messageJson);
            return;
        }

        if (!type.isBlank()) {
            processLegacyMessage(messageJson, type);
        }
    }

    private void processAdvancedTradeMessage(@NotNull JsonNode messageJson) {
        String channel = messageJson.path("channel").asText("");

        if ("subscriptions".equalsIgnoreCase(channel)) {
            log.info("Coinbase subscription acknowledged: {}", messageJson);
            return;
        }

        if (HEARTBEATS_CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }

        if (!MARKET_TRADES_CHANNEL.equalsIgnoreCase(channel)) {
            log.debug("Ignoring Coinbase channel {}", channel);
            return;
        }

        JsonNode events = messageJson.path("events");

        if (!events.isArray()) {
            log.debug("Coinbase market_trades message had no events array: {}", messageJson);
            return;
        }

        for (JsonNode event : events) {
            JsonNode trades = event.path("trades");

            if (!trades.isArray()) {
                continue;
            }

            for (JsonNode tradeNode : trades) {
                processTradeNode(tradeNode);
            }
        }
    }

    private void processTradeNode(@NotNull JsonNode tradeNode) {
        String productId = firstText(tradeNode, "product_id", "productId");
        String tradeIdText = firstText(tradeNode, "trade_id", "tradeId");
        String priceText = firstText(tradeNode, "price");
        String sizeText = firstText(tradeNode, "size");
        String sideText = firstText(tradeNode, "side");
        String timeText = firstText(tradeNode, "time", "timestamp");

        processTrade(
                productId,
                parseTradeId(tradeIdText),
                parseDouble(priceText),
                parseDouble(sizeText),
                sideText,
                timeText
        );
    }

    private void processLegacyMessage(@NotNull JsonNode messageJson, @NotNull String type) {
        try {
            switch (type) {
                case "heartbeat" -> {
                    // Nothing else needed.
                }

                case "subscriptions" -> log.info("Coinbase legacy subscription acknowledged: {}", messageJson);

                case "match" -> {
                    TradePair selectedPair = defaultTradePair;

                    if (selectedPair == null) {
                        log.debug("Skipping Coinbase legacy match because no defaultTradePair is set");
                        return;
                    }

                    processTrade(
                            toCoinbaseProductId(selectedPair),
                            parseTradeId(firstText(messageJson, "trade_id", "tradeId")),
                            parseDouble(firstText(messageJson, "price")),
                            parseDouble(firstText(messageJson, "size")),
                            firstText(messageJson, "side"),
                            firstText(messageJson, "time", "timestamp")
                    );
                }

                case "error" -> handleErrorMessage(messageJson);

                default -> log.debug("Ignoring unsupported Coinbase message type={}", type);
            }
        } catch (Exception exception) {
            log.error(
                    "Error processing Coinbase legacy message type={}: {}",
                    type,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void processTrade(
            String productId,
            long tradeId,
            double price,
            double size,
            String sideText,
            String timeText
    ) {
        if (price <= 0 || size <= 0) {
            log.debug("Skipping invalid Coinbase trade product={} price={} size={}", productId, price, size);
            return;
        }

        TradePair selectedPair = findTradePair(productId);

        if (selectedPair == null) {
            log.debug("No Coinbase live consumer found for product_id={}", productId);
            return;
        }

        ExchangeStreamConsumer consumer = liveTradeConsumers.get(selectedPair);

        if (consumer == null) {
            log.debug("No Coinbase consumer registered for {}", selectedPair);
            return;
        }

        try {
            Side side = parseSide(sideText);

            Trade trade = new Trade(
                    selectedPair,
                    price,
                    size,
                    side,
                    tradeId,
                    parseInstant(timeText)
            );

            consumer.acceptTrades(trade);

        } catch (Exception exception) {
            log.error("Error processing Coinbase trade message: {}", exception.getMessage(), exception);
        }
    }


@Override
    public void streamLiveTrades(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(liveTradesConsumer, "liveTradesConsumer must not be null");

        this.defaultTradePair = tradePair;
        liveTradeConsumers.put(tradePair, liveTradesConsumer);

        if (!isOpen()) {
            pendingSubscriptions.add(tradePair);
            log.warn("Coinbase WebSocket not open yet; registered {} for subscription after connect.", tradePair);
            return;
        }

        try {
            sendSubscribe(tradePair, MARKET_TRADES_CHANNEL);
            pendingSubscriptions.remove(tradePair);
            log.info("Subscribed Coinbase market trades for {}", tradePair);
        } catch (Exception exception) {
            pendingSubscriptions.add(tradePair);
            log.error(
                    "Failed to subscribe Coinbase trades for {}: {}",
                    tradePair,
                    exception.getMessage(),
                    exception
            );
        }
    }


    @Override
    public void stopStreamLiveTrades(@NotNull TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
        pendingSubscriptions.remove(tradePair);

        if (tradePair.equals(defaultTradePair)) {
            defaultTradePair = findAnyRegisteredPair();
        }

        if (isOpen()) {
            try {
                sendUnsubscribe(tradePair, MARKET_TRADES_CHANNEL);
            } catch (Exception exception) {
                log.warn("Unable to send Coinbase unsubscribe for {}", tradePair, exception);
            }
        }

        log.info("Unsubscribed Coinbase market trades for {}", tradePair);
    }

    public void stopAllStreamLiveTrades() {
        for (TradePair tradePair : List.copyOf(liveTradeConsumers.keySet())) {
            stopStreamLiveTrades(tradePair);
        }

        liveTradeConsumers.clear();
        pendingSubscriptions.clear();
        defaultTradePair = null;
    }

    @Override
    public boolean supportsStreamingTrades(@NotNull TradePair tradePair) {
        return !toCoinbaseProductId(tradePair).isBlank();
    }
    private CoinbaseStream stream;
    @Override
    public void subscribeStream(@NotNull String streamName, @NotNull Consumer<String> handler) {
        Objects.requireNonNull(streamName, "streamName must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        stream = parseCoinbaseStream(streamName);

        if (stream == null) {
            log.warn("Invalid Coinbase stream name: {}", streamName);
            return;
        }

        rawStreamHandlers.put(stream.key(), handler);
        registerHandler(stream.key(), handler);

        if (!isOpen()) {
            log.warn("Coinbase WebSocket not open yet; registered raw stream {}", stream.key());
            return;
        }

        try {
            sendSubscribe(stream.tradePair(), stream.channel());
            log.info("Subscribed Coinbase raw stream {}", stream.key());
        } catch (Exception exception) {
            rawStreamHandlers.remove(stream.key());
            removeHandler(stream.key());
            log.error("Failed to subscribe Coinbase raw stream {}", stream.key(), exception);
        }
    }

    @Override
    public void unsubscribeStream(@NotNull String streamName) {
         stream = parseCoinbaseStream(streamName);

        if (stream == null) {
            log.warn("Invalid Coinbase stream name for unsubscribe: {}", streamName);
            return;
        }

        rawStreamHandlers.remove(stream.key());
        removeHandler(stream.key());

        if (!isOpen()) {
            return;
        }

        try {
            sendUnsubscribe(stream.tradePair(), stream.channel());
            log.info("Unsubscribed Coinbase raw stream {}", stream.key());
        } catch (Exception exception) {
            log.warn("Failed to unsubscribe Coinbase raw stream {}", stream.key(), exception);
        }
    }

    protected void sendSubscribe(@Nullable TradePair tradePair, @NotNull String channel) {
        sendSubscriptionMessage("subscribe", tradePair, channel);
    }

    protected void sendUnsubscribe(@Nullable TradePair tradePair, @NotNull String channel) {
        sendSubscriptionMessage("unsubscribe", tradePair, channel);
    }

    private void sendSubscriptionMessage(
            @NotNull String type,
            @Nullable TradePair tradePair,
            @NotNull String channel
    ) {
        ObjectNode message = OBJECT_MAPPER.createObjectNode()
                .put("type", type)
                .put("channel", channel);

        if (tradePair != null) {
            ArrayNode productIds = message.putArray("product_ids");
            productIds.add(toCoinbaseProductId(tradePair));
        }

        if (!jwt.isBlank()) {
            message.put("jwt", jwt);
        }

        String payload = message.toString();

        log.debug("Sending Coinbase WS payload: {}", payload);
        send(payload);
    }

    private @Nullable CoinbaseStream parseCoinbaseStream(String streamName) {
        try {
            if (streamName == null || streamName.isBlank()) {
                return null;
            }

            String clean = streamName.trim();

            String channel;
            String productId;

            if (clean.contains(":")) {
                String[] parts = clean.split(":", 2);
                channel = normalizeCoinbaseChannel(parts[0]);
                productId = normalizeCoinbaseProductId(parts[1]);
            } else if (clean.contains("@")) {
                String[] parts = clean.split("@", 2);
                productId = normalizeCoinbaseProductId(parts[0]);
                channel = normalizeCoinbaseChannel(parts[1]);
            } else {
                return null;
            }

            if (channel.isBlank() || productId.isBlank()) {
                return null;
            }

            TradePair pair = tradePairFromCoinbaseProductId(productId);

            return new CoinbaseStream(channel, pair);

        } catch (Exception exception) {
            log.warn("Failed to parse Coinbase stream {}: {}", streamName, exception.getMessage());
            return null;
        }
    }

    private String normalizeCoinbaseChannel(String channel) {
        if (channel == null) {
            return "";
        }

        String normalized = channel.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "trade", "trades", "market_trade", "market_trades" -> MARKET_TRADES_CHANNEL;
            case "heartbeat", "heartbeats" -> HEARTBEATS_CHANNEL;
            case "ticker", "tickers" -> "ticker";
            case "level2", "orderbook", "order_book", "depth" -> "level2";
            case "candles", "candle", "klines", "kline" -> "candles";
            default -> normalized;
        };
    }

    private String normalizeCoinbaseProductId(String productId) {
        if (productId == null) {
            return "";
        }

        return productId
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("/", "-")
                .replace("_", "-");
    }

    private @NotNull TradePair tradePairFromCoinbaseProductId(String productId) {
        String normalized = normalizeCoinbaseProductId(productId);

        if (!normalized.contains("-")) {
            throw new IllegalArgumentException("Invalid Coinbase product id: " + productId);
        }

        String[] parts = normalized.split("-", 2);

        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid Coinbase product id: " + productId);
        }

        try {
            return TradePair.of(parts[0], parts[1]);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to create TradePair from Coinbase product id: " + productId, exception);
        }
    }

    private void handleErrorMessage(@NotNull JsonNode messageJson) {
        String errorMessage = firstText(messageJson, "message", "reason");

        if (errorMessage.isBlank()) {
            errorMessage = messageJson.toString();
        }

        log.error("Coinbase WebSocket error: {}", errorMessage);
    }

    private @Nullable TradePair findTradePair(String productId) {
        if (productId == null || productId.isBlank()) {
            TradePair fallbackPair = defaultTradePair;

            if (fallbackPair != null && liveTradeConsumers.containsKey(fallbackPair)) {
                return fallbackPair;
            }

            return null;
        }

        String normalizedProductId = productId.trim().toUpperCase(Locale.ROOT);

        synchronized (liveTradeConsumers) {
            for (TradePair pair : liveTradeConsumers.keySet()) {
                if (pair == null) {
                    continue;
                }

                String pairProductId = toCoinbaseProductId(pair);

                if (pairProductId.equalsIgnoreCase(normalizedProductId)) {
                    return pair;
                }
            }
        }

        return null;
    }

    public @Nullable TradePair getTradePair() {
        return defaultTradePair;
    }

    public void setTradePair(@NotNull TradePair tradePair) {
        this.defaultTradePair = tradePair;
    }

    private @NotNull String firstText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }

        for (String name : names) {
            JsonNode value = node.get(name);

            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private long parseTradeId(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return Math.abs((long) value.hashCode());
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.from(ISO_INSTANT.parse(value.trim()));
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }

    private Side parseSide(String value) {
        String normalized = value == null || value.isBlank()
                ? "buy"
                : value.trim().toLowerCase(Locale.ROOT);

        try {
            return Side.getSide(normalized);
        } catch (Exception exception) {
            return Side.getSide("buy");
        }
    }

    private @NotNull String toCoinbaseProductId(@NotNull TradePair tradePair) {
        return tradePair.toString('-').trim().toUpperCase(Locale.ROOT);
    }

    private @Nullable TradePair findAnyRegisteredPair() {
        synchronized (liveTradeConsumers) {
            for (TradePair pair : liveTradeConsumers.keySet()) {
                if (pair != null) {
                    return pair;
                }
            }
        }

        return null;
    }

    private record CoinbaseStream(String channel, TradePair tradePair) {
        private String key() {
            return channel + ":" + tradePair.toString('-').toUpperCase(Locale.ROOT);
        }
    }
}