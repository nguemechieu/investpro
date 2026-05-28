package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.exchange.core.NormalizedOrderRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * CoinbaseOrderPayloadFactory - Converts NormalizedOrderRequest to Coinbase JSON payloads.
 */
public class CoinbaseOrderPayloadFactory {
    private static final Logger logger = Logger.getLogger(CoinbaseOrderPayloadFactory.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Create a Coinbase REST API order payload.
     */
    public String createOrderPayload(@NotNull NormalizedOrderRequest order) {
        ObjectNode payload = mapper.createObjectNode();
        
        payload.put("product_id", order.getProductId());
        payload.put("side", order.getSide().name().toLowerCase());
        
        // Order type and price
        switch (order.getOrderType()) {
            case MARKET:
                payload.put("order_type", "market");
                payload.put("size", formatQuantity(order.getQuantity()));
                break;
            case LIMIT:
                payload.put("order_type", "limit");
                payload.put("price", formatPrice(order.getLimitPrice()));
                payload.put("size", formatQuantity(order.getQuantity()));
                payload.put("time_in_force", formatTimeInForce(order.getTimeInForce()));
                if (order.isPostOnly()) {
                    payload.put("post_only", true);
                }
                break;

            case STOP_LIMIT:
                payload.put("order_type", "stop_limit");
                payload.put("stop_price", formatPrice(order.getStopPrice()));
                payload.put("limit_price", formatPrice(order.getLimitPrice()));
                payload.put("size", formatQuantity(order.getQuantity()));
                break;
        }
        
        // Advanced order options
        if (order.isBracketOrder()) {
            ObjectNode advancedOptions = mapper.createObjectNode();
            if (order.hasStopLoss()) {
                ObjectNode stopLoss = mapper.createObjectNode();
                stopLoss.put("trigger_price", formatPrice(order.getStopLossPrice()));
                advancedOptions.set("stop_loss", stopLoss);
            }
            if (order.hasTakeProfit()) {
                ObjectNode takeProfit = mapper.createObjectNode();
                takeProfit.put("trigger_price", formatPrice(order.getTakeProfitPrice()));
                advancedOptions.set("take_profit", takeProfit);
            }
            payload.set("advanced_options", advancedOptions);
        }
        
        // Leverage (if applicable)
        if (order.getLeverage().compareTo(BigDecimal.ONE) > 0) {
            payload.put("leverage", formatPrice(order.getLeverage()));
        }
        
        return payload.toString();
    }
    
    private String formatPrice(BigDecimal price) {
        if (price == null) return "0";
        return price.stripTrailingZeros().toPlainString();
    }
    
    private String formatQuantity(BigDecimal quantity) {
        if (quantity == null) return "0";
        return quantity.stripTrailingZeros().toPlainString();
    }
    
    private String formatTimeInForce(NormalizedOrderRequest.TimeInForce timeInForce) {
        return switch (timeInForce) {
            case FOK -> "FOK";
            case IOC -> "IOC";
            case GTC -> "GTC";
            case GTD -> "GTD";
        };
    }
    
    /**
     * Parse a Coinbase order response.
     */
    public CoinbaseOrderResponse parseResponse(String jsonResponse) {
        try {
            JsonNode node = mapper.readTree(jsonResponse);
            
            return new CoinbaseOrderResponse(
                node.get("id").asText(),
                node.get("product_id").asText(),
                node.get("side").asText(),
                new BigDecimal(node.get("size").asText()),
                node.get("price") != null ? new BigDecimal(node.get("price").asText()) : null,
                node.get("status").asText(),
                node.get("created_at").asText()
            );
        } catch (Exception e) {
            logger.severe("Failed to parse Coinbase response: " + e.getMessage());
            return null;
        }
    }

    public record CoinbaseOrderResponse(String orderId, String productId, String side, BigDecimal size,
                                        BigDecimal price, String status, String createdAt) {
    }
}
