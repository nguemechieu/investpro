package org.investpro.activity.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.util.Locale;

public final class CoinbaseActivityMapper {
    public static final String EXCHANGE_ID = "COINBASE";

    private CoinbaseActivityMapper() {
    }

    public static BrokerActivityEvent mapOrderOrFill(JsonNode payload) {
        String nativeType = ActivityJsonMapperSupport.text(payload, "type", "status", "order_status");
        String eventId = ActivityJsonMapperSupport.text(payload, "fill_id", "trade_id", "order_id", "id");
        String productId = ActivityJsonMapperSupport.text(payload, "product_id", "productId", "symbol");
        BrokerActivityType activityType = mapType(nativeType, payload);

        return BrokerActivityEvent.builder()
                .eventId(eventId == null ? "coinbase-" + System.nanoTime() : eventId)
                .exchangeId(EXCHANGE_ID)
                .accountId(ActivityJsonMapperSupport.text(payload, "account_id", "portfolio_id", "accountId"))
                .nativeEventType(nativeType)
                .activityType(activityType)
                .orderId(ActivityJsonMapperSupport.text(payload, "order_id", "orderId", "id"))
                .tradeId(ActivityJsonMapperSupport.text(payload, "trade_id", "fill_id"))
                .tradePair(ActivityJsonMapperSupport.pair(productId))
                .side(ActivityJsonMapperSupport.side(ActivityJsonMapperSupport.text(payload, "side")))
                .requestedQuantity(ActivityJsonMapperSupport.decimal(payload, "size", "order_quantity", "base_size"))
                .filledQuantity(ActivityJsonMapperSupport.decimal(payload, "filled_size", "size", "base_size"))
                .remainingQuantity(ActivityJsonMapperSupport.decimal(payload, "remaining_size", "unfilled_size"))
                .price(ActivityJsonMapperSupport.decimal(payload, "price", "limit_price"))
                .averageFillPrice(ActivityJsonMapperSupport.decimal(payload, "average_filled_price", "price"))
                .fee(ActivityJsonMapperSupport.decimal(payload, "commission", "fee"))
                .feeCurrency(ActivityJsonMapperSupport.text(payload, "fee_currency", "commission_currency"))
                .eventTime(ActivityJsonMapperSupport.instant(payload, "trade_time", "created_time", "updated_time", "time"))
                .cursor(eventId)
                .rawJson(payload == null ? null : payload.toString())
                .terminalEvent(isTerminal(activityType))
                .errorEvent(activityType == BrokerActivityType.ORDER_REJECTED)
                .reason(ActivityJsonMapperSupport.text(payload, "reject_reason", "failure_reason", "message"))
                .metadataEntry("coinbase.product_id", productId)
                .build();
    }

    public static BrokerActivityType mapType(String nativeType, JsonNode payload) {
        String fillId = ActivityJsonMapperSupport.text(payload, "fill_id", "trade_id");
        if (fillId != null) {
            String remaining = ActivityJsonMapperSupport.text(payload, "remaining_size", "unfilled_size");
            return remaining != null && !"0".equals(remaining) ? BrokerActivityType.ORDER_PARTIALLY_FILLED : BrokerActivityType.ORDER_FILLED;
        }
        if (nativeType == null) {
            return BrokerActivityType.UNKNOWN;
        }
        return switch (nativeType.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN", "PENDING", "PENDING_OPEN", "NEW" -> BrokerActivityType.ORDER_CREATED;
            case "ACTIVE", "QUEUED" -> BrokerActivityType.ORDER_UPDATED;
            case "FILLED", "DONE" -> BrokerActivityType.ORDER_FILLED;
            case "PARTIALLY_FILLED" -> BrokerActivityType.ORDER_PARTIALLY_FILLED;
            case "CANCELLED", "CANCELED" -> BrokerActivityType.ORDER_CANCELLED;
            case "EXPIRED" -> BrokerActivityType.ORDER_EXPIRED;
            case "FAILED", "REJECTED" -> BrokerActivityType.ORDER_REJECTED;
            default -> BrokerActivityType.UNKNOWN;
        };
    }

    private static boolean isTerminal(BrokerActivityType type) {
        return type == BrokerActivityType.ORDER_FILLED
                || type == BrokerActivityType.ORDER_CANCELLED
                || type == BrokerActivityType.ORDER_REJECTED
                || type == BrokerActivityType.ORDER_EXPIRED;
    }
}
