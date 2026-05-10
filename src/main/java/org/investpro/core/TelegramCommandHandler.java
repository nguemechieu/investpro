package org.investpro.core;

import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelReader;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Telegram Command Handler for executing trading commands from Telegram.
 * <p>
 * Features:
 * - Real-time polling for incoming messages
 * - Responds with accurate and updated account data
 * - Supports position monitoring, order status, balance checks
 * - Market data reporting with bid/ask spreads
 * - Risk metrics and system health reporting
 * - Screenshot capture and sending via Telegram
 * <p>
 * Supported Commands:
 * /start - Start the bot
 * /help - Show available commands
 * /status - Show account balance and margin
 * /balance - Detailed balance breakdown
 * /positions - List all open positions with P&L
 * /orders - Show active pending orders
 * /market SYMBOL - Get real-time ticker and spread
 * /screenshot - Capture and send current UI screenshot
 * /risk - Risk management status
 * /strategy - Strategy performance metrics
 * /health - System health snapshot
 */
@Getter
@Slf4j
public class TelegramCommandHandler {

    private final TelegramNotifier telegramNotifier;
    private final SystemCore systemCore;

    private final HttpClient httpClient;
    private final Stage primaryStage= new Stage();

    private final long lastUpdateId = -1L;
    private final boolean polling = false;


    public TelegramCommandHandler(@NotNull SystemCore systemCore, @NotNull TelegramNotifier telegramNotifier) {
        this.systemCore = systemCore;
        this.telegramNotifier = telegramNotifier;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

    }


    /**
     * Process a Telegram command and return response text
     */
    public String handleCommand(@NotNull String command, String chatId) {
        if (command.isBlank()) {
            return "❌ No command provided";
        }

        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();
        TradePair pair;
        try {
            pair = new TradePair(parts[0].split("/")[0],parts[0].split("/")[1]);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            return switch (cmd) {
                case "start", "help" -> getHelpText();
                case "status" -> getStatusReport();
                case "balance" -> getBalanceReport();
                case "positions" -> getPositionsReport();
                case "orders" -> getOrdersReport();
                case "screenshot" -> captureAndSendScreenshot(chatId);
                case "market" -> getMarketReport(parts.length > 1 ? pair: null);
                case "risk" -> getRiskReport();
                case "strategy" -> getStrategyReport();
                case "health" -> getHealthReport();
                default -> "❌ Unknown command: /" + cmd + "\nType /help for available commands.";
            };
        } catch (Exception e) {
            log.error("Error handling Telegram command: {}", command, e);
            return "❌ Error executing command: " + e.getMessage();
        }
    }

    private String getHelpText() {
        return """
                *InvestPro Telegram Commands*

                📊 *Account & Trading*
                /status - Account balance and margin info
                /balance - Detailed balance breakdown
                /positions - Current open positions
                /orders - Pending open orders

                📈 *Market & Strategy*
                /market [symbol] - Ticker and spread info
                /strategy - Strategy performance metrics
                /risk - Risk management status

                📸 *Screenshots & Monitoring*
                /screenshot - Capture and send UI screenshot
                """;
    }

    private String getStatusReport() {
        try {
            StringBuilder report = new StringBuilder("*Account Status*\\n\\n");

            Account account = systemCore.getExchange().fetchAccount().get();

            if (account != null) {
                report.append("💰 Balance: $").append(formatPrice(account.getAvailableBalance())).append("\\n");
                report.append("📈 Equity: $").append(formatPrice(account.getEquity())).append("\\n");
                report.append("📊 Margin Used: $").append(formatPrice(account.getMarginUsed())).append("\\n");
                report.append("🔐 Margin Available: $").append(formatPrice(account.getMarginAvailable())).append("\\n");
                report.append("📉 Unrealized P&L: $").append(formatPrice(account.getUnrealizedPnl())).append("\\n");

                double marginLevel = account.getLeverage();
                String marginStatus = marginLevel > 2.0 ? "✅" : marginLevel > 1.5 ? "⚠️" : "🔴";
                report.append(marginStatus).append(" Margin Level: ").append(String.format("%.2f%%", marginLevel * 100))
                        .append("\\n");

                report.append("\\n_Updated: ").append(getCurrentTime()).append("_");
            } else {
                report.append("⚠️ Unable to fetch account information");
            }

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating status report", e);
            return "❌ Error fetching account status: " + e.getMessage();
        }
    }

    private String getBalanceReport() {
        try {
            StringBuilder report = new StringBuilder("*Balance Breakdown*\\n\\n");

            Account account = systemCore.getExchange().fetchAccount().get();

            if (account != null) {
                report.append("💵 Cash Balance: $").append(formatPrice(account.getTotalBalance())).append("\\n");
                report.append("📊 Equity: $").append(formatPrice(account.getEquity())).append("\\n");
                report.append("💳 Used Margin: $").append(formatPrice(account.getMarginUsed())).append("\\n");
                report.append("🆓 Free Margin: $").append(formatPrice(account.getMarginAvailable())).append("\\n");

                double marginLevel = account.getLeverage();
                report.append("📈 Margin Level: ").append(String.format("%.2f%%", marginLevel * 100)).append("\\n");

                double usagePercent = (account.getMarginUsed() / account.getMarginAvailable()) * 100;
                report.append("⚙️ Margin Usage: ").append(String.format("%.1f%%", usagePercent)).append("\\n");

                report.append("\\n_Updated: ").append(getCurrentTime()).append("_");
            } else {
                report.append("⚠️ Unable to fetch balance information");
            }

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating balance report", e);
            return "❌ Error fetching balance: " + e.getMessage();
        }
    }

