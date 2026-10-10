package org.investpro.exchange.infrastructure;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.coinbase.CoinbaseJwtSigner;
import org.investpro.exchange.coinbase.CoinbaseMarketDataService;
import org.investpro.exchange.models.ExchangeAccessMode;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.ExchangeFeature;
import org.investpro.models.Account;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollingExchangeStreamerTest {
    @Test void fortyCoinbaseCandlePollsStartAtDifferentTimesAndRestartResetsPhase() throws Exception {
        var exchange = coinbase(false, false);
        var scheduler = scheduler(mock(ScheduledFuture.class));
        var streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        var consumer = mock(ExchangeStreamConsumer.class);
        for (int index = 0; index < 40; index++)
            streamer.streamCandles(new TradePair("ASSET" + index, "USD"), 60, consumer);
        var delays = ArgumentCaptor.forClass(Long.class);
        verify(scheduler, times(40)).scheduleAtFixedRate(any(Runnable.class), delays.capture(), eq(60L), eq(TimeUnit.SECONDS));
        assertEquals(java.util.stream.LongStream.range(0, 40).boxed().toList(), delays.getAllValues());
        streamer.stopAll();
        clearInvocations(scheduler);
        streamer.streamCandles(new TradePair("BTC", "USD"), 60, consumer);
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(60L), eq(TimeUnit.SECONDS));
        streamer.stopAll();
    }
    @Test void unfinishedOrderPollCannotOverlapAndStoppedPollCannotDeliverLateSnapshot() {
        var exchange = mock(Exchange.class);
        when(exchange.getName()).thenReturn("Test");
        when(exchange.canUseCapability(ExchangeFeature.OPEN_ORDERS)).thenReturn(true);
        var response = new CompletableFuture<List<org.investpro.models.trading.OpenOrder>>();
        when(exchange.fetchAllOpenOrders()).thenReturn(response);
        var task = mock(ScheduledFuture.class);
        var scheduler = scheduler(task);
        var consumer = mock(ExchangeStreamConsumer.class);
        var streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        streamer.streamOrders(consumer);
        var poll = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(poll.capture(), eq(0L), eq(20L), eq(TimeUnit.SECONDS));
        poll.getValue().run(); poll.getValue().run();
        verify(exchange).fetchAllOpenOrders();
        streamer.stopAll();
        response.complete(List.of());
        verifyNoInteractions(consumer);
    }

    @Test void oandaBatchesSubscribersSkipsOverlappingRequestsAndNeverPollsDepth() throws Exception {
        var exchange = mock(org.investpro.exchange.oanda.Oanda.class);
        when(exchange.getName()).thenReturn("OANDA");
        var eur = new TradePair("EUR", "USD");
        var gbp = new TradePair("GBP", "USD");
        var response = new CompletableFuture<Map<String, Ticker>>();
        when(exchange.getLatestPrices(anyList())).thenReturn(response);
        var task = mock(ScheduledFuture.class);
        var scheduler = scheduler(task);
        var streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        var first = mock(ExchangeStreamConsumer.class);
        var second = mock(ExchangeStreamConsumer.class);
        streamer.streamTicker(eur, first);
        streamer.streamTicker(gbp, first);
        streamer.streamTicker(eur, second);
        streamer.streamOrderBook(eur, first);
        var poll = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(poll.capture(), eq(0L), eq(30L), eq(TimeUnit.SECONDS));
        poll.getValue().run(); poll.getValue().run();
        verify(exchange).getLatestPrices(List.of(eur, gbp));
        verify(exchange, never()).getLivePrice(any());
        verify(exchange, never()).fetchOrderBook(any());
        var eurQuote = new Ticker();
        var gbpQuote = new Ticker();
        response.complete(Map.of("EUR_USD", eurQuote, "GBP_USD", gbpQuote));
        verify(first).onTicker("OANDA", eur, eurQuote);
        verify(first).onTicker("OANDA", gbp, gbpQuote);
        verify(second).onTicker("OANDA", eur, eurQuote);
        streamer.stopAll();
        verify(task).cancel(false);
    }

    @Test
    void candlePollingDeliversQuoteBeforeLatestClosedCandleAndStopsCleanly() throws Exception {
        Exchange exchange = mock(Exchange.class);
        TradePair pair = new TradePair("BTC", "USD");
        when(exchange.getName()).thenReturn("Test");
        var supplier = mock(org.investpro.utils.CandleDataSupplier.class);
        when(exchange.getCandleDataSupplier(3600, pair)).thenReturn(supplier);
        int now = (int) Instant.now().getEpochSecond();
        var closed = new org.investpro.data.CandleData(100, 101, 102, 99, now - 7200, 10);
        var forming = new org.investpro.data.CandleData(101, 102, 103, 100, now - 100, 10);
        when(supplier.get()).thenReturn(CompletableFuture.completedFuture(List.of(forming, closed)));
        Ticker quote = new Ticker();
        when(exchange.getLivePrice(pair)).thenReturn(quote);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        ScheduledExecutorService scheduler = scheduler(task);
        var streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        var consumer = mock(ExchangeStreamConsumer.class);
        streamer.streamCandles(pair, 3600, consumer);
        streamer.streamCandles(pair, 3600, consumer);
        ArgumentCaptor<Runnable> poll = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(poll.capture(), eq(0L), eq(60L), eq(TimeUnit.SECONDS));
        poll.getValue().run();
        var order = inOrder(consumer);
        order.verify(consumer).onTicker("Test", pair, quote);
        order.verify(consumer).onCandle("Test", pair, closed);
        verify(consumer, never()).onCandle("Test", pair, forming);
        streamer.stopCandles(pair, 3600);
        verify(task).cancel(false);
        streamer.stopAll();
    }
    @Test
    void private401RevokesAccessAndSuccessfulAuthenticationRestoresIt() throws Exception {
        Coinbase exchange = coinbase(true, false);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> rejected = mock(HttpResponse.class);
        when(rejected.statusCode()).thenReturn(401);
        when(rejected.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(rejected.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        doReturn(CompletableFuture.completedFuture(rejected)).when(client)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        setField(exchange, "httpClient", client);
        var send = Coinbase.class.getDeclaredMethod("sendAsyncWithRetry", HttpRequest.class, int.class, int.class);
        send.setAccessible(true);
        var response = (CompletableFuture<?>) send.invoke(exchange, HttpRequest.newBuilder(
                java.net.URI.create("https://api.coinbase.com/api/v3/brokerage/orders/historical/batch")).GET().build(), 0, 3);
        assertThrows(java.util.concurrent.CompletionException.class, response::join);
        assertFalse(exchange.hasPrivateAuthentication());
        assertFalse(exchange.canUseCapability(ExchangeFeature.OPEN_ORDERS));
        assertTrue(exchange.canUseCapability(ExchangeFeature.TICKER));
        HttpResponse<byte[]> accepted = mock(HttpResponse.class);
        when(accepted.statusCode()).thenReturn(200);
        when(accepted.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(accepted.body()).thenReturn("{\"accounts\":[]}".getBytes(StandardCharsets.UTF_8));
        doReturn(accepted).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        doCallRealMethod().when(exchange).checkAuthentication();
        assertTrue(exchange.checkAuthentication().isSuccess());
        assertTrue(exchange.hasPrivateAuthentication());
    }

    @Test
    void privatePollerRechecksAuthenticationAndResumesAfterRecovery() throws Exception {
        Coinbase exchange = coinbase(true, false);
        when(exchange.fetchAllOpenOrders()).thenReturn(CompletableFuture.completedFuture(List.of()));
        ScheduledExecutorService scheduler = scheduler(mock(ScheduledFuture.class));
        PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        streamer.streamOrders(mock(ExchangeStreamConsumer.class));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(task.capture(), eq(0L), anyLong(), eq(TimeUnit.SECONDS));
        task.getValue().run();
        setField(exchange, "privateAuthenticationRejected", true);
        assertFalse(exchange.hasPrivateAuthentication());
        assertTrue(exchange.canUseCapability(ExchangeFeature.TICKER));
        task.getValue().run();
        verify(exchange, times(1)).fetchAllOpenOrders();
        setField(exchange, "privateAuthenticationRejected", false);
        task.getValue().run();
        verify(exchange, times(2)).fetchAllOpenOrders();
        streamer.stopAll();
    }

    @Test
    void realCoinbaseInitializesAndDiscoversProductsWithoutPrivateCredentials() throws Exception {
        Coinbase exchange = new Coinbase(new ExchangeCredentials("coinbase", "", "", null, null, null, null, false));
        try {
            HttpClient client = mock(HttpClient.class);
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
            when(response.body()).thenReturn("""
                    {"products":[{"product_id":"BTC-USD","base_currency_id":"BTC",
                    "quote_currency_id":"USD","product_type":"SPOT","status":"online",
                    "trading_disabled":false}],"num_products":1}
                    """.getBytes(StandardCharsets.UTF_8));
            doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            setField(exchange, "httpClient", client);
            assertEquals(ExchangeAccessMode.PUBLIC_DATA_ONLY, exchange.getAccessMode());
            assertFalse(exchange.getTradePairSymbol().isEmpty());
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(client, atLeastOnce()).send(requests.capture(), any(HttpResponse.BodyHandler.class));
            for (HttpRequest request : requests.getAllValues()) {
                assertTrue(request.headers().firstValue("Authorization").isEmpty());
                assertFalse(request.uri().getPath().contains("accounts"));
            }
        } finally {
            exchange.stopAllStreams();
        }
    }

    private Coinbase coinbase(boolean authenticated, boolean paper) throws Exception {
        Coinbase exchange = mock(Coinbase.class);
        when(exchange.getName()).thenReturn("Coinbase");
        when(exchange.isPaperTrading()).thenReturn(paper);
        doCallRealMethod().when(exchange).getCapability();
        doCallRealMethod().when(exchange).hasPrivateAuthentication();
        doCallRealMethod().when(exchange).canUseCapability(any(ExchangeFeature.class));
        doCallRealMethod().when(exchange).getAccessMode();
        if (authenticated) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";
            setField(exchange, "jwtSigner", new CoinbaseJwtSigner("organizations/test/apiKeys/test", pem));
        }
        return exchange;
    }

    private void setField(Coinbase exchange, String name, Object value) throws Exception {
        Field field = Coinbase.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(exchange, value);
    }

    private ScheduledExecutorService scheduler(ScheduledFuture<?> future) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        return scheduler;
    }

    @Test
    void unauthenticatedCoinbaseNeverCreatesPrivateTasksIncludingPaperMode() throws Exception {
        for (boolean paper : new boolean[] {false, true}) {
            Coinbase exchange = coinbase(false, paper);
            Supplier<ScheduledExecutorService> factory = mock(Supplier.class);
            ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
            PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, factory);
            for (int attempt = 0; attempt < 3; attempt++) {
                streamer.streamAccount(consumer);
                streamer.streamBalances(consumer);
                streamer.streamOrders(consumer);
                streamer.streamFills(consumer);
                streamer.streamPositions(consumer);
            }
            assertEquals(ExchangeAccessMode.PUBLIC_DATA_ONLY, exchange.getAccessMode());
            verifyNoInteractions(factory, consumer);
            verify(exchange, never()).fetchAllPositions();
            verify(exchange, never()).fetchAllOpenOrders();
            verify(exchange, never()).fetchAccount();
            verify(exchange, never()).fetchAccountTrades(any());
            streamer.stopAll();
            verifyNoInteractions(factory);
        }
    }

    @Test
    void publicMarketStreamsStillRunWithoutAuthentication() throws Exception {
        Coinbase exchange = coinbase(false, false);
        assertTrue(exchange.canUseCapability(ExchangeFeature.TICKER));
        assertTrue(exchange.canUseCapability(ExchangeFeature.HISTORICAL_CANDLES));
        assertTrue(exchange.canUseCapability(ExchangeFeature.ORDER_BOOK));
        assertTrue(exchange.canUseCapability(ExchangeFeature.STREAMING_TRADES));
        TradePair pair = mock(TradePair.class);
        Ticker ticker = new Ticker();
        when(exchange.getLatestSnapshot(pair)).thenReturn(
                new CoinbaseMarketDataService.MarketDataSnapshot(pair, ticker, null, List.of(), Instant.now()));
        ScheduledExecutorService scheduler = scheduler(mock(ScheduledFuture.class));
        ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
        PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        streamer.streamTicker(pair, consumer);
        streamer.streamOrderBook(pair, consumer);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(2)).scheduleAtFixedRate(tasks.capture(), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        tasks.getAllValues().forEach(Runnable::run);
        verify(consumer).onTicker("Coinbase", pair, ticker);
        verify(consumer, never()).onError(anyString(), any());
        verify(exchange, never()).fetchAllPositions();
        streamer.stopAll();
    }

    @Test
    void authenticatedPrivateStreamsScheduleOnceAndCancelOnShutdown() throws Exception {
        Coinbase exchange = coinbase(true, false);
        assertEquals(ExchangeAccessMode.AUTHENTICATED, exchange.getAccessMode());
        when(exchange.fetchAccount()).thenReturn(CompletableFuture.completedFuture(new Account()));
        when(exchange.fetchAllOpenOrders()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(exchange.fetchAllPositions()).thenReturn(CompletableFuture.completedFuture(List.of()));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ScheduledExecutorService scheduler = scheduler(future);
        ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
        PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        streamer.streamAccount(consumer);
        streamer.streamBalances(consumer);
        streamer.streamOrders(consumer);
        streamer.streamFills(consumer);
        streamer.streamPositions(consumer);
        streamer.streamPositions(consumer);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(3)).scheduleAtFixedRate(tasks.capture(), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        tasks.getAllValues().forEach(Runnable::run);
        verify(exchange).fetchAllPositions();
        verify(exchange).fetchAllOpenOrders();
        verify(exchange).fetchAccount();
        verify(consumer, never()).onError(anyString(), any());
        streamer.stopAll();
        verify(future, times(3)).cancel(false);
        verify(scheduler).shutdownNow();
    }

    @Test
    void unsupportedPositionsAreNotScheduledEvenWithAuthentication() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getName()).thenReturn("Unsupported");
        when(exchange.hasPrivateAuthentication()).thenReturn(true);
        when(exchange.getCapability()).thenReturn(ExchangeCapability.builder().supportsPositions(false).build());
        doCallRealMethod().when(exchange).canUseCapability(any(ExchangeFeature.class));
        Supplier<ScheduledExecutorService> factory = mock(Supplier.class);
        new PollingExchangeStreamer(exchange, factory).streamPositions(mock(ExchangeStreamConsumer.class));
        verifyNoInteractions(factory);
        verify(exchange, never()).fetchAllPositions();
    }

    @Test
    void bearerAuthenticationUsesTheExistingCoinbasePredicate() throws Exception {
        Coinbase exchange = coinbase(false, false);
        setField(exchange, "apiSecret", "Bearer prebuilt.jwt.token");
        assertTrue(exchange.hasPrivateAuthentication());
        assertTrue(exchange.canUseCapability(ExchangeFeature.POSITIONS));
        when(exchange.isPaperTrading()).thenReturn(true);
        assertFalse(exchange.hasPrivateAuthentication());
    }

    @Test
    void unexpectedPrivateEndpointFailureStillReachesConsumer() throws Exception {
        Coinbase exchange = coinbase(true, false);
        RuntimeException failure = new RuntimeException("network unavailable");
        when(exchange.fetchAllPositions()).thenReturn(CompletableFuture.failedFuture(failure));
        ScheduledExecutorService scheduler = scheduler(mock(ScheduledFuture.class));
        ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
        PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, () -> scheduler);
        streamer.streamPositions(consumer);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(task.capture(), eq(0L), anyLong(), eq(TimeUnit.SECONDS));
        task.getValue().run();
        verify(consumer).onError("Coinbase", failure);
        streamer.stopAll();
    }

    @Test
    void disabledPrivateSubscriptionLogsOnlyOnce() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PollingExchangeStreamer.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            PollingExchangeStreamer streamer = new PollingExchangeStreamer(coinbase(false, false),
                    () -> { throw new AssertionError("Disabled streams must not allocate an executor"); });
            ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
            for (int attempt = 0; attempt < 5; attempt++) {
                streamer.streamPositions(consumer);
            }
            assertEquals(1, appender.list.size());
            assertEquals(ch.qos.logback.classic.Level.INFO, appender.list.getFirst().getLevel());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("authentication-not-configured"));
            verifyNoInteractions(consumer);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void stoppedStreamerCanRestartWithAFreshExecutor() throws Exception {
        Coinbase exchange = coinbase(true, false);
        ScheduledFuture<?> firstTask = mock(ScheduledFuture.class);
        ScheduledExecutorService first = scheduler(firstTask);
        ScheduledExecutorService second = scheduler(mock(ScheduledFuture.class));
        Supplier<ScheduledExecutorService> factory = mock(Supplier.class);
        when(factory.get()).thenReturn(first, second);
        PollingExchangeStreamer streamer = new PollingExchangeStreamer(exchange, factory);
        ExchangeStreamConsumer consumer = mock(ExchangeStreamConsumer.class);
        streamer.streamPositions(consumer);
        streamer.stopAll();
        streamer.stopAll();
        streamer.streamPositions(consumer);
        verify(firstTask).cancel(false);
        verify(first).shutdownNow();
        verify(second).scheduleAtFixedRate(any(Runnable.class), eq(0L), anyLong(), eq(TimeUnit.SECONDS));
        verify(factory, times(2)).get();
        streamer.stopAll();
    }
}
