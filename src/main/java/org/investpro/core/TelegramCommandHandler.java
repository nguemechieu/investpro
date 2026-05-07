package org.investpro.core;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Telegram Command Handler for executing trading commands from Telegram.
 * <p>
 * Supported Commands:
 * /start - Start the bot
 * /help - Show available commands
 * /status - Show bot and market status
 * /balance - Show account balance
 * /positions - Show open positions
 * /orders - Show active orders
 * /orderinfo ORDERID - Get detailed order information
 * /trades - Show trade history
 * /buy SYMBOL AMOUNT [PRICE] - Execute a buy order
 * /sell SYMBOL AMOUNT [PRICE] - Execute a sell order
 * /cancel ORDERID - Cancel an order
 * /strategy - Show active strategy
 * /toggleauto - Toggle auto trading
 * /risk - Show risk metrics
 * /pnl - Show profit/loss
 * /market SYMBOL - Get market info for a symbol
 * /analysis SYMBOL - Get detailed market analysis
 * /news - Get market news
 * /top - Show top gainers/losers
 */
@Slf4j
public record TelegramCommandHandler(SystemCore systemCore, TelegramNotifier telegramNotifier) {
    /**
     * Process a Telegram command and return response text
     */
    public String handleCommand(String command, String chatId) {
        if (command == null || command.isBlank()) {
            return "❌ No command provided";
        }

        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase().replace("/", "");

        try {
            return switch (cmd) {
                case "start" -> handleStart();
                case "help" -> handleHelp();
                case "status" -> handleStatus();
                case "health" -> handleSystemHealth();
                case "diagnostics" -> handleSystemDiagnostics();
                case "balance" -> handleBalance();
                case "positions" -> handlePositions();
                case "orders" -> handleOrders();
                case "orderinfo" -> handleOrderInfo(parts);
                case "trades" -> handleTrades();
                case "buy" -> handleBuyOrder(parts);
                case "sell" -> handleSellOrder(parts);
                case "cancel" -> handleCancelOrder(parts);
                case "strategy" -> handleStrategyInfo();
                case "toggleauto" -> handleToggleAutoTrading();
                case "risk" -> handleRiskMetrics();
                case "pnl" -> handleProfitLoss();
                case "market" -> handleMarketInfo(parts);
                case "analysis" -> handleMarketAnalysis(parts);
                case "news" -> handleMarketNews();
                case "top" -> handleTopMovers();
                default -> "❌ Unknown command: /" + cmd + "\nUse /help to see available commands";
            };
        } catch (Exception e) {
            log.error("Error handling Telegram command: {}", command, e);
            return "❌ Error executing command: " + e.getMessage();
        }
    }

    private String handleStart() {
        if (systemCore == null || !systemCore.isReady()) {
            return "⚠️ Bot is initializing. Please wait...";
        }
        return """
                👋 *InvestPro Trading Bot Started*

                Connected Exchange: %s
                Status: 🟢 Online

                Type /help to see available commands.
                Type /balance to check your account.
                """.formatted(systemCore.getExchange().getDisplayName());
    }

    private String handleHelp() {
        return """
                📚 *Available Commands*

                *Account & Status*
                /balance - Show account balance
                /positions - List open positions
                /orders - Show active orders
                /orderinfo ID - Get details for specific order
                /trades - Show trade history
                /pnl - Show profit/loss
                /risk - Show risk metrics
                /status - Show bot status

                *Market Information*
                /market SYMBOL - Get market info (ex: /market BTC)
                /analysis SYMBOL - Get detailed analysis (ex: /analysis ETH)
                /news - Show market news and headlines
                /top - Show top gainers and losers

                *Trading Commands*
                /buy SYMBOL AMOUNT [PRICE] - Buy (ex: /buy BTC 0.5)
                /sell SYMBOL AMOUNT [PRICE] - Sell (ex: /sell BTC 0.5)
                /cancel ORDERID - Cancel order

                *Bot Control*
                /start - Initialize the bot
                /strategy - Show active strategy
                /toggleauto - Toggle auto trading
                /help - Show this message

                💡 *Tip:* Ask any trading questions and the bot will respond!
                Ex: "What's the BTC trend?" or "How profitable was today?"
                """;
    }

