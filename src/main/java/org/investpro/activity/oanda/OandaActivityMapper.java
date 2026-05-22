package org.investpro.activity.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.time.Instant;

public final class OandaActivityMapper {
    public static final String EXCHANGE_ID = "OANDA";

    private OandaActivityMapper() {
    }

    public static BrokerActivityEvent mapTransaction(JsonNode tx) {
        String nativeType = ActivityJsonMapperSupport.text(tx, "type");
        String eventId = ActivityJsonMapperSupport.text(tx, "id");
        String accountId = ActivityJsonMapperSupport.text(tx, "accountID");
        String instrument = ActivityJsonMapperSupport.text(tx, "instrument");
        String orderId = ActivityJsonMapperSupport.text(tx, "orderID", "id");
        String tradeId = ActivityJsonMapperSupport.text(tx, "tradeID", "tradeOpened");

        BrokerActivityType activityType = mapType(nativeType);

        if (eventId == null || eventId.isBlank()) {
            eventId = "oanda-" + Instant.now().toEpochMilli();
        }

        BrokerActivityEvent.BrokerActivityEventBuilder builder = BrokerActivityEvent.builder()
                .exchangeId(EXCHANGE_ID)
                .eventId(eventId)
                .accountId(accountId)
                .nativeEventType(nativeType)
                .activityType(activityType)
                .orderId(orderId)
                .tradeId(tradeId)
                .tradePair(ActivityJsonMapperSupport.pair(instrument != null ? instrument.replace("_", "") : null))
                .price(ActivityJsonMapperSupport.decimal(tx, "price", "tradePrice"))
                .requestedQuantity(ActivityJsonMapperSupport.decimal(tx, "units"))
                .filledQuantity(ActivityJsonMapperSupport.decimal(tx, "units"))
                .realizedPnl(ActivityJsonMapperSupport.decimal(tx, "pl"))
                .financing(ActivityJsonMapperSupport.decimal(tx, "financing"))
                .fee(ActivityJsonMapperSupport.decimal(tx, "commission"))
                .balanceAfter(ActivityJsonMapperSupport.decimal(tx, "accountBalance"))
                .eventTime(ActivityJsonMapperSupport.instant(tx, "time"))
                .source(EXCHANGE_ID)
                .cursor(eventId)
                .rawJson(tx.toString())
                .terminalEvent(isTerminal(activityType))
                .errorEvent(activityType == BrokerActivityType.ORDER_REJECTED ||
                        activityType == BrokerActivityType.MARGIN_CLOSEOUT ||
                        activityType == BrokerActivityType.LIQUIDATION);

        return builder.build();
    }

    private static BrokerActivityType mapType(String nativeType) {
        if (nativeType == null) return BrokerActivityType.UNKNOWN;
        return switch (nativeType.toUpperCase()) {
            case "ORDER_FILL" -> BrokerActivityType.ORDER_FILLED;
            case "LIMIT_ORDER", "MARKET_ORDER", "STOP_ORDER", "MARKET_IF_TOUCHED_ORDER",
                 "FIXED_PRICE_ORDER" -> BrokerActivityType.ORDER_CREATED;
            case "MARKET_ORDER_REJECT", "LIMIT_ORDER_REJECT", "STOP_ORDER_REJECT",
                 "MARKET_IF_TOUCHED_ORDER_REJECT", "FIXED_PRICE_ORDER_REJECT" -> BrokerActivityType.ORDER_REJECTED;
            case "ORDER_CANCEL" -> BrokerActivityType.ORDER_CANCELLED;
            case "ORDER_CLIENT_EXTENSIONS_MODIFY" -> BrokerActivityType.ORDER_UPDATED;
            case "TRADE_OPEN" -> BrokerActivityType.TRADE_OPENED;
            case "TRADE_UPDATE" -> BrokerActivityType.TRADE_UPDATED;
            case "TRADE_CLOSE" -> BrokerActivityType.TRADE_CLOSED;
            case "POSITION_OPEN" -> BrokerActivityType.POSITION_OPENED;
            case "POSITION_CLOSE" -> BrokerActivityType.POSITION_CLOSED;
            case "MARGIN_CALL_ENTER", "MARGIN_CALL_EXTEND" -> BrokerActivityType.MARGIN_CALL;
            case "MARGIN_CLOSEOUT" -> BrokerActivityType.MARGIN_CLOSEOUT;
            case "DAILY_FINANCING" -> BrokerActivityType.FINANCING_CHARGED;
            case "TRANSFER_FUNDS" -> BrokerActivityType.DEPOSIT;
            case "TAKE_PROFIT_ORDER", "TAKE_PROFIT_ORDER_REJECT" -> BrokerActivityType.ORDER_CREATED;
            case "STOP_LOSS_ORDER", "STOP_LOSS_ORDER_REJECT" -> BrokerActivityType.ORDER_CREATED;
            case "TRAILING_STOP_LOSS_ORDER", "TRAILING_STOP_LOSS_ORDER_REJECT" -> BrokerActivityType.ORDER_CREATED;
            case "STOP_LOSS_ORDER_TRIGGERED" -> BrokerActivityType.STOP_LOSS_TRIGGERED;
            case "TAKE_PROFIT_ORDER_TRIGGERED" -> BrokerActivityType.TAKE_PROFIT_TRIGGERED;
            case "TRAILING_STOP_LOSS_ORDER_TRIGGERED" -> BrokerActivityType.TRAILING_STOP_TRIGGERED;
            case "CLIENT_CONFIGURE" -> BrokerActivityType.ACCOUNT_CONFIG_CHANGED;
            case "CLIENT_CONFIGURE_REJECT" -> BrokerActivityType.ACCOUNT_CONFIG_CHANGED;
            case "CREATE", "REOPEN" -> BrokerActivityType.ACCOUNT_CONFIG_CHANGED;
            default -> BrokerActivityType.UNKNOWN;
        };
    }

    private static boolean isTerminal(BrokerActivityType type) {
        return type == BrokerActivityType.ORDER_FILLED ||
                type == BrokerActivityType.ORDER_CANCELLED ||
                type == BrokerActivityType.ORDER_REJECTED ||
                type == BrokerActivityType.ORDER_EXPIRED ||
                type == BrokerActivityType.TRADE_CLOSED ||
                type == BrokerActivityType.POSITION_CLOSED;
    }
}
