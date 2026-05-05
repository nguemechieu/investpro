package org.investpro.exchange;

import org.investpro.exchange.infrastructure.BrokerExchangeAdapter;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Trade;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Alpaca extends BrokerExchangeAdapter {

    public Alpaca(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
    }

    @Override
    public String getName() {
        return "ALPACA";
    }

    @Override
    public String getExchangeId() {
        return "alpaca";
    }

    @Override
    public String getDisplayName() {
        return "Alpaca";
    }

    @Override
    public boolean isPaperTrading() {
        return true;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS);
    }

    // --------- Capability Methods ---------

    @Override
    public boolean supportsLiveTrading() {
        return true;
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return false;
    }

    @Override
    public boolean supportsPositions() {
        return true;
    }

    @Override
    public boolean supportsAccountTrades() {
        return true;
    }

    @Override
    public boolean supportsStopLossTakeProfit() {
        return true;
    }

    @Override
    public boolean supportsBracketOrders() {
        return false;
    }

    @Override
    public boolean supportsLeverage() {
        return false;
    }

    @Override
    public boolean supportsDerivatives() {
        return false;
    }

    @Override
    public boolean supportsForex() {
        return false;
    }

    @Override
    public boolean supportsStocks() {
        return true;
    }

    @Override
    public boolean supportsCrypto() {
        return false;
    }

    // --------- Streaming Transport Methods ---------

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.POLLING;
    }

    @Override
    public boolean supportsNativeWebSocket() {
        return false;
    }

    @Override
    public boolean supportsHttpStreaming() {
        return false;
    }

    @Override
    public boolean supportsPollingFallback() {
        return true;
    }

    @Override
    public void connectStream() {
        // Alpaca uses polling for streaming
    }

    @Override
    public void disconnectStream() {
        stopAllStreams();
    }

    @Override
    public boolean isStreamConnected() {
        return isConnected();
    }

    @Override
    public void reconnectStream() {
        disconnectStream();
        connectStream();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        if (subscription == null || consumer == null) {
            return;
        }
        // Polling-based streaming
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                streamTicker(pair, consumer);
            }
            if (subscription.isTrades()) {
                streamTrades(pair, consumer);
            }
            if (subscription.isCandles()) {
                streamCandles(pair, 60, consumer);
            }
        }
        if (subscription.isAccount()) {
            streamAccount(consumer);
        }
        if (subscription.isOrders()) {
            streamOrders(consumer);
        }
        if (subscription.isBalances()) {
            streamBalances(consumer);
        }
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
        if (subscription == null) {
            return;
        }
        stopAllStreams();
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Not supported
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        // Not applicable
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        // Stop polling
    }

    @Override
    public void stopAccountStream() {
        // Stop polling
    }

    @Override
    public void stopBalancesStream() {
        // Stop polling
    }

    @Override
    public void stopOrdersStream() {
        // Stop polling
    }

    @Override
    public void stopFillsStream() {
        // Stop polling
    }

    @Override
    public void stopPositionsStream() {
        // Stop polling
    }

    // --------- Streaming Capability Methods ---------

    @Override
    public boolean supportsTickerStreaming() {
        return false;
    }

    @Override
    public boolean supportsAccountStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderStreaming() {
        return false;
    }

    @Override
    public boolean supportsFillStreaming() {
        return false;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return false;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return false;
    }
    // --------- Order Creation Methods ---------

    @Override
    public CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice
    ) {
        return failedFuture(unsupported("createLimitOrder"));
    }

    @Override
    public CompletableFuture<String> createStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double stopPrice
    ) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit
    ) {
        return failedFuture(unsupported("createBracketOrder"));
    }

    // --------- Order Cancellation Methods ---------

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return failedFuture(unsupported("cancelOrder"));
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        return failedFuture(unsupported("cancelOrders"));
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return failedFuture(unsupported("cancelAllOrders"));
    }

    // --------- Order Query Methods ---------

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return failedFuture(unsupported("fetchOrder"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return failedFuture(unsupported("fetchOpenOrders"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return failedFuture(unsupported("fetchAllOpenOrders"));
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return failedFuture(unsupported("fetchOrderHistory"));
    }

    // --------- Position Methods ---------

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return failedFuture(unsupported("fetchPositions"));
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return failedFuture(unsupported("fetchAllPositions"));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return failedFuture(unsupported("fetchPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return failedFuture(unsupported("closeAllPositions"));
    }

    // --------- Trade History Methods ---------

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        return failedFuture(unsupported("fetchAccountTrades"));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return failedFuture(unsupported("fetchAccountTradesSince"));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(
            TradePair tradePair,
            Instant from,
            Instant to
    ) {
        return failedFuture(unsupported("fetchAccountTradesBetween"));
    }

    // --------- Manual Trading Methods ---------

    @Override
    public void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    ) {
        // Not implemented for Alpaca yet
    }

    @Override
    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    ) {
        // Not implemented for Alpaca yet
    }

    @Override
    public void autoTrading(Boolean auto, String signal) {
        // Not implemented for Alpaca yet
    }

    // --------- Order Validation Methods ---------

    @Override
    public CompletableFuture<Boolean> validateOrder(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    ) {
        boolean valid = tradePair != null
                && supportsMarketType(marketType)
                && size >= getMinOrderAmount(tradePair);
        return CompletableFuture.completedFuture(valid);
    }

    // --------- Normalization Methods ---------

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return amount > 0 && Double.isFinite(amount) ? amount : 0.0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return price >= 0 && Double.isFinite(price) ? price : 0.0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.001;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(unsupported("setLeverage"));
    }}
