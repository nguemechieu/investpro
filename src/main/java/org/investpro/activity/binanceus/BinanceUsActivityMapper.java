package org.investpro.activity.binanceus;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.activity.ActivityJsonMapperSupport;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.time.Instant;

public final class BinanceUsActivityMapper {
    public static final String EXCHANGE_ID = "BINANCEUS";

    private BinanceUsActivityMapper() {
    }

    public static BrokerActivityEvent mapTrade(JsonNode trade) {
        String symbol = ActivityJsonMapperSupport.text(trade, "symbol");
        String tradeId = ActivityJsonMapperSupport.text(trade, "id");
        String orderId = ActivityJsonMapperSupport.text(trade, "orderId");
        String sideStr = ActivityJsonMapperSupport.text(trade, "isBuyer");
        String eventId = tradeId != null ? tradeId : ("bnus-trade-" + Instant.now().toEpochMilli());
        boolean isBuyer = "true".equalsIgnoreCase(sideStr);

        return BrokerActivityEvent.builder()
                .exchangeId(EXCHANGE_ID)
                .eventId(eventId)
                .nativeEventType("TRADE")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .orderId(orderId)
                .tradeId(tradeId)
                .tradePair(ActivityJsonMapperSupport.pair(symbol))
                .side(isBuyer ? org.investpro.utils.Side.BUY : org.investpro.utils.Side.SELL)
                .filledQuantity(ActivityJsonMapperSupport.decimal(trade, "qty"))
                .price(ActivityJsonMapperSupport.decimal(trade, "price"))
                .fee(ActivityJsonMapperSupport.decimal(trade, "commission"))
                .feeCurrency(ActivityJsonMapperSupport.text(trade, "commissionAsset"))
                .realizedPnl(ActivityJsonMapperSupport.decimal(trade, "realizedPnl"))
                .eventTime(ActivityJsonMapperSupport.instant(trade, "time"))
                .source(EXCHANGE_ID)
                .cursor(tradeId)
                .rawJson(trade.toString())
                .terminalEvent(true)
                .build();
    }
}