    private String handleStatus() {
        if (systemCore == null || !systemCore.isReady()) {
            return "🔴 *Bot Status*: Offline";
        }

        Exchange exchange = systemCore.getExchange();
        boolean autoTradingEnabled = systemCore.isAutoTradingEnabled();
        boolean aiReasoningEnabled = systemCore.isAiReasoningEnabled();

        return """
                🟢 *Bot Status*: Online

                *Exchange:* %s
                *Auto Trading:* %s
                *AI Reasoning:* %s
                *Streaming:* %s

                Use /balance to check account balance
                """.formatted(
                exchange.getDisplayName(),
                autoTradingEnabled ? "✅ Enabled" : "❌ Disabled",
                aiReasoningEnabled ? "✅ Enabled" : "❌ Disabled",
                systemCore.isStreaming() ? "🔴 Active" : "⚪ Inactive");
    }

    private String handleSystemHealth() {
        if (systemCore == null) {
            return "❌ System Core not available";
        }
        return systemCore.getSystemHealthSummary();
    }

    private String handleSystemDiagnostics() {
        if (systemCore == null) {
            return "❌ System Core not available";
        }
        return systemCore.getSystemDiagnostics();
    }

    private String handleBalance() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            Exchange exchange = systemCore.getExchange();
            Map<String, Double> balances = exchange.getAccount().balancesView();

            // Calculate total balance
            double totalBalance = balances != null ? balances.values().stream().mapToDouble(Double::doubleValue).sum()
                    : 0;

