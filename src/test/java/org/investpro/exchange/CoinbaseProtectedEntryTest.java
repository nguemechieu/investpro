package org.investpro.exchange;

import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinbaseProtectedEntryTest {
    @Test void protectionIsNeverDroppedToSubmitOrdinaryEntry() {
        Coinbase exchange = mock(Coinbase.class, CALLS_REAL_METHODS);
        TradePair pair = mock(TradePair.class);
        assertThrows(CompletionException.class,
                () -> exchange.createBracketOrder(pair, Side.BUY, 1, 0, 90, 120).join());
        verify(exchange, never()).createMarketOrder(any(), any(), anyDouble());
        verify(exchange, never()).createLimitOrder(any(), any(), anyDouble(), anyDouble());
    }

    @Test void explicitEntryWithoutProtectionRetainsNormalMarketBehavior() {
        Coinbase exchange = mock(Coinbase.class, CALLS_REAL_METHODS);
        TradePair pair = mock(TradePair.class);
        doReturn(CompletableFuture.completedFuture("entry-id")).when(exchange).createMarketOrder(pair, Side.BUY, 1);
        assertEquals("entry-id", exchange.createBracketOrder(pair, Side.BUY, 1, 0, 0, 0).join());
    }
}
