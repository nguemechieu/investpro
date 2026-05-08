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
import org.investpro.timeframe.Timeframe;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class InteractiveBrokers extends BrokerExchangeAdapter {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InteractiveBrokers.class);

    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    public InteractiveBrokers(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        initializePaperTradingAccount();
    }

    private void initializePaperTradingAccount() {
        balances.put("USD", 100000.0);
    }

    @Override
    public String getName() {
        return "INTERACTIVE BROKERS";
    }

    @Override
    public String getExchangeId() {
        return "interactive_brokers";
    }

    @Override
    public String getDisplayName() {
        return "Interactive Brokers";
    }

    @Override
    public boolean isPaperTrading() {
        // If user explicitly selected trading mode during onboarding, respect that
        if (getUserSelectedTradingMode() != null && !getUserSelectedTradingMode().isBlank()) {
            return "PAPER".equalsIgnoreCase(getUserSelectedTradingMode());
        }
        // Otherwise, default to paper trading for Interactive Brokers
        return true;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS, MARKET_TYPES.FOREX);
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return createMarketOrder(symbol, side, quantity);
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return createLimitOrder(symbol, side, quantity, limitPrice);
    }

    // --------- Capability Methods ---------

    @Override
    public boolean supportsLiveTrading() {
        return false;
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsLeverage() {
        return true;
    }

    @Override
    public boolean supportsDerivatives() {
        return true;
    }

    @Override
    public boolean supportsForex() {
        return true;
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
        // Interactive Brokers uses polling for streaming
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
        if (subscription.isPositions()) {
            streamPositions(consumer);
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
        // Polling-based
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
        // Stop polling
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

    @Override
    public boolean supportsCandleStreaming() {
        return true;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return true;
    }

    // --------- Order Creation Methods ---------

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            double fillPrice = 150.0;
            if (side == Side.BUY) {
                double cost = amount * fillPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient buying power");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient position");
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USD", balances.getOrDefault("USD", 0.0) + (amount * fillPrice));
            }
            // Record trade in history
            Trade trade = new Trade();
            trade.setTradePair(tradePair);
            trade.setPrice(fillPrice);
            trade.setAmount(amount);
            trade.setTransactionType(side);
            trade.setLocalTradeId(System.nanoTime());
            trade.setTimestamp(java.time.Instant.now());
            trade.setFee(0.0);
            trade.setStopLoss(0.0);
            trade.setTakeProfit(0.0);
            trade.setSwap(0.0);
            trade.setProfit(0.0);
            tradeHistory.add(trade);
            orders.put(orderId, "FILLED");
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice) {
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            if (side == Side.BUY) {
                double cost = amount * limitPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient buying power");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient position");
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USD", balances.getOrDefault("USD", 0.0) + (amount * limitPrice));
            }
            // Record trade in history
            Trade trade = new Trade();
            trade.setTradePair(tradePair);
            trade.setPrice(limitPrice);
            trade.setAmount(amount);
            trade.setTransactionType(side);
            trade.setLocalTradeId(System.nanoTime());
            trade.setTimestamp(java.time.Instant.now());
            trade.setFee(0.0);
            trade.setStopLoss(0.0);
            trade.setTakeProfit(0.0);
            trade.setSwap(0.0);
            trade.setProfit(0.0);
            tradeHistory.add(trade);
            orders.put(orderId, "FILLED");
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double stopPrice) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
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
        if (tradePair == null) {
            return CompletableFuture.completedFuture(new ArrayList<>(tradeHistory));
        }
        return CompletableFuture.completedFuture(
                tradeHistory.stream()
                        .filter(t -> t.getTradePair() != null && t.getTradePair().equals(tradePair))
                        .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        List<Trade> result = tradeHistory.stream()
                .filter(t -> since == null || (t.getTimestamp() != null && t.getTimestamp().isAfter(since)))
                .filter(t -> tradePair == null || (t.getTradePair() != null && t.getTradePair().equals(tradePair)))
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(
            TradePair tradePair,
            Instant from,
            Instant to) {
        List<Trade> result = tradeHistory.stream()
                .filter(t -> t.getTimestamp() != null &&
                        (from == null || t.getTimestamp().isAfter(from)) &&
                        (to == null || t.getTimestamp().isBefore(to)))
                .filter(t -> tradePair == null || (t.getTradePair() != null && t.getTradePair().equals(tradePair)))
                .toList();
        return CompletableFuture.completedFuture(result);
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
            double slippage) {
        // Not implemented for Interactive Brokers yet
    }

    @Override
    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        // Not implemented for Interactive Brokers yet
    }

    @Override
    public void autoTrading(@NotNull Boolean auto, String signal) {
        // Not implemented for Interactive Brokers yet
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
            double slippage) {
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
        return 20.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(unsupported("setLeverage"));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(unsupported("modifyStopLoss"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return failedFuture(unsupported("closePartialPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(unsupported("modifyTakeProfit"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(unsupported("enableTrailingStop"));
    }
    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(
                Timeframe.M1,
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1);
    }

}
