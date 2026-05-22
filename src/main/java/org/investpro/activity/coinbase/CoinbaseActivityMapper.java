package org.investpro.activity.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.time.Instant;

public final class CoinbaseActivityMapper {
    public static final String EXCHANGE_ID = "COINBASE";

    private CoinbaseActivityMapper() {
    }

    public static BrokerActivityEvent mapFill(JsonNode fill) {
        String nativeType = "FILL";
        String tradeId = ActivityJsonMapperSupport.text(fill, "trade_id");
        String orderId = ActivityJsonMapperSupport.text(fill, "order_id");
        String productId = ActivityJsonMapperSupport.text(fill, "product_id");
        String sideStr = ActivityJsonMapperSupport.text(fill, "side");
        String eventId = tradeId != null ? tradeId : ("cb-fill-" + Instant.now().toEpochMilli());

        return BrokerActivityEvent.builder()
                .exchangeId(EXCHANGE_ID)
                .eventId(eventId)
                .nativeEventType(nativeType)
                .activityType(BrokerActivityType.ORDER_FILLED)
                .orderId(orderId)
                .tradeId(tradeId)
                .tradePair(ActivityJsonMapperSupport.pair(productId != null ? productId.replace("-", "") : null))
                .side(ActivityJsonMapperSupport.side(sideStr))
                .filledQuantity(ActivityJsonMapperSupport.decimal(fill, "size"))
                .price(ActivityJsonMapperSupport.decimal(fill, "price"))
                .fee(ActivityJsonMapperSupport.decimal(fill, "fee"))
                .eventTime(ActivityJsonMapperSupport.instant(fill, "done_at", "created_at"))
                .source(EXCHANGE_ID)
                .cursor(tradeId)
                .rawJson(fill.toString())
                .terminalEvent(true)
                .build();
    }

    public static BrokerActivityEvent mapOrder(JsonNode order) {
        String status = ActivityJsonMapperSupport.text(order, "status");
        String orderId = ActivityJsonMapperSupport.text(order, "id");
        String productId = ActivityJsonMapperSupport.text(order, "product_id");
        String sideStr = ActivityJsonMapperSupport.text(order, "side");
        BrokerActivityType activityType = mapOrderStatus(status);
        String eventId = orderId != null ? orderId : ("cb-order-" + Instant.now().toEpochMilli());

        return BrokerActivityEvent.builder()
                .exchangeId(EXCHANGE_ID)
                .eventId(eventId)
                .nativeEventType("ORDER_" + (status != null ? status.toUpperCase() : "UNKNOWN"))
                .activityType(activityType)
                .orderId(orderId)
                .tradePair(ActivityJsonMapperSupport.pair(productId != null ? productId.replace("-", "") : null))
                .side(ActivityJsonMapperSupport.side(sideStr))
                .requestedQuantity(ActivityJsonMapperSupport.decimal(order, "size"))
                .filledQuantity(ActivityJsonMapperSupport.decimal(order, "filled_size"))
                .price(ActivityJsonMapperSupport.decimal(order, "price", "executed_value"))
                .fee(ActivityJsonMapperSupport.decimal(order, "fill_fees"))
                .eventTime(ActivityJsonMapperSupport.instant(order, "done_at", "created_at"))
                .source(EXCHANGE_ID)
                .cursor(orderId)
                .rawJson(order.toString())
                .terminalEvent(isTerminal(activityType))
                .build();
    }

    private static BrokerActivityType mapOrderStatus(String status) {
        if (status == null) return BrokerActivityType.UNKNOWN;
        return switch (status.toLowerCase()) {
            case "open" -> BrokerActivityType.ORDER_SUBMITTED;
            case "done" -> BrokerActivityType.ORDER_FILLED;
            case "cancelled", "canceled" -> BrokerActivityType.ORDER_CANCELLED;
            case "rejected" -> BrokerActivityType.ORDER_REJECTED;
            case "pending" -> BrokerActivityType.ORDER_PENDING_CONFIRMATION;
            default -> BrokerActivityType.UNKNOWN;
        };
    }

    private static boolean isTerminal(BrokerActivityType type) {
        return type == BrokerActivityType.ORDER_FILLED ||
                type == BrokerActivityType.ORDER_CANCELLED ||
                type == BrokerActivityType.ORDER_REJECTED ||
                type == BrokerActivityType.ORDER_EXPIRED;
    }
}
