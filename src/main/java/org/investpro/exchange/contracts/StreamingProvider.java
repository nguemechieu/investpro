package org.investpro.exchange.contracts;


import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

public interface StreamingProvider {

    StreamTransport getStreamTransport();

    boolean supportsNativeWebSocket();

    boolean supportsHttpStreaming();

    boolean supportsPollingFallback();

    void connectStream();

    void disconnectStream();

    boolean isStreamConnected();

    void reconnectStream();

    void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer);

    void stopStreaming(ExchangeStreamSubscription subscription);

    void stopAllStreams();

    void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer);

    void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer);

    void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer);

    void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer);

    void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer);

    void streamAccount(ExchangeStreamConsumer consumer);

    void streamBalances(ExchangeStreamConsumer consumer);

    void streamOrders(ExchangeStreamConsumer consumer);

    void streamFills(ExchangeStreamConsumer consumer);

    void streamPositions(ExchangeStreamConsumer consumer);

    void stopTickerStream(TradePair tradePair);

    void stopTradesStream(TradePair tradePair);

    void stopOrderBookStream(TradePair tradePair);

    void stopCandlesStream(TradePair tradePair, int secondsPerCandle);

    void stopAccountStream();

    void stopBalancesStream();

    void stopOrdersStream();

    void stopFillsStream();

    void stopPositionsStream();
}