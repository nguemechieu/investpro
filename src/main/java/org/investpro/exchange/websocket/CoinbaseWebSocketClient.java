package org.investpro.exchange.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Coinbase Advanced Trade WebSocket client.
 *
 * Primary endpoint:
 * - Public market data: wss://advanced-trade-ws.coinbase.com
 * - User/authenticated channels: wss://advanced-trade-ws-user.coinbase.com
 *
 * Main public market channel used here:
 * - market_trades
 *
 * Notes:
 * - market_trades does not require JWT.
 * - authenticated/user channels require JWT.
 * - This client supports multiple TradePair subscriptions.
 * - Incoming trades are routed by Coinbase product_id, not by one global TradePair.
 */
@Setter
@Getter
public class CoinbaseWebSocketClient extends WebSocketClient {

    public static final String MARKET_TRADES_CHANNEL = "market_trades";
    public static final String HEARTBEATS_CHANNEL = "heartbeats";

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String jwt;

    /**
     * UI/broker connection state.
     */
    public BooleanProperty connectionEstablished = new SimpleBooleanProperty(false);

    /**
     * Source of truth for active symbol routing.
     *
     * Coinbase sends product_id such as BTC-USD.
     * We match that product_id against these TradePair keys.
     */
    protected final Map<TradePair, LiveTradesConsumer> liveTradeConsumers =
            Collections.synchronizedMap(new HashMap<>());

    /**
     * Pairs registered before the WebSocket is open, or pairs that failed to subscribe.
     */
    private final Set<TradePair> pendingSubscriptions =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Last/default pair.
     *
     * This is only a fallback for legacy Coinbase Pro-style messages that may not include product_id.
     * Do not use this as the main routing mechanism.
     */
    private volatile @Nullable TradePair defaultTradePair;

    public CoinbaseWebSocketClient(URI uri, Draft draft, String jwt) {
        super(uri, draft);
        this.jwt = jwt == null ? "" : jwt.trim();
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        setConnectionEstablished(true);
        logger.info("Coinbase WebSocket opened: {}", getURI());

        /*
         * Heartbeats help keep subscriptions open.
         */
        try {
            sendSubscribe(null, HEARTBEATS_CHANNEL);
        } catch (Exception exception) {
            logger.debug("Unable to subscribe to Coinbase heartbeats", exception);
        }

        synchronized (liveTradeConsumers) {
            for (TradePair subscribedPair : liveTradeConsumers.keySet()) {
                try {
                    sendSubscribe(subscribedPair, MARKET_TRADES_CHANNEL);
                    pendingSubscriptions.remove(subscribedPair);
                    logger.info("Resubscribed to Coinbase market trades for {}", subscribedPair);
                } catch (Exception exception) {
                    pendingSubscriptions.add(subscribedPair);
                    logger.warn("Unable to resubscribe to Coinbase trades for {}", subscribedPair, exception);
                }
            }
        }
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            logger.warn("Received empty Coinbase WebSocket message");
            return;
        }

