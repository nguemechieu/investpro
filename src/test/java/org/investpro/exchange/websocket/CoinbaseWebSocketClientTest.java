package org.investpro.exchange.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinbaseWebSocketClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summarizesAdvancedTradeSubscriptionAcknowledgement() throws Exception {
        JsonNode acknowledgement = MAPPER.readTree("""
                {
                  "channel": "subscriptions",
                  "client_id": "",
                  "timestamp": "2026-05-20T21:21:29.845Z",
                  "sequence_num": 0,
                  "events": [
                    {
                      "subscriptions": {
                        "heartbeats": [],
                        "market_trades": ["ETH-USD", "BTC-USD"]
                      }
                    }
                  ]
                }
                """);

        assertEquals(
                "heartbeats=[], market_trades=[BTC-USD, ETH-USD]",
                CoinbaseWebSocketClient.formatSubscriptionAcknowledgement(acknowledgement)
        );
    }

    @Test
    void fallsBackToRawJsonForUnknownSubscriptionShape() throws Exception {
        JsonNode acknowledgement = MAPPER.readTree("""
                {"channel":"subscriptions","events":[{"type":"subscriptions"}]}
                """);

        String summary = CoinbaseWebSocketClient.formatSubscriptionAcknowledgement(acknowledgement);

        assertTrue(summary.contains("\"channel\":\"subscriptions\""));
    }

    @Test
    void stopAllLiveTradesUnsubscribesWithoutRecursing() throws Exception {
        TestableCoinbaseWebSocketClient client = new TestableCoinbaseWebSocketClient();
        TradePair btcUsd = TradePair.of("BTC", "USD");
        TradePair ethUsd = TradePair.of("ETH", "USD");

        ExchangeStreamConsumer consumer = new NoopExchangeStreamConsumer();

        client.liveTradeConsumers.put(btcUsd, consumer);
        client.liveTradeConsumers.put(ethUsd, consumer);
        client.setTradePair(btcUsd);

        assertDoesNotThrow(client::stopAllStreamLiveTrades);

        assertTrue(client.liveTradeConsumers.isEmpty());
        assertNull(client.getTradePair());
        assertEquals(2, client.sentPayloads.size());
        assertTrue(client.sentPayloads.stream().allMatch(payload -> payload.contains("\"type\":\"unsubscribe\"")));
    }

    private static final class TestableCoinbaseWebSocketClient extends CoinbaseWebSocketClient {

        private final List<String> sentPayloads = new ArrayList<>();

        private TestableCoinbaseWebSocketClient() {
            super(URI.create("wss://example.com"), new Draft_6455(), "");
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(String text) {
            sentPayloads.add(text);
        }
    }

    private static final class NoopExchangeStreamConsumer implements ExchangeStreamConsumer {

        @Override
        public void onStatus(@Nullable String exchangeName, @Nullable String message) {
        }

        @Override
        public void onBalance(@Nullable String exchangeName, @Nullable Account account) {
        }

        @Override
        public void onOrder(@Nullable String exchangeName, @Nullable OpenOrder order) {
        }

        @Override
        public void onOrders(String exchangeName, List<OpenOrder> orders) {
        }

        @Override
        public void onFill(String exchangeName, TradePair tradePair, Trade fill) {
        }

        @Override
        public void onOrderAccepted(String exchangeName, String orderId) {
        }

        @Override
        public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
        }

        @Override
        public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
        }

        @Override
        public void onOrderCancelled(String exchangeName, String orderId) {
        }

        @Override
        public void onRawMessage(String exchangeName, String channel, String rawJson) {
        }

        @Override
        public boolean hasReceivedEvents() {
            return false;
        }

        @Override
        public boolean hasErrors() {
            return false;
        }

        @Override
        public UiExchangeStreamConsumer onOrdersUpdate(@Nullable Consumer<List<OpenOrder>> setAll) {
            return null;
        }
    }
}
