package org.investpro.activity.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.util.Locale;

public final class OandaActivityMapper {
    public static final String EXCHANGE_ID = "OANDA";

    private OandaActivityMapper() {
    }

    public static BrokerActivityEvent mapTransaction(JsonNode transaction) {
        String type = ActivityJsonMapperSupport.text(transaction, "type");
        String id = ActivityJsonMapperSupport.text(transaction, "id", "transactionID");
        BrokerActivityType activityType = mapType(type);
        String instrument = ActivityJsonMapperSupport.text(transaction, "instrument");
        JsonNode tradeOpened = transaction == null ? null : transaction.get("tradeOpened");
        JsonNode tradeReduced = transaction == null ? null : transaction.get("tradeReduced");
        JsonNode tradesClosed = transaction == null ? null : transaction.get("tradesClosed");
        String tradeId = ActivityJsonMapperSupport.text(tradeOpened, "tradeID");
        if (tradeId == null) {
            tradeId = ActivityJsonMapperSupport.text(tradeReduced, "tradeID");
        }
        if (tradeId == null && tradesClosed != null && tradesClosed.isArray() && !tradesClosed.isEmpty()) {
            tradeId = ActivityJsonMapperSupport.text(tradesClosed.get(0), "tradeID");
        }

        return BrokerActivityEvent.builder()
                .eventId(id == null ? "oanda-" + System.nanoTime() : id)
                .exchangeId(EXCHANGE_ID)
                .accountId(ActivityJsonMapperSupport.text(transaction, "accountID", "accountId"))
                .nativeEventType(type)
                .activityType(activityType)
                .orderId(ActivityJsonMapperSupport.text(transaction, "orderID", "orderId"))
                .tradeId(tradeId)
                .tradePair(ActivityJsonMapperSupport.pair(instrument))
                .side(ActivityJsonMapperSupport.side(ActivityJsonMapperSupport.text(transaction, "reason")))
                .requestedQuantity(ActivityJsonMapperSupport.decimal(transaction, "units"))
                .filledQuantity(ActivityJsonMapperSupport.decimal(transaction, "units"))
                .price(ActivityJsonMapperSupport.decimal(transaction, "price"))
                .averageFillPrice(ActivityJsonMapperSupport.decimal(transaction, "price"))
                .realizedPnl(ActivityJsonMapperSupport.decimal(transaction, "pl"))
                .fee(ActivityJsonMapperSupport.decimal(transaction, "commission"))
                .commission(ActivityJsonMapperSupport.decimal(transaction, "commission"))
                .financing(ActivityJsonMapperSupport.decimal(transaction, "financing"))
                .balanceAfter(ActivityJsonMapperSupport.decimal(transaction, "accountBalance"))
                .balanceCurrency(ActivityJsonMapperSupport.text(transaction, "homeCurrency", "currency"))
                .eventTime(ActivityJsonMapperSupport.instant(transaction, "time"))
                .cursor(id)
                .rawJson(transaction == null ? null : transaction.toString())
                .terminalEvent(isTerminal(activityType))
                .errorEvent(activityType == BrokerActivityType.ORDER_REJECTED)
                .reason(ActivityJsonMapperSupport.text(transaction, "reason", "rejectReason"))
                .metadataEntry("oanda.type", type)
                .build();
    }

    public static BrokerActivityType mapType(String nativeType) {
        if (nativeType == null) {
            return BrokerActivityType.UNKNOWN;
        }
        return switch (nativeType.trim().toUpperCase(Locale.ROOT)) {
            case "MARKET_ORDER", "LIMIT_ORDER", "STOP_ORDER", "MARKET_IF_TOUCHED_ORDER", "TAKE_PROFIT_ORDER",
                    "STOP_LOSS_ORDER", "TRAILING_STOP_LOSS_ORDER" -> BrokerActivityType.ORDER_CREATED;
            case "ORDER_FILL" -> BrokerActivityType.ORDER_FILLED;
            case "ORDER_CANCEL" -> BrokerActivityType.ORDER_CANCELLED;
            case "ORDER_CANCEL_REJECT", "ORDER_CLIENT_EXTENSIONS_MODIFY_REJECT",
                    "TRADE_CLIENT_EXTENSIONS_MODIFY_REJECT" -> BrokerActivityType.ORDER_REJECTED;
            case "ORDER_CLIENT_EXTENSIONS_MODIFY", "TRADE_CLIENT_EXTENSIONS_MODIFY" -> BrokerActivityType.ORDER_UPDATED;
            case "DAILY_FINANCING" -> BrokerActivityType.FINANCING_CHARGED;
            case "TRANSFER_FUNDS" -> BrokerActivityType.BALANCE_CHANGED;
            case "MARGIN_CALL_ENTER", "MARGIN_CALL_EXTEND" -> BrokerActivityType.MARGIN_CALL;
            case "MARGIN_CLOSEOUT", "DELAYED_TRADE_CLOSURE" -> BrokerActivityType.MARGIN_CLOSEOUT;
            default -> BrokerActivityType.UNKNOWN;
        };
    }

    private static boolean isTerminal(BrokerActivityType type) {
        return type == BrokerActivityType.ORDER_FILLED
                || type == BrokerActivityType.ORDER_CANCELLED
                || type == BrokerActivityType.ORDER_REJECTED
                || type == BrokerActivityType.ORDER_EXPIRED
                || type == BrokerActivityType.MARGIN_CLOSEOUT;
    }
}