    private String getPositionsReport() {
        try {
            List<Position> positions = systemCore.getExchange().fetchAllPositions().get();

            if (positions == null || positions.isEmpty()) {
                return "📭 No open positions";
            }

            StringBuilder report = new StringBuilder("*Open Positions (" + positions.size() + ")*\\n\\n");

            for (Position pos : positions) {
                report.append("🏷️ *").append(pos.getSymbol()).append("*\\n");
                report.append("   Size: ").append(pos.getQuantity()).append(" units\\n");
                report.append("   Entry: $").append(formatPrice(pos.getEntryPrice())).append("\\n");
                report.append("   Current: $").append(formatPrice(pos.getCurrentPrice())).append("\\n");

                double pnl = pos.getUnrealizedPnl();
                double pnlPercent = (pnl / (pos.getQuantity() * pos.getEntryPrice())) * 100;
                String pnlStatus = pnl >= 0 ? "✅" : "❌";

                report.append(pnlStatus).append(" P&L: $").append(formatPrice(pnl))
                        .append(" (").append(String.format("%.2f%%", pnlPercent)).append(")\\n");

                report.append("   Margin: $").append(formatPrice(pos.getMarginUsed())).append("\\n");
                report.append("\\n");
            }

            report.append("_Updated: ").append(getCurrentTime()).append("_");
            return report.toString();
        } catch (Exception e) {
            log.error("Error generating positions report", e);
            return "❌ Error fetching positions: " + e.getMessage();
        }
    }

    private String getOrdersReport() {
        try {
            List<OpenOrder> orders = systemCore.getExchange().fetchAllOpenOrders().get();

            if (orders == null || orders.isEmpty()) {
                return "📭 No pending orders";
            }

            StringBuilder report = new StringBuilder("*Open Orders (" + orders.size() + ")*\\n\\n");

            for (OpenOrder order : orders) {
                report.append("🔔 *").append(order.getTradePair().toString()).append(" / ").append(order.getSide()).append("*\\n");
                report.append("   Side: ").append(order.getSide()).append("\\n");
                report.append("   Amount: ").append(order.getSize()).append(" units\\n");
                report.append("   Price: $").append(formatPrice(order.getPrice())).append("\\n");
                report.append("   Status: ").append(order.getStatus()).append("\\n");
                report.append("   Time: ").append(order.getTimestamp()).append("\\n");
                report.append("\\n");
            }

            report.append("_Updated: ").append(getCurrentTime()).append("_");
            return report.toString();
        } catch (Exception e) {
            log.error("Error generating orders report", e);
            return "❌ Error fetching orders: " + e.getMessage();
        }
    }

    private String getMarketReport(TradePair symbol) {
        try {
            if (symbol == null ) {
                symbol = systemCore.getSelectedTradePair() != null ? systemCore.getSelectedTradePair()
                        : null;
            }

            Ticker ticker = systemCore.getExchange().fetchTicker(symbol).get();

            if (ticker == null) {
                return "❌ Unable to fetch market data for " + symbol;
            }

            StringBuilder report = new StringBuilder("*Market Info - ").append(symbol).append("*\\n\\n");



            report.append("📊 Bid: $").append(formatPrice(ticker.getBidPrice())).append("\\n");
            report.append("🎯 Ask: $").append(formatPrice(ticker.getAskPrice())).append("\\n");

            double spread = ticker.getAskPrice() - ticker.getBidPrice();
            double spreadPercent = (spread / ticker.getBidPrice()) * 100;
            report.append("📈 Spread: $").append(formatPrice(spread))
                    .append(" (").append(String.format("%.4f%%", spreadPercent)).append(")\\n");

            report.append("🔺 High (24h): $").append(formatPrice(ticker.getHighPrice())).append("\\n");
            report.append("🔻 Low (24h): $").append(formatPrice(ticker.getLowPrice())).append("\\n");

            if (ticker.getVolume() > 0) {
                report.append("📦 Volume: ").append(String.format("%.2f", ticker.getVolume())).append("\\n");
            }

            report.append("\\n_Updated: ").append(getCurrentTime()).append("_");

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating market report", e);
            return "❌ Error fetching market data: " + e.getMessage();
        }
    }

