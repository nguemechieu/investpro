package org.investpro.exchange;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalPaperExecutionTest {
    @Test
    void protectedMarketEntryClosesAtObservedStopAndMarksEquity() {
        LocalPaperExecution paper = new LocalPaperExecution();
        TradePair pair = pair();
        String entry = paper.provider().createBracketOrder(pair, Side.BUY, 2, 0, 90, 120).join();
        assertEquals("FILLED", paper.provider().fetchOrder(entry).join().orElseThrow().getStatus());
        assertEquals(90, paper.positions().getFirst().getStopLoss());
        assertEquals(10000, paper.account("test").getEquity());
        paper.updateMarketPrice(pair, 110);
        assertEquals(10020, paper.account("test").getEquity());
        paper.updateMarketPrice(pair, 89);
        assertTrue(paper.positions().isEmpty());
        assertEquals(9978, paper.account("test").getAvailableBalance());
    }

    @Test
    void protectedLimitEntryWaitsThenFillsAndTakesProfitLocally() {
        LocalPaperExecution paper = new LocalPaperExecution();
        TradePair pair = pair();
        String entry = paper.provider().createBracketOrder(pair, Side.BUY, 1, 95, 90, 120).join();
        assertTrue(paper.positions().isEmpty());
        paper.updateMarketPrice(pair, 94);
        assertEquals("FILLED", paper.provider().fetchOrder(entry).join().orElseThrow().getStatus());
        assertEquals(94, paper.positions().getFirst().getEntryPrice());
        paper.updateMarketPrice(pair, 121);
        assertTrue(paper.positions().isEmpty());
        assertEquals(10027, paper.account("test").getAvailableBalance());
    }
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
