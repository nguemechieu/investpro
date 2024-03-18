package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Pair;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CoinbaseWebSocketClient extends WebSocketClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Set<TradePair> tradePairs = Set.of(new TradePair("BTC", "USD"), new TradePair("ETH", "USD"), new TradePair("LTC", "USD"), new TradePair("XRP", "USD"));

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    protected BooleanProperty connectionEstablished;
    private LiveTrade liveTradeConsumers;


    public CoinbaseWebSocketClient(URI uri, Draft draft) throws SQLException, ClassNotFoundException {
        super(uri, draft);


        connectionEstablished = new SimpleBooleanProperty(false);
        this.connect();
        
    }



    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);
        }

        TradePair tradePair = null;
        try {
            tradePair = parseTradePair(messageJson);
        } catch (CurrencyNotFoundException exception) {
            logger.error(STR."coinbase websocket client: could not initialize trade pair: \{messageJson.get("product_id").asText()}", exception);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "heartbeat":
                send(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString());
                break;
            case "match":
                if (liveTradeConsumers.containsKey(tradePair)) {
                    assert tradePair != null;
                    Trade newTrade = new Trade(tradePair,
                            DefaultMoney.of(new BigDecimal(messageJson.get("price").asText()),
                                    tradePair.getCounterCurrency()),
                            DefaultMoney.of(new BigDecimal(messageJson.get("size").asText()),
                                    tradePair.getBaseCurrency()),
                            side, messageJson.at("trade_id").asLong(),
                            Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText())));
                    liveTradeConsumers.acceptTrades(Collections.singletonList(newTrade));
                }
                break;
            case "error":
                throw new IllegalArgumentException(STR."Error on Coinbase websocket client: \{messageJson.get("message").asText()}");
            default:
                throw new IllegalStateException(STR."Unhandled message type on Gdax websocket client: \{messageJson.get("type").asText()}");
        }
    }

    private @NotNull TradePair parseTradePair(@NotNull JsonNode messageJson) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        final String productId = messageJson.get("product_id").asText();
        final String[] products = productId.split("-");
        TradePair tradePair;
        if (products[0].equalsIgnoreCase("BTC")) {
            tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
        } else {
            // products[0] == "ETH"
            if (products[1].equalsIgnoreCase("usd")) {
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
            } else {
                // productId == "ETH-BTC"
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));
            }
        }

        return tradePair;
    }

    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                .put("product_id", tradePair.toString('-')).toPrettyString());
        liveTradeConsumers.put(tradePair, liveTradesConsumer);
    }


    public void stopStreamLiveTrades(TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
    }
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return tradePairs.contains(tradePair);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
    }

    public void setLiveTradeConsumers(LiveTrade liveTradeConsumers) {
        this.liveTradeConsumers = liveTradeConsumers;
    }
}
