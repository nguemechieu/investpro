package org.investpro.exchange;

import org.investpro.exchange.binance.Binance;
import org.investpro.exchange.bitfinex.Bitfinex;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnsupportedOrderRoutingTest {
    @Test void unsupportedTypesNeverBecomeMarketOrders() throws Exception {
        for (Exchange exchange : new Exchange[]{mock(Binance.class, CALLS_REAL_METHODS), mock(Bitfinex.class, CALLS_REAL_METHODS)}) {
            Order order = new Order(); order.setSymbol("BTC/USD"); order.setType("STOP_LIMIT");
            order.setSide(Side.BUY); order.setQuantity(1); order.setPrice(100);
            var error = assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createOrder(order).join());
            assertInstanceOf(UnsupportedOperationException.class, error.getCause());
            verify(exchange, never()).createMarketOrder(any(), any(), anyDouble());
            verify(exchange, never()).createLimitOrder(any(), any(), anyDouble(), anyDouble());
        }
    }
}
