package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.data.CandleData;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IbkrMarketDataProviderTest {
    IbkrMarketDataProviderTest() throws Exception { }
    private final IbkrConnectionManager manager = mock(IbkrConnectionManager.class);
    private final IbkrClientPortalClient portal = mock(IbkrClientPortalClient.class);
    private final IbkrContractResolver resolver = mock(IbkrContractResolver.class);
    private final IbkrTwsSession session = mock(IbkrTwsSession.class);
    private final IbkrResolvedContract contract = mock(IbkrResolvedContract.class);
    private final TradePair pair = new TradePair("EUR", "USD");
    private IbkrMarketDataProvider provider(boolean simulation, IbkrConnectionMode transport) {
        when(manager.getMode()).thenReturn(IbkrConnectionManager.Mode.LIVE);
        when(manager.getConnectionMode()).thenReturn(transport);
        when(manager.getTwsSession()).thenReturn(session);
        when(resolver.requireResolved(pair)).thenReturn(contract);
        when(contract.conId()).thenReturn(123L);
        return new IbkrMarketDataProvider(manager, portal, resolver, simulation);
    }
    private Ticker quote() { return new Ticker(1.1, 1.09, 1.11, 0, System.currentTimeMillis()); }
    @Test void twsFailureNeverFallsBackEvenWithSimulationEnabled() {
        var provider = provider(true, IbkrConnectionMode.TWS_API);
        when(session.ticker(contract)).thenReturn(CompletableFuture.failedFuture(new IbkrMarketDataException(354, "Not subscribed")));
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verify(manager).markMarketDataAvailable(false);
        verify(manager, never()).markMarketDataAvailable(true);
    }
    @Test void portalMissingQuoteFailsAndUsesDedicatedWorker() {
        var provider = provider(false, IbkrConnectionMode.CLIENT_PORTAL_GATEWAY);
        when(portal.fetchTicker(contract)).thenAnswer(_ -> {
            assertEquals("ibkr-market-data", Thread.currentThread().getName());
            return Optional.empty();
        });
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verify(manager).markMarketDataAvailable(false);
    }
    @Test void paperRequiresRealDataByDefault() {
        var provider = provider(false, IbkrConnectionMode.CLIENT_PORTAL_GATEWAY);
        when(manager.getMode()).thenReturn(IbkrConnectionManager.Mode.PAPER);
        when(portal.fetchTicker(contract)).thenReturn(Optional.empty());
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
    }
    @Test void explicitSimulationProducesDataWithoutBrokerHealth() throws Exception {
        var provider = provider(true, IbkrConnectionMode.TWS_API);
        when(manager.getMode()).thenReturn(IbkrConnectionManager.Mode.PAPER);
        assertEquals(Ticker.QuoteType.SIMULATED, provider.fetchTicker(pair).join().getQuoteType());
        assertEquals(300, provider.candleDataSupplier(60, pair).get().get().size());
        assertTrue(provider.fetchOrderBook(pair).join().getSequence().contains("SIMULATED"));
        assertFalse(provider.fetchRecentTradesUntil(pair, Instant.now().minusSeconds(60)).join().isEmpty());
        verify(manager, never()).markMarketDataAvailable(true);
        verifyNoInteractions(session, portal);
    }
    @Test void unsupportedLiveDataNeverFabricatesValues() throws Exception {
        var provider = provider(false, IbkrConnectionMode.CLIENT_PORTAL_GATEWAY);
        assertThrows(Exception.class, () -> provider.candleDataSupplier(60, pair).get().get());
        assertThrows(CompletionException.class, () -> provider.fetchOrderBook(pair).join());
        assertThrows(CompletionException.class, () -> provider.fetchRecentTradesUntil(pair, Instant.now()).join());
        verify(manager, times(3)).markMarketDataAvailable(false);
        verifyNoInteractions(portal);
    }
    @Test void unresolvedContractFailsClearly() {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        when(resolver.requireResolved(pair)).thenThrow(new IllegalStateException("Missing"));
        var error = assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        assertInstanceOf(IbkrMarketDataException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("unresolved"));
        verifyNoInteractions(session);
    }
    @Test void crossedQuoteFailsValidation() {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        when(session.ticker(contract)).thenReturn(CompletableFuture.completedFuture(new Ticker(100, 101, 99, 0, System.currentTimeMillis())));
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verify(manager).markMarketDataAvailable(false);
    }
    @Test void validQuoteIsCopiedAndDelayedStatusPreserved() {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        Ticker quote = quote(); quote.setQuoteType(Ticker.QuoteType.DELAYED);
        when(session.ticker(contract)).thenReturn(CompletableFuture.completedFuture(quote));
        Ticker result = provider.fetchTicker(pair).join();
        assertNotSame(quote, result);
        assertEquals(Ticker.QuoteType.DELAYED, result.getQuoteType());
        verify(manager).markMarketDataAvailable(true);
    }
    @Test void twsFuturesAreComposedWithoutBlockingCaller() throws Exception {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        CompletableFuture<Ticker> pending = new CompletableFuture<>();
        when(session.ticker(contract)).thenReturn(pending);
        var future = provider.fetchTickers(List.of(pair, pair));
        assertFalse(future.isDone());
        pending.complete(quote());
        assertEquals(2, future.get().size());
    }
    @Test void historyIsRealAndInProgressUsesActualBar() throws Exception {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        Instant started = Instant.now().minusSeconds(30);
        CandleData bar = new CandleData(1.1, 1.11, 1.12, 1.09, (int)started.getEpochSecond(), 12);
        when(session.history(contract, 60)).thenReturn(CompletableFuture.completedFuture(List.of(bar)));
        assertEquals(List.of(bar), provider.candleDataSupplier(60, pair).get().get());
        var current = provider.fetchCandleDataForInProgressCandle(pair, started, 60).join().orElseThrow();
        assertEquals(1.12, current.highPriceSoFar());
        assertEquals(12, current.volumeSoFar());
        verifyNoInteractions(portal);
    }
    @Test void executorIsBounded() throws Exception {
        var field = IbkrMarketDataProvider.class.getDeclaredField("WORKERS");
        field.setAccessible(true);
        var executor = (ThreadPoolExecutor) field.get(null);
        assertEquals(4, executor.getMaximumPoolSize());
        assertEquals(64, executor.getQueue().size() + executor.getQueue().remainingCapacity());
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, executor.getRejectedExecutionHandler());
    }
    @Test void concurrentSimulationRequestsAreSafe() {
        var provider = provider(true, IbkrConnectionMode.TWS_API);
        when(manager.getMode()).thenReturn(IbkrConnectionManager.Mode.PAPER);
        var futures = java.util.stream.IntStream.range(0, 32).mapToObj(_ -> provider.fetchTicker(pair)).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        assertTrue(futures.stream().allMatch(f -> f.join().getLastPrice() > 0));
        verify(manager, never()).markMarketDataAvailable(true);
    }
    @Test void emptyTwsQuoteFailsRatherThanReturningEmptyTicker() {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        when(session.ticker(contract)).thenReturn(CompletableFuture.completedFuture(Ticker.empty()));
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verify(manager).markMarketDataAvailable(false);
    }
    @Test void inFlightSimulationCannotEscapeAfterSwitchToLive() {
        var provider = provider(true, IbkrConnectionMode.TWS_API);
        when(manager.getMode()).thenReturn(IbkrConnectionManager.Mode.PAPER, IbkrConnectionManager.Mode.LIVE);
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verify(manager).markMarketDataAvailable(false);
    }
    @Test void concurrentPortalCallsUseBoundedWorkersAndCopySnapshots() {
        var provider = provider(false, IbkrConnectionMode.CLIENT_PORTAL_GATEWAY);
        Ticker shared = quote();
        when(portal.fetchTicker(contract)).thenAnswer(_ -> {
            assertEquals("ibkr-market-data", Thread.currentThread().getName());
            return Optional.of(shared);
        });
        var futures = java.util.stream.IntStream.range(0, 32).mapToObj(_ -> provider.fetchTicker(pair)).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        assertTrue(futures.stream().allMatch(f -> f.join() != shared));
        verify(manager, times(32)).markMarketDataAvailable(true);
    }
    @Test void cachedSyntheticContractIsNotARealBrokerContract() {
        var provider = provider(false, IbkrConnectionMode.TWS_API);
        when(contract.metadataJson()).thenReturn("{\"syntheticCashContract\":true}");
        assertThrows(CompletionException.class, () -> provider.fetchTicker(pair).join());
        verifyNoInteractions(session);
        verify(manager).markMarketDataAvailable(false);
    }
    @Test void portalCannotResolveMissingBrokerIdByHashingForexSymbol() {
        var credentials = new org.investpro.exchange.credentials.ExchangeCredentials(
                "interactive_brokers", "", "", null, null, null, "U123", false);
        var client = spy(new IbkrClientPortalClient(credentials));
        doReturn(true).when(client).isAuthenticated();
        var candidate = new IbkrContractCandidate(null, "EUR", "Euro", IbkrSecurityType.FOREX,
                "CASH", "IDEALPRO", "IDEALPRO", "USD", "EUR.USD", "", "", "", "",
                "CLIENT_PORTAL_SECDEF", "{\"syntheticCashContract\":true}");
        assertTrue(client.fetchSecurityDefinitionDetails(candidate).isEmpty());
    }
}