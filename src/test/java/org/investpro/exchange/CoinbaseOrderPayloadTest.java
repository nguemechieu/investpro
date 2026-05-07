package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoinbaseOrderPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void normalizesMarketOrderWithSideAndBaseSize() throws Exception {
        Order order = new Order();
        order.setSymbol("BTC/USD");
        order.setType("MARKET");
        order.setSide(Side.BUY);
        order.setQuantity(0.0001);

        JsonNode payload = MAPPER.readTree(Coinbase.normalizeOrderPayload(MAPPER.valueToTree(order)));

        assertEquals("BTC-USD", payload.path("product_id").asText());
        assertEquals("BUY", payload.path("side").asText());
        assertEquals("0.0001", payload.path("order_configuration").path("market_market_ioc").path("base_size").asText());
    }

    @Test
    void normalizesLimitOrderWithoutLosingRequestedType() throws Exception {
        Order order = new Order();
        order.setSymbol("ETH-USD");
        order.setType("LIMIT");
        order.setSide(Side.SELL);
        order.setQuantity(1.25);
        order.setPrice(2400.50);

        JsonNode payload = MAPPER.readTree(Coinbase.normalizeOrderPayload(MAPPER.valueToTree(order)));
        JsonNode limit = payload.path("order_configuration").path("limit_limit_gtc");

        assertEquals("SELL", payload.path("side").asText());
        assertEquals("1.25", limit.path("base_size").asText());
        assertEquals("2400.5", limit.path("limit_price").asText());
        assertFalse(limit.path("post_only").asBoolean());
    }

    @Test
    void keepsAdvancedTradePayloadUnchangedWhenAlreadyNormalized() throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("client_order_id", "client-1");
        payload.put("product_id", "BTC-USD");
        payload.put("side", "BUY");
        payload.set("order_configuration", MAPPER.createObjectNode()
                .set("market_market_ioc", MAPPER.createObjectNode().put("base_size", "0.01")));

        JsonNode normalized = MAPPER.readTree(Coinbase.normalizeOrderPayload(payload));

        assertEquals("client-1", normalized.path("client_order_id").asText());
        assertTrue(normalized.path("order_configuration").has("market_market_ioc"));
    }

    @Test
    void returnsOrderIdFromSuccessfulCreateOrderResponse() {
        String response = """
                {"success":true,"success_response":{"order_id":"order-123"}}
                """;

        assertEquals("order-123", Coinbase.requireSuccessfulOrderResponse(response));
    }

    @Test
    void rejectsCoinbaseCreateOrderFailureResponse() {
        String response = """
                {"success":false,"error_response":{"message":"The order configuration was invalid"}}
                """;

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> Coinbase.requireSuccessfulOrderResponse(response));

        assertTrue(exception.getMessage().contains("The order configuration was invalid"));
    }

}
