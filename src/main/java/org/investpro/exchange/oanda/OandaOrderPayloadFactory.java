package org.investpro.exchange.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.exchange.core.NormalizedOrderRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * OandaOrderPayloadFactory - Converts NormalizedOrderRequest to OANDA JSON payloads.
 */
public class OandaOrderPayloadFactory {
    private static final Logger logger = Logger.getLogger(OandaOrderPayloadFactory.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Create an OANDA order payload.
     * OANDA uses 'orders' array with detailed order objects.
     */
    public String createOrderPayload(@NotNull NormalizedOrderRequest order) {
        ObjectNode rootPayload = mapper.createObjectNode();
        
        // OANDA wraps orders in an 'orders' array
        ObjectNode orderNode = mapper.createObjectNode();
        
        orderNode.put("instrument", order.getProductId());
        orderNode.put("units", formatUnits(order.getQuantity(), order.getSide()));
        
        // Order type mapping
        switch (order.getOrderType()) {
            case MARKET:
                orderNode.put("type", "MARKET");
                break;
            case LIMIT:
                orderNode.put("type", "LIMIT");
                orderNode.put("price", formatPrice(order.getLimitPrice()));
                orderNode.put("timeinforce", formatTimeInForce(order.getTimeInForce()));
                break;
            case STOP:
                orderNode.put("type", "STOP");
                orderNode.put("price", formatPrice(order.getStopPrice()));
                orderNode.put("timeInForce", formatTimeInForce(order.getTimeInForce()));
                break;
            case STOP_LIMIT:
                orderNode.put("type", "LIMIT");
                orderNode.put("price", formatPrice(order.getLimitPrice()));
                orderNode.put("priceBound", formatPrice(order.getStopPrice()));
                break;
            case TRAILING_STOP:
                orderNode.put("type", "TRAILING_STOP_LOSS");
                orderNode.put("distance", formatPrice(order.getTrailingAmount()));
                orderNode.put("timeInForce", formatTimeInForce(order.getTimeInForce()));
                break;
        }
        
        // Bracket orders
        if (order.isBracketOrder()) {
            ObjectNode takeProfitOrder = mapper.createObjectNode();
            takeProfitOrder.put("type", "TAKE_PROFIT");
            if (order.hasTakeProfit()) {
                takeProfitOrder.put("price", formatPrice(order.getTakeProfitPrice()));
            }
            orderNode.set("takeProfitOnFill", takeProfitOrder);
            
            ObjectNode stopLossOrder = mapper.createObjectNode();
            stopLossOrder.put("type", "STOP_LOSS");
            if (order.hasStopLoss()) {
                stopLossOrder.put("price", formatPrice(order.getStopLossPrice()));
            }
            orderNode.set("stopLossOnFill", stopLossOrder);
        }
        
        rootPayload.set("order", orderNode);
        
        return rootPayload.toString();
    }
    
    private String formatUnits(BigDecimal quantity, NormalizedOrderRequest.Side side) {
        if (quantity == null) return "0";
        
        // OANDA uses positive/negative units to indicate side
        BigDecimal units = quantity.stripTrailingZeros();
        if (side == NormalizedOrderRequest.Side.SELL) {
            units = units.negate();
        }
        return units.toPlainString();
    }
    
    private String formatPrice(BigDecimal price) {
        if (price == null) return "0";
        return price.stripTrailingZeros().toPlainString();
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
     * Parse an OANDA order response.
     */
    public OandaOrderResponse parseResponse(String jsonResponse) {
        try {
            JsonNode node = mapper.readTree(jsonResponse);
            
            // Response can have either 'orderFillTransaction' or 'orderCreateTransaction'
            JsonNode txn = node.has("orderFillTransaction") ? 
                node.get("orderFillTransaction") : 
                node.get("orderCreateTransaction");
            
            if (txn == null) {
                logger.severe("No transaction in OANDA response");
                return null;
            }
            
            return new OandaOrderResponse(
                txn.get("id").asText(),
                txn.get("instrument").asText(),
                txn.get("units").asText(),
                txn.get("price") != null ? txn.get("price").asText() : null,
                txn.get("status") != null ? txn.get("status").asText() : "ACCEPTED",
                txn.get("time").asText()
            );
        } catch (Exception e) {
            logger.severe("Failed to parse OANDA response: " + e.getMessage());
            return null;
        }
    }

    public record OandaOrderResponse(String transactionId, String instrument, String units, String price, String status,
                                     String time) {
    }
}
