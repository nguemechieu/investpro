package org.investpro.exchange;

import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualOrderSubmissionTest {
    static Stream<Class<? extends Exchange>> exchanges() {
        return Stream.<Class<? extends Exchange>>of(org.investpro.exchange.alpaca.Alpaca.class,
                org.investpro.exchange.binance.Binance.class, org.investpro.exchange.binanceus.BinanceUs.class,
                org.investpro.exchange.bitfinex.Bitfinex.class, org.investpro.exchange.coinbase.Coinbase.class,
                org.investpro.exchange.ibkr.IbkrExchange.class, org.investpro.exchange.kraken.Kraken.class,
                org.investpro.exchange.oanda.Oanda.class, org.investpro.exchange.schwab.Schwab.class,
                org.investpro.exchange.stellar.StellarNetwork.class);
    }
    @TestFactory Stream<DynamicTest> marketAndLimitCases() {
        return exchanges().flatMap(venue -> Stream.of(ManualOrderSubmission.Type.MARKET, ManualOrderSubmission.Type.LIMIT)
                .map(type -> DynamicTest.dynamicTest(venue.getSimpleName() + " " + type,
                        () -> manualMarketAndLimitRouteToSelectedExchange(venue, type))));
    }    private Exchange venue(Class<? extends Exchange> type) {
        var venue = mock(type);
        when(venue.isAuthenticatedSessionConnected()).thenReturn(true);
        when(venue.canSubmitLiveOrders()).thenReturn(true);
        when(venue.deskOrderExecution()).thenReturn(venue);
        if (venue instanceof org.investpro.exchange.ibkr.IbkrExchange ibkr)
            when(ibkr.executeRiskApproved(eq(true), any())).thenAnswer(call ->
                    ((java.util.function.Supplier<?>) call.getArgument(1)).get());
        return venue;
    }
    void manualMarketAndLimitRouteToSelectedExchange(Class<? extends Exchange> type, ManualOrderSubmission.Type orderType) throws Exception {
        var venue = venue(type); var pair = new TradePair("BTC", "USD");
        when(venue.createMarketOrder(pair, Side.BUY, 2)).thenReturn(CompletableFuture.completedFuture("native-market"));
        when(venue.createLimitOrder(pair, Side.SELL, 2, 100)).thenReturn(CompletableFuture.completedFuture("native-limit"));
        assertEquals(orderType == ManualOrderSubmission.Type.MARKET ? "native-market" : "native-limit",
                submit(venue, false, orderType, pair, orderType == ManualOrderSubmission.Type.MARKET ? Side.BUY : Side.SELL, 2, 100).join());
        if (orderType == ManualOrderSubmission.Type.MARKET) verify(venue, times(1)).createMarketOrder(pair, Side.BUY, 2);
        else verify(venue, times(1)).createLimitOrder(pair, Side.SELL, 2, 100);
    }
    @Test void invalidQuantityAndSideNeverReachBroker() throws Exception {
        var venue = venue(Exchange.class); var pair = new TradePair("BTC", "USD");
        for (double value : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(CompletionException.class, () -> submit(venue,false,ManualOrderSubmission.Type.MARKET,pair,Side.BUY,value,0).join());
        assertThrows(CompletionException.class, () -> submit(venue,false,ManualOrderSubmission.Type.MARKET,pair,null,1,0).join());
        verify(venue, never()).deskOrderExecution();
    }
    @Test void authenticationChangingWhileQueuedCannotBecomePaperOrLiveOrder() throws Exception {
        var venue = venue(Exchange.class); var pair = new TradePair("BTC", "USD");
        List<Runnable> queued = new ArrayList<>();
        var result = ManualOrderSubmission.submit(venue,false,ManualOrderSubmission.Type.MARKET,pair,Side.BUY,1,0,0,0,false,queued::add);
        when(venue.isDeskPaperTrading()).thenReturn(true);
        queued.getFirst().run();
        assertThrows(CompletionException.class, result::join);
        verify(venue, never()).createMarketOrder(any(),any(),anyDouble());
    }
    @Test void paperOrdersUseOnlyCapturedLocalProvider() throws Exception {
        var venue = venue(Exchange.class); var provider = mock(OrderExecutionProvider.class);
        when(venue.isDeskPaperTrading()).thenReturn(true); when(venue.localPaperOrderExecution()).thenReturn(provider);
        var pair = new TradePair("BTC", "USD");
        when(provider.createMarketOrder(pair,Side.BUY,1)).thenReturn(CompletableFuture.completedFuture("local-order"));
        assertEquals("local-order",submit(venue,true,ManualOrderSubmission.Type.MARKET,pair,Side.BUY,1,0).join());
        verify(venue, never()).createMarketOrder(any(),any(),anyDouble());
    }
    @Test void unsupportedStopsCannotTurnIntoMarketOrders() throws Exception {
        var venue = venue(Exchange.class); var pair = new TradePair("BTC", "USD");
        assertThrows(CompletionException.class, () -> submit(venue,false,ManualOrderSubmission.Type.STOP,pair,Side.BUY,1,100).join());
        verify(venue, never()).createMarketOrder(any(),any(),anyDouble());
        verify(venue, never()).createStopOrder(any(),any(),anyDouble(),anyDouble());
    }
    @Test void synchronousBrokerFailureBecomesFailedFutureOffCallerThread() throws Exception {
        var venue = venue(Exchange.class); var pair = new TradePair("BTC", "USD");
        var threadName = new AtomicReference<String>();
        when(venue.createMarketOrder(pair,Side.BUY,1)).thenAnswer(_ -> {
            threadName.set(Thread.currentThread().getName()); throw new IllegalStateException("broker rejected");
        });
        var result = ManualOrderSubmission.submit(venue,false,ManualOrderSubmission.Type.MARKET,pair,Side.BUY,1,0,0,0,false);
        assertThrows(ExecutionException.class, () -> result.get(5,TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("investpro-trading-"));
        verify(venue,times(1)).createMarketOrder(pair,Side.BUY,1);
    }
    @Test void invalidPricesAndReversedBracketsNeverSubmit() throws Exception {
        var venue = venue(Exchange.class); var pair = new TradePair("BTC", "USD");
        for (double price : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(CompletionException.class, () -> submit(venue,false,ManualOrderSubmission.Type.LIMIT,pair,Side.BUY,1,price).join());
        when(venue.supportsBracketOrders()).thenReturn(true);
        assertThrows(CompletionException.class, () -> ManualOrderSubmission.submit(venue,false,
                ManualOrderSubmission.Type.BRACKET,pair,Side.BUY,1,100,110,90,false,Runnable::run).join());
        verify(venue,never()).createLimitOrder(any(),any(),anyDouble(),anyDouble());
        verify(venue,never()).createBracketOrder(any(),any(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
    }    private CompletableFuture<String> submit(Exchange venue, boolean paper, ManualOrderSubmission.Type type,
            TradePair pair, Side side, double quantity, double price) {
        return ManualOrderSubmission.submit(venue,paper,type,pair,side,quantity,price,0,0,false,Runnable::run);
    }
}