        JsonNode messageJson;

        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException exception) {
            logger.error("Failed to parse Coinbase WebSocket message: {}", exception.getMessage(), exception);
            return;
        }

        String type = messageJson.path("type").asText("");
        String channel = messageJson.path("channel").asText("");

        logger.debug("Coinbase WS message type={} channel={}", type, channel);

        if ("error".equalsIgnoreCase(type)) {
            handleErrorMessage(messageJson);
            return;
        }

        /*
         * Coinbase Advanced Trade messages usually contain:
         * {
         *   "channel": "...",
         *   "events": [...]
         * }
         */
        if (messageJson.has("channel") || messageJson.has("events")) {
            processAdvancedTradeMessage(messageJson);
            return;
        }

        /*
         * Backward compatibility for old Coinbase Pro-style messages.
         */
        if (!type.isBlank()) {
            processLegacyMessage(messageJson, type);
        }
    }

    private void processAdvancedTradeMessage(@NotNull JsonNode messageJson) {
        String channel = messageJson.path("channel").asText("");

        if ("subscriptions".equalsIgnoreCase(channel)) {
            setConnectionEstablished(true);
            logger.info("Coinbase subscription acknowledged: {}", messageJson);
            return;
        }

        if (HEARTBEATS_CHANNEL.equalsIgnoreCase(channel)) {
            setConnectionEstablished(true);
            return;
        }

        if (!MARKET_TRADES_CHANNEL.equalsIgnoreCase(channel)) {
            logger.debug("Ignoring Coinbase channel {}", channel);
            return;
        }

        JsonNode events = messageJson.path("events");

        if (!events.isArray()) {
            logger.debug("Coinbase market_trades message had no events array: {}", messageJson);
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
                case "heartbeat" -> setConnectionEstablished(true);

                case "subscriptions" -> {
                    setConnectionEstablished(true);
                    logger.info("Coinbase legacy subscription acknowledged: {}", messageJson);
                }

                case "match" -> {
                    TradePair selectedPair = defaultTradePair;

                    if (selectedPair == null) {
                        logger.debug("Skipping Coinbase legacy match because no defaultTradePair is set");
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

                default -> logger.debug("Ignoring unsupported Coinbase message type={}", type);
            }
        } catch (Exception exception) {
            logger.error(
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
            logger.debug("Skipping invalid Coinbase trade product={} price={} size={}", productId, price, size);
            return;
        }

        TradePair selectedPair = findTradePair(productId);

        if (selectedPair == null) {
            logger.debug("No Coinbase live consumer found for product_id={}", productId);
            return;
        }

        LiveTradesConsumer consumer = liveTradeConsumers.get(selectedPair);

        if (consumer == null) {
            logger.debug("No Coinbase consumer registered for {}", selectedPair);
            return;
        }

        try {
            Side side = parseSide(sideText);

            Trade newTrade = new Trade(
                    selectedPair,
                    price,
                    size,
                    side,
                    tradeId,
                    parseInstant(timeText)
            );

            consumer.acceptTrades(newTrade);
        } catch (Exception exception) {
            logger.error("Error processing Coinbase trade message: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Finds the registered TradePair that matches Coinbase's product_id.
     * <p>
     * Example:
     * product_id = BTC-USD
     * TradePair.toString('-') = BTC-USD
     */
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

    /**
     * Subscribe to live market trades for a specific trading pair.
     */
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        if (liveTradesConsumer == null) {
            logger.error("Attempted to stream Coinbase trades with null consumer for {}", tradePair);
            return;
        }

        this.defaultTradePair = tradePair;
        liveTradeConsumers.put(tradePair, liveTradesConsumer);

        if (!isOpen()) {
            pendingSubscriptions.add(tradePair);
            logger.warn(
                    "Coinbase WebSocket not open yet; registered {} for subscription after connect.",
                    tradePair
            );
            return;
        }

        try {
            sendSubscribe(tradePair, MARKET_TRADES_CHANNEL);
            pendingSubscriptions.remove(tradePair);
            logger.info("Subscribed to Coinbase market trades for {}", tradePair);
        } catch (Exception exception) {
            pendingSubscriptions.add(tradePair);
            logger.error(
                    "Failed to subscribe to Coinbase trades for {}: {}",
                    tradePair,
                    exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Stop streaming live trades for a specific trading pair.
     */
    public void stopStreamLiveTrades(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }

        liveTradeConsumers.remove(tradePair);
        pendingSubscriptions.remove(tradePair);

        if (tradePair.equals(defaultTradePair)) {
            defaultTradePair = findAnyRegisteredPair();
        }

        if (isOpen()) {
            try {
                sendUnsubscribe(tradePair, MARKET_TRADES_CHANNEL);
            } catch (Exception exception) {
                logger.warn("Unable to send Coinbase unsubscribe for {}", tradePair, exception);
            }
        }

        logger.info("Unsubscribed from Coinbase market trades for {}", tradePair);
    }

    public void stopAllStreamLiveTrades() {
        for (TradePair tradePair : List.copyOf(liveTradeConsumers.keySet())) {
            stopStreamLiveTrades(tradePair);
        }

        liveTradeConsumers.clear();
        pendingSubscriptions.clear();
        defaultTradePair = null;
    }

    /**
     * Coinbase Advanced Trade supports market_trades for valid product IDs.
     * This method should not depend on whether a consumer is already registered.
     */
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return tradePair != null && !toCoinbaseProductId(tradePair).isBlank();
    }

    protected void sendSubscribe(TradePair tradePair, String channel) {
        sendSubscriptionMessage("subscribe", tradePair, channel);
    }

    protected void sendUnsubscribe(TradePair tradePair, String channel) {
        sendSubscriptionMessage("unsubscribe", tradePair, channel);
    }

    private void sendSubscriptionMessage(
            String type,
            TradePair tradePair,
            String channel
    ) {
        ObjectNode message = OBJECT_MAPPER.createObjectNode()
                .put("type", type)
                .put("channel", channel);

        if (tradePair != null) {
            ArrayNode productIds = message.putArray("product_ids");
            productIds.add(toCoinbaseProductId(tradePair));
        }

        /*
         * Public market channels do not require JWT.
         * Authenticated/user channels require JWT.
         */
        if (!jwt.isBlank()) {
            message.put("jwt", jwt);
        }

        String payload = message.toString();

        logger.debug("Sending Coinbase WS subscription payload: {}", payload);
        send(payload);
    }

    private void handleErrorMessage(@NotNull JsonNode messageJson) {
        String errorMessage = firstText(messageJson, "message", "reason");

        if (errorMessage.isBlank()) {
            errorMessage = messageJson.toString();
        }

        logger.error("Coinbase WebSocket error: {}", errorMessage);
    }

    public @Nullable TradePair getTradePair() {
        return defaultTradePair;
    }

    public void setTradePair(@NotNull TradePair tradePair) {
        this.defaultTradePair = tradePair;
    }

    private void setConnectionEstablished(boolean value) {
        try {
            if (Platform.isFxApplicationThread()) {
                connectionEstablished.set(value);
            } else {
                Platform.runLater(() -> connectionEstablished.set(value));
            }
        } catch (IllegalStateException exception) {
            /*
             * In tests or headless environments, JavaFX may not be initialized.
             */
            connectionEstablished.set(value);
        }
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

    @Override
    public void onClose(int code, String reason, boolean remote) {
        setConnectionEstablished(false);

        String remoteText = remote ? "remote" : "local";
        logger.info("Coinbase WebSocket closed ({}, code={}): {}", remoteText, code, reason);

        synchronized (liveTradeConsumers) {
            pendingSubscriptions.addAll(liveTradeConsumers.keySet());
        }
    }

    @Override
    public void onError(Exception exception) {
        setConnectionEstablished(false);

        if (exception == null) {
            logger.error("Coinbase WebSocket unknown error");
            return;
        }

        logger.error("Coinbase WebSocket error: {}", exception.getMessage(), exception);
    }
}