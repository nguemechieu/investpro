package org.investpro.ui;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradingDeskTransactionsTest {
    @Test void capturedExchangeMergesAndSortsFillsWithoutDuplicateRows() throws Exception {
        Exchange captured = mock(Exchange.class);
        TradePair pair = new TradePair("EUR", "USD");
        Trade older = new Trade(pair, 1.1, 10, Side.BUY, 1, Instant.ofEpochSecond(100));
        Trade newer = new Trade(pair, 1.2, 10, Side.SELL, 2, Instant.ofEpochSecond(200));
        when(captured.fetchAccountTrades(pair)).thenReturn(CompletableFuture.completedFuture(List.of(older, newer, older)));
        assertEquals(List.of(newer, older), TradingDesk.fetchAccountTransactions(captured, List.of(pair)));
        verify(captured, never()).getTradePairSymbol();
    }
    @Test void noVisiblePairsUsesCapturedVenuesSymbols() throws Exception {
        Exchange captured = mock(Exchange.class);
        TradePair pair = new TradePair("EUR", "USD");
        TradePair sameSymbolWithDifferentQuote = new TradePair("EUR", "USD");
        sameSymbolWithDifferentQuote.setLast(1.2);
        when(captured.getTradePairSymbol()).thenReturn(List.of(pair, sameSymbolWithDifferentQuote));
        when(captured.fetchAccountTrades(pair)).thenReturn(CompletableFuture.completedFuture(List.of()));
        assertTrue(TradingDesk.fetchAccountTransactions(captured, List.of()).isEmpty());
        verify(captured, times(1)).fetchAccountTrades(pair);
        verify(captured, never()).fetchAccountTrades(sameSymbolWithDifferentQuote);
    }
    @Test void partialFailureStillReturnsAvailableFills() throws Exception {
        Exchange captured = mock(Exchange.class);
        TradePair unavailable = new TradePair("GBP", "USD");
        TradePair available = new TradePair("EUR", "USD");
        Trade fill = new Trade(available, 1.1, 10, Side.BUY, 1, Instant.now());
        when(captured.fetchAccountTrades(unavailable)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Unavailable")));
        when(captured.fetchAccountTrades(available)).thenReturn(CompletableFuture.completedFuture(List.of(fill)));
        assertEquals(List.of(fill), TradingDesk.fetchAccountTransactions(captured, List.of(unavailable, available)));
    }
    @Test void interruptedRefreshStopsAndPreservesInterruptStatus() throws Exception {
        Exchange captured = mock(Exchange.class);
        TradePair pair = new TradePair("EUR", "USD");
        when(captured.fetchAccountTrades(pair)).thenReturn(new CompletableFuture<>());
        try {
            Thread.currentThread().interrupt();
            assertThrows(IllegalStateException.class, () -> TradingDesk.fetchAccountTransactions(captured, List.of(pair)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
