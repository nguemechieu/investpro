package org.investpro.exchange;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalPaperExecutionTest {
    private TradePair pair() {
        TradePair pair = mock(TradePair.class);
        when(pair.toString('/')).thenReturn("BTC/USD");
        when(pair.getLastPrice()).thenReturn(100.0);
        return pair;
    }

    @Test
    void marketTradesUpdateOnlyLocalBalancesAndHistory() {
        LocalPaperExecution paper = new LocalPaperExecution();
        TradePair pair = pair();
        String buy = paper.provider().createMarketOrder(pair, Side.BUY, 2).join();
        assertTrue(buy.startsWith("paper-"));
        assertEquals(9800, paper.account("test").getAvailableBalance());
        assertEquals("FILLED", paper.provider().fetchOrder(buy).join().orElseThrow().getStatus());
        paper.provider().placeMarketOrder(pair, Side.SELL, 1).join();
        assertEquals(9900, paper.account("test").getAvailableBalance());
        assertEquals(2, paper.provider().fetchOrderHistory(null, null).join().size());
    }

    @Test
    void pendingOrdersCancelLocally() {
        LocalPaperExecution paper = new LocalPaperExecution();
        String id = paper.provider().createLimitOrder(pair(), Side.BUY, 1, 90).join();
        assertEquals(1, paper.provider().fetchAllOpenOrders().join().size());
        assertEquals(10000, paper.account("test").getAvailableBalance());
        paper.provider().cancelOrder(id).join();
        assertTrue(paper.provider().fetchAllOpenOrders().join().isEmpty());
        assertEquals("CANCELLED", paper.provider().fetchOrder(id).join().orElseThrow().getStatus());
    }

    @Test
    void rejectsUnfundedTradesWithoutChangingBalance() {
        LocalPaperExecution paper = new LocalPaperExecution();
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> paper.provider().createMarketOrder(pair(), Side.BUY, 1000).join());
        assertEquals(10000, paper.account("test").getAvailableBalance());
    }
}
