package org.investpro.core.bot;


import org.investpro.repository.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.Db1;
import org.investpro.exchange.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.models.trading.TradePair;
import org.investpro.service.CurrencyService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

/**
 * SmartBotUsageExamples demonstrates all available SmartBot streaming methods.
 * This shows how to use each factory method and streaming mode.
 * Key Methods Demonstrated:
 * - startStreaming(pair) - Default mode (EVERYTHING)
 * - startStreaming(pair, mode) - Specific streaming mode
 * - switchStreamingMode(newMode) - Change modes during operation
 * - All 6 ExchangeStreamSubscription factory methods
 * - Custom subscription with selective flags
 */
@Getter
@Setter
public class SmartBotUsageExamples {

    private static final Logger logger = LoggerFactory.getLogger(SmartBotUsageExamples.class);

    /**
     * Example 1: Start streaming with default mode (all 9 subscription flags).
     * This is the most common use case.
     */
    public static void example1_DefaultStreaming(@NotNull SmartBot bot, TradePair btcUsdt) {
        logger.info("Example 1: Default streaming (EVERYTHING mode)");

        // Start streaming with all data: ticker, trades, candles, orderBook, account, orders, fills, positions, balances
        SmartBot.StreamingMode mode=SmartBot.StreamingMode.EVERYTHING;
        bot.startStreaming(btcUsdt, mode);

        // Bot is now streaming all market and account data for BTC/USDT
        logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 2: Stream only market data (price analysis).
     * Useful when you don't need account changes.
     */
    public static void example2_MarketDataOnly(SmartBot bot, TradePair btcUsdt) {
        logger.info("Example 2: Market data only mode");

        // Stream only: ticker, trades, candles, orderBook (no account data)
        bot.startStreaming(btcUsdt, SmartBot.StreamingMode.MARKET_DATA);

        if (logger.isInfoEnabled()) logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 3: Stream only account data (portfolio monitoring).
     * Useful when you don't need price data.
     */
    public static void example3_AccountDataOnly(@NotNull SmartBot bot) {
        logger.info("Example 3: Account data only mode");

        // Stream only account-level data: account, orders, fills, positions, balances
        // No trade pair required for account data

        logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 4: Stream only ticker data (lightweight price updates).
     * Most minimal subscription.
     */
    public static void example4_TickerOnly(@NotNull SmartBot bot, TradePair ethUsdt) {
        logger.info("Example 4: Ticker only mode");

        // Stream only ticker for lightweight price monitoring
        bot.startStreaming(ethUsdt, SmartBot.StreamingMode.TICKER_ONLY);

        if (logger.isInfoEnabled()) {
            logger.info("Streaming: {}", bot.getSubscriptionSummary());
        }
    }

    /**
     * Example 5: Stream only trade executions.
     * Useful for analyzing real-time executions without order book.
     */
    public static void example5_TradesOnly(SmartBot bot, TradePair tradePair) {
        logger.info("Example 5: Trades only mode");

        bot.startStreaming(tradePair, SmartBot.StreamingMode.TRADES_ONLY);

        logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 6: Stream only order book data.
     * Useful for depth analysis and market microstructure.
     */
    public static void example6_OrderBookOnly(SmartBot bot, TradePair tradePair) {
        logger.info("Example 6: Order book only mode");

        bot.startStreaming(tradePair, SmartBot.StreamingMode.ORDER_BOOK_ONLY);

        logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 7: Switch streaming modes on the fly.
     * Useful for adapting to different market conditions.
     */
    public static void example7_SwitchModes(SmartBot bot, TradePair btcUsdt) {
        logger.info("Example 7: Switching streaming modes");

        // Start with everything
        bot.startStreaming(btcUsdt, SmartBot.StreamingMode.EVERYTHING);
        logger.info("Mode 1: {}", bot.getSubscriptionSummary());

        // Switch to market data only (when you want to reduce network traffic)
        bot.switchStreamingMode(SmartBot.StreamingMode.MARKET_DATA);
        logger.info("Mode 2 (switched): {}", bot.getSubscriptionSummary());

        // Switch to account data only (when price becomes less important)
        bot.switchStreamingMode(SmartBot.StreamingMode.ACCOUNT_DATA);
        logger.info("Mode 3 (switched): {}", bot.getSubscriptionSummary());

        // Switch back to everything
        bot.switchStreamingMode(SmartBot.StreamingMode.EVERYTHING);
        logger.info("Mode 4 (switched back): {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 8: Custom subscription with fine-grained control.
     * Useful when standard modes don't fit your exact needs.
     */
    public static void example8_CustomSubscription(@NotNull SmartBot bot, TradePair tradePair) {
        logger.info("Example 8: Custom subscription");

        // Start with custom mode
        bot.startStreaming(tradePair, SmartBot.StreamingMode.CUSTOM);

        // Fine-tune the subscription with specific flags
        // Let's say we want: ticker, trades, and orders only
        bot.customizeSubscription(
            true,   // ticker
            true,   // trades
            false,  // candles (not needed)
            false,  // orderBook (not needed)
            false,  // account (not needed)
            true,   // orders (important for execution tracking)
            false,  // fills (not needed)
            false,  // positions (not needed)
            false   // balances (not needed)
        );

        logger.info("Streaming: {}", bot.getSubscriptionSummary());
    }

    /**
     * Example 9: List all available streaming modes with descriptions.
     * Useful for UI components or dynamic mode selection.
     */
    public static void example9_ListAvailableModes(SmartBot bot) {
        logger.info("Example 9: Available streaming modes");

        SmartBot.StreamingMode[] modes = bot.getAvailableStreamingModes();

        for (SmartBot.StreamingMode mode : modes) {
            logger.info("Mode: {} - {}", mode.name(), mode.description);
        }
    }

    /**
     * Example 10: Comprehensive streaming strategy based on market conditions.
     * Shows adaptive streaming based on different scenarios.
     */
    public static void example10_AdaptiveStreaming(SmartBot bot, TradePair tradePair) {
        logger.info("Example 10: Adaptive streaming strategy");

        // Scenario 1: Pre-market analysis (market data only)
        logger.info("Scenario 1: Pre-market analysis");
        bot.startStreaming(tradePair, SmartBot.StreamingMode.MARKET_DATA);
        logger.info("Market analysis mode active");

        // Scenario 2: Trading started (everything enabled)
        logger.info("Scenario 2: Trading active");
        bot.switchStreamingMode(SmartBot.StreamingMode.EVERYTHING);
        logger.info("Full streaming mode active");

        // Scenario 3: High volatility detected (ticker + orders only)
        logger.info("Scenario 3: High volatility - reducing noise");
        bot.startStreaming(tradePair, SmartBot.StreamingMode.CUSTOM);
        bot.customizeSubscription(
            true,   // ticker (price updates critical)
            false,  // trades (too noisy)
            false,  // candles (not needed)
            false,  // orderBook (not needed)
            false,  // account (not critical)
            true,   // orders (execution tracking)
            true,   // fills (fill tracking)
            true,   // positions (position monitoring)
            false   // balances (not needed)
        );
        logger.info("High-volatility mode active");

        // Scenario 4: Portfolio monitoring only (account data only)
        logger.info("Scenario 4: Post-trade monitoring");
        bot.switchStreamingMode(SmartBot.StreamingMode.ACCOUNT_DATA);
        logger.info("Account monitoring mode active");
    }

    /**
     * Example 11: Direct usage of all ExchangeStreamSubscription factory methods.
     * Shows the underlying subscription patterns.
     */
    public static void example11_SubscriptionFactoryMethods(TradePair tradePair) {
        logger.info("Example 11: All ExchangeStreamSubscription factory methods");

        // Factory Method 1: Everything
        logger.info("1. everything() - All 9 flags");
        ExchangeStreamSubscription sub1 = ExchangeStreamSubscription.everything(
            java.util.Set.of(tradePair)
        );
        logSubscriptionFlags(sub1);

        // Factory Method 2: Market Data
        logger.info("2. marketData() - Ticker, Trades, Candles, OrderBook");
        ExchangeStreamSubscription sub2 = ExchangeStreamSubscription.marketData(
            java.util.Set.of(tradePair)
        );
        logSubscriptionFlags(sub2);

        // Factory Method 3: Account Data
        logger.info("3. accountData() - Account, Orders, Fills, Positions, Balances");
        ExchangeStreamSubscription sub3 = ExchangeStreamSubscription.accountData();
        logSubscriptionFlags(sub3);

        // Factory Method 4: Ticker Only
        logger.info("4. forTicker() - Ticker only");
        ExchangeStreamSubscription sub4 = ExchangeStreamSubscription.forTicker(
            java.util.Set.of(tradePair)
        );
        logSubscriptionFlags(sub4);

        // Factory Method 5: Trades Only
        logger.info("5. forTrades() - Trades only");
        ExchangeStreamSubscription sub5 = ExchangeStreamSubscription.forTrades(
            java.util.Set.of(tradePair)
        );
        logSubscriptionFlags(sub5);

        // Factory Method 6: Order Book Only
        logger.info("6. forOrderBook() - Order book only");
        ExchangeStreamSubscription sub6 = ExchangeStreamSubscription.forOrderBook(
            java.util.Set.of(tradePair)
        );
        logSubscriptionFlags(sub6);
    }

    /**
     * Helper method to log subscription flags.
     */
    private static void logSubscriptionFlags(ExchangeStreamSubscription sub) {
        StringBuilder flags = new StringBuilder("Flags: ");
        if (sub.isTicker()) flags.append("ticker ");
        if (sub.isTrades()) flags.append("trades ");
        if (sub.isCandles()) flags.append("candles ");
        if (sub.isOrderBook()) flags.append("orderBook ");
        if (sub.isAccount()) flags.append("account ");
        if (sub.isOrders()) flags.append("orders ");
        if (sub.isFills()) flags.append("fills ");
        if (sub.isPositions()) flags.append("positions ");
        if (sub.isBalances()) flags.append("balances");
        logger.info(flags.toString());
    }

    /**
     * Main method demonstrating all examples.
     */
    static void main(String[] args) throws SQLException, ClassNotFoundException, InvalidPropertiesFormatException {
        logger.info("=== SmartBot Streaming Methods - All Examples ===");

        // Initialize SmartBot
        SmartBot bot = new SmartBot();

        // Example trade pairs
        TradePair btcUsdt = new TradePair("BTC", "USDT");
        TradePair ethUsdt = new TradePair("ETH", "USDT");

        // Get API credentials from environment variables
        String apiKey = System.getenv("COINBASE_API_KEY");
        String apiSecret = System.getenv("COINBASE_API_SECRET");

        if (apiKey == null || apiSecret == null) {
            logger.error("COINBASE_API_KEY and COINBASE_API_SECRET environment variables must be set");
            return;
        }

        // Initialize exchange
        Exchange exchange = new Coinbase(apiKey, apiSecret);

        // Initialize trade service
        TradeRepository tradeRepository = new TradeRepositoryImpl();
        TradeService tradeService = new TradeService(tradeRepository);

        // Initialize order service
        Properties config = new Properties();
        config.setProperty("db_name", "InvestPro.sql");
        Db1 db = new Db1(config);
        OrderRepository orderRepository = new OrderRepositoryImpl(db);
        OrderService orderService = new OrderService(orderRepository);

        // Initialize currency service
        CurrencyRepository currencyRepository = new CurrencyRepositoryImpl(db);
        CurrencyService currencyService = new CurrencyService(currencyRepository);

        // Create TradingService
        TradingService tradingService = new TradingService(tradeService, orderService, currencyService);

        // Get Telegram token from environment variable (optional)
        String telegramToken = System.getenv("TELEGRAM_BOT_TOKEN");
        if (telegramToken == null) {
            telegramToken = "";  // Empty string if not provided
        }

        // Start the bot
        bot.start(exchange, tradingService, btcUsdt, telegramToken);

        try {
            // Run all examples
            logger.info("\n=== Running Examples ===\n");

            logger.info("--- Example 1: Default Streaming ---");
            example1_DefaultStreaming(bot, btcUsdt);

            logger.info("\n--- Example 2: Market Data Only ---");
            example2_MarketDataOnly(bot, btcUsdt);

            logger.info("\n--- Example 3: Account Data Only ---");
            example3_AccountDataOnly(bot);

            logger.info("\n--- Example 4: Ticker Only ---");
            example4_TickerOnly(bot, ethUsdt);

            logger.info("\n--- Example 5: Trades Only ---");
            example5_TradesOnly(bot, btcUsdt);

            logger.info("\n--- Example 6: Order Book Only ---");
            example6_OrderBookOnly(bot, btcUsdt);

            logger.info("\n--- Example 7: Switch Modes ---");
            example7_SwitchModes(bot, btcUsdt);

            logger.info("\n--- Example 8: Custom Subscription ---");
            example8_CustomSubscription(bot, btcUsdt);

            logger.info("\n--- Example 9: List Available Modes ---");
            example9_ListAvailableModes(bot);

            logger.info("\n--- Example 10: Adaptive Streaming ---");
            example10_AdaptiveStreaming(bot, btcUsdt);

            logger.info("\n--- Example 11: Factory Methods ---");
            example11_SubscriptionFactoryMethods(btcUsdt);

        } finally {
            // Cleanup
            bot.stop();
            logger.info("\nSmartBot stopped. All examples completed.");
        }

        logger.info("\n=== Available SmartBot Methods ===");
        logger.info("1. startStreaming(pair) - Default EVERYTHING mode");
        logger.info("2. startStreaming(pair, mode) - Specific mode");
        logger.info("3. switchStreamingMode(mode) - Change modes live");
        logger.info("4. customizeSubscription(...) - Fine-grained control");
        logger.info("5. getAvailableStreamingModes() - List all modes");
        logger.info("6. getStreamingModeDescription(mode) - Mode info");
        logger.info("7. getSubscriptionSummary() - Current subscription");
        logger.info("\n=== Available Streaming Modes ===");
        logger.info("EVERYTHING, MARKET_DATA, ACCOUNT_DATA, TICKER_ONLY, TRADES_ONLY, ORDER_BOOK_ONLY, CUSTOM");
    }
}
