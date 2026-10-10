package org.investpro.core.state;

import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SystemStateStoreTest {
    @Test void snapshotsAreExchangeScopedImmutableAndIgnoreOlderQuotes() throws Exception {
        var state = new SystemStateStore();
        var pair = new TradePair("BTC", "USD");
        var ticker = new Ticker(100, 99, 101, 90, 110, 80, 12, 200);
        state.updateTicker("first", pair, ticker);
        state.updateTicker("second", pair, new Ticker(200, 199, 201, 180, 220, 160, 24, 200));
        ticker.setLastPrice(999);
        state.updateTicker("first", pair, new Ticker(50, 49, 51, 45, 55, 40, 6, 100));
        assertEquals(100, state.ticker("first", pair).orElseThrow().last());
        assertEquals(200, state.ticker("second", pair).orElseThrow().last());
        assertThrows(UnsupportedOperationException.class, () -> state.tickers().clear());
        var display = state.ticker("first", pair).orElseThrow().ticker();
        display.setLastPrice(1);
        assertEquals(100, state.ticker("first", pair).orElseThrow().last());
        state.clearExchange("first");
        assertTrue(state.ticker("first", pair).isEmpty());
        assertTrue(state.ticker("second", pair).isPresent());
    }

    @Test void emptySnapshotsClearPositionsAndOrders() {
        var state = new SystemStateStore();
        state.updatePositions("venue", List.of());
        state.updateOrders("venue", List.of());
        assertTrue(state.positions("venue").isEmpty());
        assertTrue(state.orders("venue").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> state.positions("venue").clear());
    }
}
