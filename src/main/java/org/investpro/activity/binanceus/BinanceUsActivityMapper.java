package org.investpro.activity.binanceus;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.util.Locale;

public final class BinanceUsActivityMapper {
    public static final String EXCHANGE_ID = "BINANCEUS";

    private BinanceUsActivityMapper() {
    }

    public static BrokerActivityEvent mapExecutionReport(JsonNode payload) {
        String executionType = ActivityJsonMapperSupport.text(payload, "x", "executionType", "status");
        String orderStatus = ActivityJsonMapperSupport.text(payload, "X", "orderStatus");
        BrokerActivityType activityType = mapType(executionType, orderStatus);
        String eventId = ActivityJsonMapperSupport.text(payload, "t", "tradeId", "i", "orderId", "u", "updateId");
        String symbol = ActivityJsonMapperSupport.text(payload, "s", "symbol");

        return BrokerActivityEvent.builder()
                .eventId(eventId == null ? "binanceus-" + System.nanoTime() : eventId)
                .exchangeId(EXCHANGE_ID)
                .nativeEventType(executionType)
                .activityType(activityType)
                .orderId(ActivityJsonMapperSupport.text(payload, "i", "orderId"))
                .tradeId(ActivityJsonMapperSupport.text(payload, "t", "tradeId"))
                .tradePair(ActivityJsonMapperSupport.pair(symbol))
                .side(ActivityJsonMapperSupport.side(ActivityJsonMapperSupport.text(payload, "S", "side")))
                .requestedQuantity(ActivityJsonMapperSupport.decimal(payload, "q", "origQty"))
                .filledQuantity(ActivityJsonMapperSupport.decimal(payload, "z", "executedQty", "l", "lastExecutedQty"))
                .remainingQuantity(ActivityJsonMapperSupport.decimal(payload, "remainingQty"))
                .price(ActivityJsonMapperSupport.decimal(payload, "p", "price"))
                .averageFillPrice(ActivityJsonMapperSupport.decimal(payload, "L", "lastExecutedPrice"))
                .fee(ActivityJsonMapperSupport.decimal(payload, "n", "commission"))
                .feeCurrency(ActivityJsonMapperSupport.text(payload, "N", "commissionAsset"))
                .eventTime(ActivityJsonMapperSupport.instant(payload, "E", "eventTime", "T", "tradeTime"))
                .cursor(ActivityJsonMapperSupport.text(payload, "u", "updateId", "E", "eventTime"))
                .rawJson(payload == null ? null : payload.toString())
                .terminalEvent(isTerminal(activityType))
                .errorEvent(activityType == BrokerActivityType.ORDER_REJECTED)
                .reason(ActivityJsonMapperSupport.text(payload, "r", "rejectReason"))
                .metadataEntry("binance.executionType", executionType)
                .metadataEntry("binance.orderStatus", orderStatus)
                .build();
    }

    public static BrokerActivityType mapType(String executionType, String orderStatus) {
        String execution = executionType == null ? "" : executionType.trim().toUpperCase(Locale.ROOT);
        String status = orderStatus == null ? "" : orderStatus.trim().toUpperCase(Locale.ROOT);

        if ("TRADE".equals(execution)) {
            return "PARTIALLY_FILLED".equals(status) ? BrokerActivityType.ORDER_PARTIALLY_FILLED : BrokerActivityType.ORDER_FILLED;
        }
        return switch (!execution.isBlank() ? execution : status) {
            case "NEW" -> BrokerActivityType.ORDER_CREATED;
            case "CANCELED", "CANCELLED" -> BrokerActivityType.ORDER_CANCELLED;
            case "REJECTED" -> BrokerActivityType.ORDER_REJECTED;
            case "EXPIRED" -> BrokerActivityType.ORDER_EXPIRED;
            case "REPLACED" -> BrokerActivityType.ORDER_UPDATED;
            case "PARTIALLY_FILLED" -> BrokerActivityType.ORDER_PARTIALLY_FILLED;
            case "FILLED" -> BrokerActivityType.ORDER_FILLED;
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