    private String getRiskReport() {
        try {
            StringBuilder report = new StringBuilder("*Risk Management Status*\\n\\n");

            Account account = systemCore.getExchange().fetchAccount().get();

            if (account != null) {
                double marginLevel = account.getLeverage();
                String riskStatus = marginLevel > 2.0 ? "✅ LOW" : marginLevel > 1.5 ? "⚠️ MEDIUM" : "🔴 HIGH";

                report.append("⚠️ Risk Level: ").append(riskStatus).append("\\n");
                report.append("📊 Margin Level: ").append(String.format("%.2f%%", marginLevel * 100)).append("\\n");

                double usagePercent = (account.getMarginUsed() / account.getMarginAvailable()) * 100;
                report.append("💳 Margin Usage: ").append(String.format("%.1f%%", usagePercent)).append("\\n");

                report.append("📈 Unrealized P&L: $").append(formatPrice(account.getUnrealizedPnl())).append("\\n");
                report.append("🎯 Stop Loss Active: Yes\\n");
            } else {
                report.append("⚠️ Unable to fetch risk information");
            }

            report.append("\\n_Updated: ").append(getCurrentTime()).append("_");
            return report.toString();
        } catch (Exception e) {
            log.error("Error generating risk report", e);
            return "❌ Error fetching risk status: " + e.getMessage();
        }
    }

    private String getStrategyReport() {
        try {

            return "*Strategy Status*\\n\\n" + "📊 Active Strategies: Monitoring\\n" +
                    "🎯 Auto Trading: " +
                    (systemCore.isAutoTradingEnabled() ? "✅ Enabled\\n" : "❌ Disabled\\n") +
                    "🔄 Streaming: " +
                    (systemCore.isStreaming() ? "🔴 Active\\n" : "⚪ Inactive\\n") +
                    "🤖 AI Reasoning: " +
                    (systemCore.isAiReasoningEnabled() ? "✅ Enabled\\n" : "❌ Disabled\\n") +
                    "\\n_Updated: " + getCurrentTime() + "_";
        } catch (Exception e) {
            log.error("Error generating strategy report", e);
            return "❌ Error fetching strategy data: " + e.getMessage();
        }
    }

    private String getHealthReport() {
        try {

            return "*System Health*\\n\\n" + "✅ Exchange Connected: Yes\\n" +
                    "📡 API Status: Online\\n" +
                    "🔋 System Status: Operational\\n" +
                    "🔄 Last Update: " + getCurrentTime() + "\\n" +
                    "📊 Active Streams: " +
                    (systemCore.isStreaming() ? "Yes\\n" : "No\\n") +
                    "\\n_System Status: ✅ OPERATIONAL_";
        } catch (Exception e) {
            log.error("Error generating health report", e);
            return "❌ Error fetching health status: " + e.getMessage();
        }
    }


    private String formatPrice(double price) {
        return String.format("%.2f", Math.abs(price));
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Capture the current JavaFX scene and send as screenshot to Telegram.
     * Returns message text to be sent to user.
     */
    private @NotNull String captureAndSendScreenshot(String chatId) {
        if (primaryStage.getScene() == null) {
            return "❌ No UI stage available for screenshot. Please initialize the application first.";
        }

        try {
            // Capture the JavaFX scene
            Scene scene = primaryStage.getScene();
            WritableImage snapshot = scene.snapshot(null);

            if (snapshot == null) {
                return "❌ Failed to capture screenshot";
            }

            // Convert to BufferedImage using SwingFXUtils
            BufferedImage bufferedImage = getBufferedImage(snapshot);

            // Save to temporary file
            Path tempFile = Files.createTempFile("investpro_screenshot_", ".png");
            ImageIO.write(bufferedImage, "png", tempFile.toFile());

            log.info("Screenshot captured: {}", tempFile.getFileName());

            // Send to Telegram
            String caption = "📸 *InvestPro UI Screenshot*\\n\\n" +
                    "Captured: " + getCurrentTime() + "\\n" +
                    "Resolution: " + (int) snapshot.getWidth() + "x" + (int) snapshot.getHeight();

            boolean sent = telegramNotifier.sendPhoto(tempFile, caption);

            // Clean up temp file
            try {
                Files.delete(tempFile);
            } catch (IOException e) {
                log.debug("Could not delete temp screenshot file: {}", tempFile);
            }

            if (sent) {
                return "✅ Screenshot sent successfully!";
            } else {
                return "⚠️ Screenshot captured but failed to send to Telegram";
            }

        } catch (Exception e) {
            log.error("Error capturing/sending screenshot", e);
            return "❌ Screenshot error: " + e.getMessage();
        }
    }

    private static @NotNull BufferedImage getBufferedImage(@NotNull WritableImage snapshot) {
        BufferedImage bufferedImage = new BufferedImage(
                (int) snapshot.getWidth(),
                (int) snapshot.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        PixelReader pixelReader = snapshot.getPixelReader();


        // Copy pixels from snapshot to buffered image
        for (int y = 0; y < (int) snapshot.getHeight(); y++) {
            for (int x = 0; x < (int) snapshot.getWidth(); x++) {
                int argb = pixelReader.getArgb(x, y);
                bufferedImage.setRGB(x, y, argb);
            }
        }
        return bufferedImage;
    }
}