            return String.format(
                    "💰 *Account Balance*\n\nCurrency: USD\nBalance: $%.2f",
                    totalBalance);
        } catch (Exception e) {
            log.debug("Error fetching balance", e);
            return "⚠️ Could not fetch balance: " + e.getMessage();
        }
    }

    private String handlePositions() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            // Get positions - placeholder since getPositions might not exist
            // This would be implemented when Exchange API is available
            return "📊 *Open Positions*\n\n📭 No open positions\n\nPositions data coming soon...";
        } catch (Exception e) {
            log.debug("Error fetching positions", e);
            return "⚠️ Could not fetch positions: " + e.getMessage();
        }
    }

    private String handleOrders() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            // Placeholder - getOrders may not exist on Exchange
            return "📋 *Active Orders*\n\n📭 No active orders";
        } catch (Exception e) {
            log.debug("Error fetching orders", e);
            return "⚠️ Could not fetch orders: " + e.getMessage();
        }
    }

    /**
     * Handle /orderinfo command to get detailed order information
     */
    private String handleOrderInfo(@NotNull String[] parts) {
        if (parts.length < 2) {
            return "❌ Usage: /orderinfo ORDERID";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String orderId = parts[1];
            String orderDetails = systemCore.getOrderDetails(orderId);

            if (orderDetails == null || orderDetails.isEmpty()) {
                return "❌ Order not found: " + orderId;
            }

            return "📋 *Order Details*\n\n" + orderDetails;
        } catch (Exception e) {
            log.debug("Error getting order info", e);
            return "⚠️ Could not fetch order details: " + e.getMessage();
        }
    }

    private String handleBuyOrder(@NotNull String[] parts) {
        if (parts.length < 3) {
            return "❌ Usage: /buy SYMBOL AMOUNT [PRICE]\nExample: /buy BTC 0.5 or /buy BTC 0.5 45000";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String symbol = parts[1].toUpperCase();
            double amount = Double.parseDouble(parts[2]);
            Double priceLimit = parts.length > 3 ? Double.parseDouble(parts[3]) : null;

            // Validate amount
            if (amount <= 0) {
                return "❌ Amount must be positive";
            }

            // Execute buy order
            boolean orderType = priceLimit != null; // true = LIMIT, false = MARKET
            String response = systemCore.placeBuyOrder(symbol, amount, priceLimit);

            return "✅ *Buy Order Submitted*\n\n" +
                    "Symbol: " + symbol + "\n" +
                    "Amount: " + amount + "\n" +
                    "Type: " + (orderType ? "LIMIT @ $" + priceLimit : "MARKET") + "\n\n" +
                    response;
        } catch (NumberFormatException e) {
            return "❌ Invalid amount or price format";
        } catch (Exception e) {
            log.error("Error executing buy order", e);
            return "❌ Failed to place buy order: " + e.getMessage();
        }
    }

    private String handleSellOrder(@NotNull String[] parts) {
        if (parts.length < 3) {
            return "❌ Usage: /sell SYMBOL AMOUNT [PRICE]\nExample: /sell BTC 0.5";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String symbol = parts[1].toUpperCase();
            double amount = Double.parseDouble(parts[2]);
            Double priceLimit = parts.length > 3 ? Double.parseDouble(parts[3]) : null;

            if (amount <= 0) {
                return "❌ Amount must be positive";
            }

            // Execute sell order
            boolean orderType = priceLimit != null;
            String response = systemCore.placeSellOrder(symbol, amount, priceLimit);

            return "✅ *Sell Order Submitted*\n\n" +
                    "Symbol: " + symbol + "\n" +
                    "Amount: " + amount + "\n" +
                    "Type: " + (orderType ? "LIMIT @ $" + priceLimit : "MARKET") + "\n\n" +
                    response;
        } catch (NumberFormatException e) {
            return "❌ Invalid amount or price format";
        } catch (Exception e) {
            log.error("Error executing sell order", e);
            return "❌ Failed to place sell order: " + e.getMessage();
        }
    }

    private String handleCancelOrder(@NotNull String[] parts) {
        if (parts.length < 2) {
            return "❌ Usage: /cancel ORDERID";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String orderId = parts[1];
            // Placeholder for order cancellation
            return "✅ *Order Cancelled*\n\nOrder ID: " + orderId;
        } catch (Exception e) {
            log.error("Error cancelling order", e);
            return "❌ Failed to cancel order: " + e.getMessage();
        }
    }

    private String handleStrategyInfo() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String strategyName = systemCore.getActiveStrategyName();
            String strategyStats = systemCore.getStrategyStats();

            return "📈 *Active Strategy*\n\n" +
                    "Strategy: " + (strategyName != null ? strategyName : "None") + "\n\n" +
                    (strategyStats != null ? strategyStats : "No statistics available");
        } catch (Exception e) {
            log.debug("Error getting strategy info", e);
            return "⚠️ Could not fetch strategy info: " + e.getMessage();
        }
    }

    private String handleToggleAutoTrading() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            boolean currentState = systemCore.isAutoTradingEnabled();
            systemCore.setAutoTradingEnabled(!currentState);

            return "✅ *Auto Trading " + (!currentState ? "Enabled" : "Disabled") + "*\n\n" +
                    "Auto trading is now " + (!currentState ? "🟢 ON" : "🔴 OFF");
        } catch (Exception e) {
            log.error("Error toggling auto trading", e);
            return "❌ Failed to toggle auto trading: " + e.getMessage();
        }
    }

    private String handleRiskMetrics() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String riskMetrics = systemCore.getRiskMetrics();
            return "⚠️ *Risk Metrics*\n\n" +
                    (riskMetrics != null ? riskMetrics : "No risk metrics available");
        } catch (Exception e) {
            log.debug("Error getting risk metrics", e);
            return "⚠️ Could not fetch risk metrics: " + e.getMessage();
        }
    }

    private String handleProfitLoss() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            double totalPnL = systemCore.calculateTotalPnL();
            double percentReturn = systemCore.calculateReturnPercentage();

            String pnlStatus = totalPnL >= 0 ? "📈 Profit" : "📉 Loss";
            String arrow = totalPnL >= 0 ? "📈" : "📉";

            return arrow + " *" + pnlStatus + "*\n\n" +
                    "Total P&L: $" + String.format("%.2f", totalPnL) + "\n" +
                    "Return: " + String.format("%.2f%%", percentReturn);
        } catch (Exception e) {
            log.debug("Error calculating P&L", e);
            return "⚠️ Could not calculate P&L: " + e.getMessage();
        }
    }

    private String handleMarketInfo(@NotNull String[] parts) {
        if (parts.length < 2) {
            return "❌ Usage: /market SYMBOL\nExample: /market BTC";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String symbol = parts[1].toUpperCase();
            TradePair tradePair = systemCore.getTradePair(symbol);

            if (tradePair == null) {
                return "❌ Symbol not found: " + symbol;
            }

            double bid = tradePair.getBid();
            double ask = tradePair.getAsk();
            double spread = ask - bid;
            double spreadPct = (spread / bid) * 100;

            return """
                    📊 *Market Info - %s*

                    Bid: $%.2f
                    Ask: $%.2f
                    Spread: $%.2f (%.4f%%)
                    24h Volume: %.2f

                    💡 Tip: Use /analysis %s for detailed market analysis,%s
                    """.formatted(
                    symbol,
                    bid,
                    ask,
                    spread,
                    spreadPct,
                    tradePair.getVolume());
        } catch (Exception e) {
            log.debug("Error getting market info", e);
            return "⚠️ Could not fetch market info: " + e.getMessage();
        }
    }

    private String handleTrades() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String tradeHistory = systemCore.getRecentTrades(10);

            if (tradeHistory == null || tradeHistory.isEmpty()) {
                return "📭 No recent trades";
            }

            return "📈 *Recent Trades*\n\n" + tradeHistory;
        } catch (Exception e) {
            log.debug("Error getting trades", e);
            return "⚠️ Could not fetch trade history: " + e.getMessage();
        }
    }

    /**
     * Handle /analysis command for detailed market analysis
     */
    private String handleMarketAnalysis(@NotNull String[] parts) {
        if (parts.length < 2) {
            return "❌ Usage: /analysis SYMBOL\nExample: /analysis BTC";
        }

        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String symbol = parts[1].toUpperCase();
            TradePair tradePair = systemCore.getTradePair(symbol);

            if (tradePair == null) {
                return "❌ Symbol not found: " + symbol;
            }

            double bid = tradePair.getBid();
            double ask = tradePair.getAsk();
            double volume = tradePair.getVolume();
            double spread = ask - bid;
            double spreadPct = (spread / bid) * 100;

            return """
                    🔍 *Detailed Market Analysis - %s*

                    💹 *Price*
                    Bid: $%.2f
                    Ask: $%.2f
                    Spread: $%.2f (%.4f%%)

                    📊 *Volume*
                    24h Volume: %.2f
                    Volume Trend: %s

                    📈 *Technical Indicators*
                    Moving Average: $%.2f
                    RSI: %d
                    MACD: %s

                    💡 *Recommendation*
                    %s
                    """.formatted(
                    symbol,
                    bid,
                    ask,
                    spread,
                    spreadPct,
                    volume,
                    systemCore.getVolumeTrend(symbol),
                    systemCore.getMovingAverage(symbol),
                    systemCore.getRSI(symbol),
                    systemCore.getMACD(symbol),
                    systemCore.getTradeRecommendation(symbol));
        } catch (Exception e) {
            log.debug("Error getting market analysis", e);
            return "⚠️ Could not fetch analysis: " + e.getMessage();
        }
    }

    /**
     * Handle /news command to show market news
     */
    private String handleMarketNews() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String news = systemCore.getLatestNews();

            if (news == null || news.isEmpty()) {
                return "📭 No news available";
            }

            return "📰 *Market News*\n\n" + news;
        } catch (Exception e) {
            log.debug("Error getting news", e);
            return "⚠️ Could not fetch news: " + e.getMessage();
        }
    }

    /**
     * Handle /top command to show top gainers and losers
     */
    private String handleTopMovers() {
        if (systemCore == null || !systemCore.isReady()) {
            return "❌ Bot is not ready";
        }

        try {
            String topGainers = systemCore.getTopGainers(5);
            String topLosers = systemCore.getTopLosers(5);

            return "📊 *Market Movers - Top 5*\n\n" +
                    "🟢 *Top Gainers*\n" + topGainers + "\n" +
                    "🔴 *Top Losers*\n" + topLosers;
        } catch (Exception e) {
            log.debug("Error getting top movers", e);
            return "⚠️ Could not fetch market movers: " + e.getMessage();
        }
    }
}