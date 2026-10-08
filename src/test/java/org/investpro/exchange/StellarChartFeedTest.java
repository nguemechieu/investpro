package org.investpro.exchange;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.data.CandleData;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.*;
import org.investpro.exchange.stellar.StellarNetwork;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;
import org.stellar.sdk.Asset;
import org.stellar.sdk.Server;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.requests.TradeAggregationsRequestBuilder;
import org.stellar.sdk.requests.OrderBookRequestBuilder;
import org.stellar.sdk.responses.OrderBookResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TradeAggregationResponse;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StellarChartFeedTest {
    private StellarNetwork adapter() {
        return new StellarNetwork(new ExchangeCredentials("stellar", "", "", "", "", "", "paper-account", true));
    }

    private TradeAggregationResponse record(long timestamp) {
        return new TradeAggregationResponse(timestamp, 1, "10", "20", "2", "3", null,
                "1", null, "2", null, "2.5", null);
    }

    private void injectServer(StellarNetwork stellar, Server server) {
        stellar.setMarketDataServer(server);
        stellar.setMarketDataServerPaperMode(true);
    }

    @Test
    void historyFetchesMultiplePagesNewestFirstThenReturnsChronologicalRecords() throws Exception {
        var stellar = adapter();
        var server = mock(Server.class);
        var request = mock(TradeAggregationsRequestBuilder.class);
        injectServer(stellar, server);
        when(server.tradeAggregations(any(), any(), anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(request);
        Page<TradeAggregationResponse> first = mock(Page.class);
        Page<TradeAggregationResponse> second = mock(Page.class);
        when(first.getRecords()).thenReturn(IntStream.range(50, 250).mapToObj(i -> record((i + 1L) * 60_000)).toList());
        when(second.getRecords()).thenReturn(IntStream.range(0, 50).mapToObj(i -> record((i + 1L) * 60_000)).toList());
        when(request.execute()).thenReturn(first, second);
        Method fetch = StellarNetwork.class.getDeclaredMethod("fetchAggregationRecords", Asset.class, Asset.class,
                long.class, long.class, long.class, int.class);
        fetch.setAccessible(true);
        List<TradeAggregationResponse> records = (List<TradeAggregationResponse>) fetch.invoke(stellar,
                Asset.createNativeAsset(), Asset.createNativeAsset(), 0L, 15_060L, 60_000L, 250);
        assertThat(records).hasSize(250);
        assertThat(records.getFirst().getTimestamp()).isEqualTo(60_000L);
        assertThat(records.getLast().getTimestamp()).isEqualTo(15_000_000L);
        verify(server).tradeAggregations(any(), any(), eq(0L), eq(3_060_000L), eq(60_000L), eq(0L));
        verify(request).limit(200);
        verify(request).limit(50);
        verify(request, times(2)).order(RequestBuilder.Order.DESC);
    }

    @Test
    void currentCandleQueriesItsOwnWindowAndRejectsOlderAggregation() throws Exception {
        var stellar = adapter();
        var server = mock(Server.class);
        var request = mock(TradeAggregationsRequestBuilder.class);
        injectServer(stellar, server);
        when(server.tradeAggregations(any(), any(), anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(request);
        Page<TradeAggregationResponse> page = mock(Page.class);
        when(page.getRecords()).thenReturn(List.of(record(60_000L)));
        when(request.execute()).thenReturn(page);
        Method fetch = StellarNetwork.class.getDeclaredMethod("fetchInProgressAggregationCandle", TradePair.class,
                long.class, long.class, long.class, boolean.class);
        fetch.setAccessible(true);
        Optional<?> result = (Optional<?>) fetch.invoke(stellar, new TradePair("XLM", "USDC"), 120L, 150L, 60_000L, false);
        assertThat(result).isEmpty();
        verify(server).tradeAggregations(any(), any(), eq(120_000L), eq(150_000L), eq(60_000L), eq(0L));
        verify(request).order(RequestBuilder.Order.DESC);
        verify(request).limit(1);
    }

    @Test
    void inverseCandlesPreserveQuoteOrientationAndVolume() throws Exception {
        var stellar = adapter();
        Method convert = StellarNetwork.class.getDeclaredMethod("invertedCandleFromAggregation", TradeAggregationResponse.class);
        convert.setAccessible(true);
        CandleData candle = (CandleData) convert.invoke(stellar, record(60_000L));
        assertThat(candle.openPrice()).isEqualTo(0.5);
        assertThat(candle.closePrice()).isEqualTo(0.4);
        assertThat(candle.highPrice()).isEqualTo(1.0);
        assertThat(candle.lowPrice()).isEqualTo(1.0 / 3.0);
        assertThat(candle.volume()).isEqualTo(20.0);
    }

    @Test
    void chartIntervalsMatchHorizonSupportedResolutions() throws Exception {
        var stellar = adapter();
        assertThat(stellar.getSupportedTimeframes()).doesNotContain(Timeframe.MN);
        assertThat(stellar.getCandleDataSupplier(60, new TradePair("XLM", "USDC")).getSupportedGranularities())
                .containsExactlyInAnyOrder(60, 300, 900, 3600, 86400, 604800);
        assertThat(stellar.supportsTimeframe(2_419_200)).isEqualTo("N/A");
    }

    @Test
    void paperModeUsesRealHorizonQuotesAndDoesNotInventPricesOnFailure() throws Exception {
        var stellar = adapter();
        var server = mock(Server.class);
        injectServer(stellar, server);
        var request = mock(OrderBookRequestBuilder.class, RETURNS_SELF);
        when(server.orderBook()).thenReturn(request);
        var response = mock(OrderBookResponse.class);
        var bid = mock(OrderBookResponse.Row.class);
        var ask = mock(OrderBookResponse.Row.class);
        when(bid.getPrice()).thenReturn("0.12");
        when(bid.getAmount()).thenReturn("100");
        when(ask.getPrice()).thenReturn("0.14");
        when(ask.getAmount()).thenReturn("100");
        when(response.getBids()).thenReturn(List.of(bid));
        when(response.getAsks()).thenReturn(List.of(ask));
        when(request.execute()).thenReturn(response);
        var quote = stellar.getLivePrice(new TradePair("XLM", "USDC"));
        assertThat(quote.getBidPrice()).isEqualTo(0.12);
        assertThat(quote.getAskPrice()).isEqualTo(0.14);
        assertThat(quote.getMidPrice()).isEqualTo(0.13);
        when(request.execute()).thenThrow(new IllegalStateException("Horizon unavailable"));
        assertThatThrownBy(() -> stellar.getLivePrice(new TradePair("XLM", "USDC")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subscriptionsStartAndStopTheSameChartInterval() throws Exception {
        var stellar = adapter();
        var poller = mock(PollingExchangeStreamer.class);
        var field = StellarNetwork.class.getDeclaredField("pollingStreamer");
        field.setAccessible(true);
        field.set(stellar, poller);
        var pair = new TradePair("XLM", "USDC");
        var consumer = mock(ExchangeStreamConsumer.class);
        var subscription = ExchangeStreamSubscription.marketData(Set.of(pair));
        subscription.setSecondsPerCandle(300);
        stellar.stream(subscription, consumer);
        stellar.stopStreaming(subscription);
        stellar.stopAllStreams();
        verify(poller).streamTicker(pair, consumer);
        verify(poller).streamOrderBook(pair, consumer);
        verify(poller).streamCandles(pair, 300, consumer);
        verify(poller).stopTicker(pair);
        verify(poller).stopOrderBook(pair);
        verify(poller).stopCandles(pair, 300);
        verify(poller).stopAll();
    }
}
