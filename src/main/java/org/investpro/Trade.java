package org.investpro;

import jakarta.persistence.*;
import lombok.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Represents a completed trade transaction where one party buys and the other sells an asset at a fixed price.
 * This class stores trade metadata including price, quantity, timestamps, and order details.
 * Author: Noel Nguemechieu
 */

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class Trade {
    public void createOrder(TradePair tradePair, BigDecimal price,
                            BigDecimal amount,Side side, ENUM_ORDER_TYPE orderType, Instant instant, BigDecimal stopLoss, BigDecimal takeProfit, int secondsPerCandle, Consumer<List<Trade>> tradeConsumer) {
        // Implement logic to create the order and update the trade consumer with the new trade
        // Example:
         Trade newTrade = Trade.builder()
            .account(account)
            .side(side)
            .orderType(orderType)
            .tradePair(tradePair)
            .exchange(exchange)
            .price(price)
            .amount(amount)
            .timestamp(instant)
            .stopLoss(stopLoss)
            .takeProfit(takeProfit)
            .build();
        tradeConsumer.accept(new ArrayList<>(Collections.singletonList(newTrade)));
        // Add the new trade to the trade history
        tradeHistory.getAllTrades().add(newTrade);


        try {
            exchange.createOrder(
                    tradePair,
                    side,orderType,price.byteValueExact(),amount.byteValueExact(), Date.from(timestamp),stopLoss.byteValueExact(),takeProfit.byteValueExact()

          );
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | ExecutionException |
                 InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    enum ACTIVITY_DATE{ DAILY, WEEKLY, MONTHLY}
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "local_trade_id", updatable = false, nullable = false)
    private long localTradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private Side side;

    @Enumerated(EnumType.STRING)
    private ENUM_ORDER_TYPE orderType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_pair_id", nullable = false)
    private TradePair tradePair;

    private Exchange exchange;

    @Column(name = "price", nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(name = "amount", nullable = false, precision = 18, scale = 8)
    private BigDecimal amount;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "stop_loss", precision = 18, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 18, scale = 8)
    private BigDecimal takeProfit;

    @Column(name = "fee", precision = 18, scale = 8)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    private SIGNAL signal;

    private static final Logger logger = LoggerFactory.getLogger(Trade.class);

    public static TradeHistory tradeHistory;

    static {
        try {
            tradeHistory = new TradeHistory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<PriceData> prices;
    private double openPrice;
    private double closePrice;
    private double highPrice;
    private double lowPrice;
    private double currentPrice;
    private double volume;
    private boolean isClosed;
    private long tradeId;
    private int openTime;
    private int closeTime;

    // 🔹 Constructor for real-time trade data
    public Trade(TradePair tradePair, BigDecimal price, BigDecimal amount, Side side, Instant timestamp) {
        this.tradeId = Instant.now().toEpochMilli();
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.side = side;
        this.timestamp = timestamp;
        this.highPrice = price.doubleValue();
        this.lowPrice = price.doubleValue();

        logger.info("New Trade Created: {}", this);
        tradeHistory.getAllTrades().add(this);

        // Start tracking high/low price
        startPriceTracking();

    }

    // 🔹 Constructor for executed trades with additional details
    public Trade(@NotNull Exchange exchange, TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType, BigDecimal price,
                 BigDecimal amount, Instant timestamp, BigDecimal stopLoss, BigDecimal takeProfit) {

        this.tradeId = Instant.now().toEpochMilli();
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.amount = amount;
        this.timestamp = timestamp;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.highPrice = price.doubleValue();
        this.lowPrice = price.doubleValue();

        tradeHistory.put(this);
        logger.info("New Trade Created: {}", this);

        // Start tracking high/low price
        startPriceTracking();
    }
    private final ScheduledExecutorService priceTrackerExecutor = Executors.newScheduledThreadPool(1);


    /**
     * 📊 **Efficiently tracks the high & low price while trade is open (without busy-waiting)**.
     */

    private  double getBalance() {

        return exchange.getAccountSummary().getLast().getBalance();
    }
    /**
     * 🚀 **Closes the trade and stops price tracking**.
     */
    public void closeOrder(int tradeId) {
        this.isClosed = true;
        this.closeTime = (int) System.currentTimeMillis();
        this.closePrice = currentPrice; // Store final closing price
        logger.info("Trade Closed: {}", this);

        CompletableFuture.runAsync(() -> {
            try {
                exchange.cancelOrder(String.valueOf(tradeId));
                tradeHistory.getTrade(tradeId).setStatus("Closed");
            } catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeyException e) {
                logger.error("Error closing trade {}: {}", tradeId, e.getMessage());
            }
        });
    }

    /**
     * 🚀 **Cancels all active orders**.
     */
    void closeAllOrders() {
        CompletableFuture.runAsync(() -> {
            try {
                exchange.cancelAllOrders();
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                logger.error("Error closing all orders: {}", e.getMessage());
            }
        });
        tradeHistory.getTrade(tradeId).setStatus("closed");


    }
String status;
    /**
     * 🛒 **Creates a new order and subscribes to live trade data**.
     */
    public void createOrder(TradePair tradePair, BigDecimal price, BigDecimal amount, Side side, Instant timestamp,
                            ENUM_ORDER_TYPE orderType, int secondsPerCandles, Consumer<List<Trade>> tradeConsumer) {

        CustomWebSocketClient websocketClient = exchange.getWebsocketClient(exchange,tradePair, secondsPerCandles);
        websocketClient.subscribe(tradePair, tradeConsumer);

        TradeOrder tradeOrder = new TradeOrder(exchange, tradePair, side, orderType, price, amount, timestamp);
        tradeOrder.createOrder();
        openTime = (int) Instant.now().toEpochMilli();
        tradeHistory.getTrade(tradeId).setStatus("Created");


    }

    /**
     * 📈 **Updates the trade's current price**.
     */
    public void updateTradePrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

















              private final ScheduledExecutorService reportExecutor = Executors.newScheduledThreadPool(1);


        private BigDecimal profitLoss;
        private final List<TradeActivity> tradeActivities = new ArrayList<>();
        private ACTIVITY_DATE reportFrequency;  // "DAILY", "WEEKLY", "MONTHLY"

        public Trade(Exchange exchange, TradePair tradePair, double initialPrice, ACTIVITY_DATE reportFrequency) {
            this.exchange = exchange;
            this.tradePair = tradePair;
            this.highPrice = initialPrice;
            this.lowPrice = initialPrice;
            this.reportFrequency = reportFrequency;
            this.tradeId = Instant.now().toEpochMilli();
            this.status = "Open";
            tradeHistory.put(this);

            // Start tracking price and activities
            startPriceTracking();
            startReportScheduler();
        }

        /**
         * 📊 **Tracks high/low prices and trade activities while trade is open.**
         */
        private void startPriceTracking() {
            priceTrackerExecutor.scheduleAtFixedRate(() -> {
                if (isClosed) {
                    logger.info("✅ Stopping price tracking for Trade: {}", tradePair);
                    priceTrackerExecutor.shutdown();
                    return;
                }

                try {
                    double latestPrice = exchange.fetchLivesBidAsk(tradePair);

                    if (latestPrice > highPrice) highPrice = latestPrice;
                    if (latestPrice < lowPrice) lowPrice = latestPrice;

                    currentPrice = latestPrice;
                    profitLoss = calculateProfitLoss();

                    // Record the trade activity
                    tradeActivities.add(new TradeActivity(Instant.now(), currentPrice, highPrice, lowPrice, profitLoss));

                    logger.info("🔹 Trade {} | High: {} | Low: {} | Current: {} | P/L: {}",
                            tradePair, highPrice, lowPrice, currentPrice, profitLoss);

                } catch (Exception e) {
                    logger.error("Error tracking price for trade {}: {}", tradePair, e.getMessage());
                }
            }, 6, 6, TimeUnit.SECONDS); // Executes every 1 second
        }

        /**
         * 📆 **Schedules report generation based on user setting (daily, weekly, monthly).**
         */
        private void startReportScheduler() {
            long delay = switch (reportFrequency) {
                case DAILY-> 1;
                case WEEKLY -> 7;
                case MONTHLY -> 30;
                // Default to daily
            };

            reportExecutor.scheduleAtFixedRate(this::generateReport, delay, delay, TimeUnit.DAYS);
        }

        /**
         * 📝 **Generates a trade report and saves it to a file.**
         */
        public void generateReport() {
            if (tradeActivities.isEmpty()) {
                logger.warn("📉 No trade activities recorded for report generation.");
                return;
            }

            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fileName = "TradeReport_" + tradePair + "_" + reportFrequency + "_" + date + ".csv";

            try (FileWriter writer = new FileWriter(fileName)) {
                writer.append("Timestamp,Current Price,High Price,Low Price,Profit/Loss\n");

                for (TradeActivity activity : tradeActivities) {
                    writer.append(String.format("%s,%.2f,%.2f,%.2f,%.2f\n",
                            activity.timestamp, activity.currentPrice, activity.highPrice, activity.lowPrice, activity.profitLoss));
                }

                logger.info("✅ Trade report generated: {}", fileName);
            } catch (IOException e) {
                logger.error("❌ Error writing trade report: {}", e.getMessage());
            }

            // Clear trade activity logs after saving report
            tradeActivities.clear();
        }

        /**
         * 💰 **Calculates profit/loss based on trade execution price.**
         */
        @Contract(" -> new")
        private @NotNull BigDecimal calculateProfitLoss() {
            return BigDecimal.valueOf(currentPrice - highPrice + lowPrice);
        }

        /**
         * 🚀 **Closes the trade and stops tracking.**
         */
        public void closeOrder() {
            this.isClosed = true;
            logger.info("🚨 Trade Closed: {}", this);
            priceTrackerExecutor.shutdown();
            reportExecutor.shutdown();
        }

    /**
     * 📊 **Trade Activity Tracker (For Reporting).**
     */
    private record TradeActivity(Instant timestamp, double currentPrice, double highPrice, double lowPrice,
                                 BigDecimal profitLoss) {
    }

}
