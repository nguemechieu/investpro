package org.investpro.ui.market;

import org.investpro.exchange.Exchange;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.market.MarketInstrumentService;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectedMarketWatchModelTest {
    @Test
    void catalogRequestsDoNotRunOnTheCallingUiThread() {
        var service = mock(MarketInstrumentService.class);
        var exchange = mock(Exchange.class);
        Thread caller = Thread.currentThread();
        var loader = new java.util.concurrent.atomic.AtomicReference<Thread>();
        when(service.loadForExchange(exchange)).thenAnswer(_ -> {
            loader.set(Thread.currentThread());
            return CompletableFuture.completedFuture(List.of());
        });
        new ConnectedMarketWatchModel(service).load(Map.of("Coinbase", exchange)).join();
        assertNotSame(caller, loader.get());
    }

    @Test
    void retainsIdenticalSymbolsFromSeparateExchangesAndTheirChartSource() throws Exception {
        var service = mock(MarketInstrumentService.class);
        var coinbase = mock(Exchange.class);
        var oanda = mock(Exchange.class);
        var first = mock(MarketInstrument.class);
        var second = mock(MarketInstrument.class);
        when(first.tradePair()).thenReturn(new TradePair("EUR", "USD"));
        when(second.tradePair()).thenReturn(new TradePair("EUR", "USD"));
        when(first.nativeSymbol()).thenReturn("EUR-USD");
        when(second.nativeSymbol()).thenReturn("EUR_USD");
        when(service.loadForExchange(coinbase)).thenReturn(CompletableFuture.completedFuture(List.of(first)));
        when(service.loadForExchange(oanda)).thenReturn(CompletableFuture.completedFuture(List.of(second)));
        var model = new ConnectedMarketWatchModel(service);
        var result = model.load(Map.of("Coinbase", coinbase, "OANDA", oanda)).join();
        assertEquals(2, result.rows().size());
        assertSame(coinbase, result.rows().getFirst().exchange());
        assertSame(oanda, result.rows().getLast().exchange());
        assertTrue(result.failedExchanges().isEmpty());
        assertTrue(model.load(Map.of()).join().rows().isEmpty());
    }

    @Test
    void venueFailureDoesNotHideSuccessfulVenueAndReconnectInvalidatesCatalog() {
        var service = mock(MarketInstrumentService.class);
        var first = mock(Exchange.class);
        var replacement = mock(Exchange.class);
        when(first.getExchangeId()).thenReturn("coinbase");
        when(replacement.getExchangeId()).thenReturn("coinbase");
        when(service.loadForExchange(first)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        when(service.loadForExchange(replacement)).thenReturn(CompletableFuture.completedFuture(List.of()));
        var model = new ConnectedMarketWatchModel(service);
        assertEquals(Set.of("Coinbase"), model.load(Map.of("Coinbase", first)).join().failedExchanges());
        model.load(Map.of("Coinbase", replacement)).join();
        verify(service, times(2)).invalidateExchange("coinbase");
    }
}
