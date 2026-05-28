package org.investpro.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.activity.binanceus.BinanceUsActivityMapper;
import org.investpro.activity.coinbase.CoinbaseActivityMapper;
import org.investpro.activity.oanda.OandaActivityMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeActivityMapperTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mapsOandaOrderFillIntoUniversalActivity() throws Exception {
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "id": "42",
                  "accountID": "acct",
                  "type": "ORDER_FILL",
                  "orderID": "order-1",
                  "instrument": "EUR_USD",
                  "units": "100",
                  "price": "1.08123",
                  "pl": "4.25",
                  "commission": "0.10",
                  "financing": "-0.02",
                  "time": "2026-05-22T12:00:00Z"
                }
                """);

        BrokerActivityEvent event = OandaActivityMapper.mapTransaction(payload);

        assertEquals(BrokerActivityType.ORDER_FILLED, event.getActivityType());
        assertEquals("42", event.getCursor());
        assertEquals(new BigDecimal("4.25"), event.getRealizedPnl());
        assertTrue(event.isTerminalEvent());
    }

    @Test
    void mapsCoinbaseFillIntoUniversalActivity() throws Exception {
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "fill_id": "fill-7",
                  "order_id": "order-7",
                  "product_id": "BTC-USD",
                  "side": "BUY",
                  "size": "0.01",
                  "price": "65000.12",
                  "commission": "1.25",
                  "trade_time": "2026-05-22T12:01:00Z"
                }
                """);

        BrokerActivityEvent event = CoinbaseActivityMapper.mapOrderOrFill(payload);

        assertEquals(BrokerActivityType.ORDER_FILLED, event.getActivityType());
        assertEquals("fill-7", event.getTradeId());
        assertEquals(new BigDecimal("1.25"), event.getFee());
    }

    @Test
    void mapsBinanceExecutionReportIntoUniversalActivity() throws Exception {
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "e": "executionReport",
                  "E": 1770000000000,
                  "s": "ETHUSDT",
                  "S": "SELL",
                  "x": "TRADE",
                  "X": "PARTIALLY_FILLED",
                  "i": 1001,
                  "t": 2002,
                  "q": "1.5",
                  "z": "0.5",
                  "L": "3100.00",
                  "n": "0.15",
                  "N": "USDT"
                }
                """);

        BrokerActivityEvent event = BinanceUsActivityMapper.mapExecutionReport(payload);

        assertEquals(BrokerActivityType.ORDER_PARTIALLY_FILLED, event.getActivityType());
        assertEquals("1001", event.getOrderId());
        assertEquals("2002", event.getTradeId());
        assertEquals(new BigDecimal("0.15"), event.getFee());
    }
}
