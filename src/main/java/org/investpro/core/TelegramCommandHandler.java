package org.investpro.core;

import javafx.application.Platform;
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
    private final TelegramTradingCommands tradingCommands;

    private final HttpClient httpClient;
    private volatile Stage primaryStage = null;

    private final long lastUpdateId = -1L;
    private final boolean polling = false;

    public TelegramCommandHandler(@NotNull SystemCore systemCore, @NotNull TelegramNotifier telegramNotifier) {
        this.systemCore = systemCore;
        this.telegramNotifier = telegramNotifier;
        this.tradingCommands = new TelegramTradingCommands(systemCore);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

    }

    /**
     * Set the primary application stage for screenshot functionality
     * Must be called after the JavaFX application is initialized
     */
    public void setPrimaryStage(@NotNull Stage stage) {
        this.primaryStage = stage;
        log.debug("Primary stage set for Telegram screenshot commands");
    }
    public void setBotControl(java.util.function.Function<String, String> control) { tradingCommands.setBotControl(control); }

    public String handleCommand(@NotNull String command, String user) {
        if (command.isBlank()) {
            return "❌ No command provided";
        }

        // Strip leading slashes from command
        String cleanCommand = command.trim().replaceAll("^/+", "");

        // Debug logging to track command parsing

        String[] parts = cleanCommand.split("\\s+");
        String cmd = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT);

        log.debug("Parsed command: '{}' | Parts count: {}", cmd, parts.length);

        try {
            String tradingReply = tradingCommands.handle(user, cmd, parts);
            if (tradingReply != null) return tradingReply;
            return switch (cmd) {
                case "start", "help" -> getHelpText();
                case "status" -> getStatusReport();
                case "balance" -> getBalanceReport();
                case "positions" -> getPositionsReport();
                case "orders" -> getOrdersReport();
                case "screenshot" -> user.startsWith("desktop:") || user.startsWith(Objects.toString(telegramNotifier.getChatId(), "") + ":")
                        ? captureAndSendScreenshot() : "Screenshots are available only in the configured notification chat.";
                case "market", "quote" -> {
                    if (parts.length < 2) yield "Usage: /quote PAIR_OR_NATIVE_PRODUCT_ID";
                    try {
                        yield getMarketReport(TradePair.fromSymbol(parts[1]));
                    } catch (SQLException | ClassNotFoundException | IllegalArgumentException error) {
                        yield "Invalid symbol. Use a currency pair or an exchange-native futures/perpetual product ID.";
                    }
                }
                case "risk" -> getRiskReport();
                case "strategy" -> getStrategyReport();
                case "health" -> getHealthReport();
                case "ask", "analyze", "compare", "learn", "invest", "news" -> {
                    if (parts.length < 2) yield "Usage: /" + cmd + " YOUR_QUESTION_OR_TOPIC";
                    String question = cleanCommand.substring(parts[0].length()).trim();
                    String context = "Current desktop exchange: " + systemCore.getExchange().getName()
                            + "; mode: " + systemCore.getExchange().getResolvedTradingMode() + ".\n";
                    if (cmd.equals("analyze")) context += getMarketReport(parsePair(parts[1])) + "\n";
                    yield telegramNotifier.askAI(user, context + cmd + ": " + question);
                }
                case "reset" -> { telegramNotifier.resetConversation(user); yield "AI conversation cleared."; }
                case "setapikey" ->
                        "Configure OPENAI_API_KEY in the desktop environment. Do not send keys through Telegram.";
                default -> "❌ Unknown command: /" + cmd + "\nType /help for available commands.";
            };
        } catch (Exception e) {
            log.warn("Telegram command {} failed ({})", cmd, e.getClass().getSimpleName());
            if (e instanceof IllegalArgumentException) return "Invalid arguments. Use /help for command syntax.";
            return "Command could not complete. Check /orders or /history before retrying a trading action.";
        }
    }

    private String getHelpText() {
        return """
                InvestPro Remote Desk

                ACCOUNT: /status /balance /portfolio /positions /orders /history
                MARKET: /quote BTC/USD /analyze BTC/USD /strategy /risk
                WATCHLIST: /watch BTC/USD /unwatch BTC/USD /watchlist
                TRADING: /buy BTC/USD QUANTITY /sell BTC/USD QUANTITY
                /limit buy|sell BTC/USD QUANTITY PRICE
                /stop buy|sell SYMBOL QUANTITY STOP_PRICE
                /trailing buy|sell SYMBOL QUANTITY DISTANCE amount|percent
                /bracket buy|sell SYMBOL QUANTITY ENTRY STOP_LOSS TAKE_PROFIT
                /cancelall /botstart /botstop
                /cancel ORDER_ID /confirm CODE /abort
                CONTROL: /pause /resume /mode /exchange /health /screenshot
                INVESTMENT: /invest TOPIC /compare ASSETS /learn TOPIC /news TOPIC
                AI: /ask QUESTION or send a normal message; /reset clears conversation
                SIZING: /size EQUITY RISK_PERCENT ENTRY STOP

                Trade actions require a preview and confirmation within 60 seconds.
                PAPER uses local simulation; LIVE uses your connected broker.
                Watchlists and AI conversations are session-local.
                """;
    }

    private TradePair parsePair(String value) throws Exception {
        return TradePair.fromSymbol(value);
    }

    private String getStatusReport() {
        try {
            StringBuilder report = new StringBuilder("*Account Status*\n\n");

            Account account = systemCore.getExchange().tradingAccount().get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (account != null) {
                report.append("💰 Balance: $").append(formatPrice(account.getAvailableBalance())).append("\n");
                report.append("📈 Equity: $").append(formatPrice(account.getEquity())).append("\n");
                report.append("📊 Margin Used: $").append(formatPrice(account.getMarginUsed())).append("\n");
                report.append("🔐 Margin Available: $").append(formatPrice(account.getMarginAvailable())).append("\n");
                report.append("📉 Unrealized P&L: $").append(formatPrice(account.getUnrealizedPnl())).append("\n");

                double marginLevel = account.getLeverage();
                String marginStatus = marginLevel > 2.0 ? "✅" : marginLevel > 1.5 ? "⚠️" : "🔴";
                report.append(marginStatus).append(" Margin Level: ").append(String.format("%.2f%%", marginLevel * 100))
                        .append("\n");

                report.append("\n_Updated: ").append(getCurrentTime()).append("_");
            } else {
                report.append("⚠️ Unable to fetch account information");
            }

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating status report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getBalanceReport() {
        try {
            StringBuilder report = new StringBuilder("*Balance Breakdown*\n\n");

            Account account = systemCore.getExchange().tradingAccount().get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (account != null) {
                report.append("💵 Cash Balance: $").append(formatPrice(account.getTotalBalance())).append("\n");
                report.append("📊 Equity: $").append(formatPrice(account.getEquity())).append("\n");
                report.append("💳 Used Margin: $").append(formatPrice(account.getMarginUsed())).append("\n");
                report.append("🆓 Free Margin: $").append(formatPrice(account.getMarginAvailable())).append("\n");

                double marginLevel = account.getLeverage();
                report.append("📈 Margin Level: ").append(String.format("%.2f%%", marginLevel * 100)).append("\n");

                double freeMargin = account.getMarginAvailable();
                double usagePercent = freeMargin > 0 ? (account.getMarginUsed() / freeMargin) * 100 : 0.0;
                report.append("⚙️ Margin Usage: ").append(String.format("%.1f%%", usagePercent)).append("\n");

                report.append("\n_Updated: ").append(getCurrentTime()).append("_");
            } else {
                report.append("⚠️ Unable to fetch balance information");
            }

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating balance report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getPositionsReport() {
        try {
            if (systemCore.getExchange().isPaperTrading()) return tradingCommands.handle("local", "portfolio", new String[]{"portfolio"});
            List<Position> positions = systemCore.getExchange().fetchAllPositions().get(15, java.util.concurrent.TimeUnit.SECONDS);

            StringBuilder report = new StringBuilder("*Open Positions (" + positions.size() + ")*\n\n");

            for (Position pos : positions) {
                report.append("🏷️ *").append(pos.getSymbol()).append("*\n");
                report.append("   Size: ").append(pos.getQuantity()).append(" units\n");
                report.append("   Entry: $").append(formatPrice(pos.getEntryPrice())).append("\n");
                report.append("   Current: $").append(formatPrice(pos.getCurrentPrice())).append("\n");

                double pnl = pos.getUnrealizedPnl();
                double notional = pos.getQuantity() * pos.getEntryPrice();
                double pnlPercent = notional > 0 ? (pnl / notional) * 100 : 0.0;
                String pnlStatus = pnl >= 0 ? "✅" : "❌";

                report.append(pnlStatus).append(" P&L: $").append(formatPrice(pnl))
                        .append(" (").append(String.format("%.2f%%", pnlPercent)).append(")\n");

                report.append("   Margin: $").append(formatPrice(pos.getMarginUsed())).append("\n");
                report.append("\n");
            }

            report.append("_Updated: ").append(getCurrentTime()).append("_");
            return report.toString();
        } catch (Exception e) {
            log.error("Error generating positions report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getOrdersReport() {
        try {
            List<OpenOrder> orders = systemCore.getExchange().orderExecution().fetchAllOpenOrders().get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (orders == null || orders.isEmpty()) {
                return "📭 No pending orders";
            }

            StringBuilder report = new StringBuilder("*Open Orders (" + orders.size() + ")*\n\n");

            for (OpenOrder order : orders) {
                report.append("Order ID: ").append(order.getOrderId()).append("\n");
                report.append("🔔 *").append(order.getTradePair().toString()).append(" / ").append(order.getSide())
                        .append("*\n");
                report.append("   Side: ").append(order.getSide()).append("\n");
                report.append("   Amount: ").append(order.getSize()).append(" units\n");
                report.append("   Price: $").append(formatPrice(order.getPrice())).append("\n");
                report.append("   Status: ").append(order.getStatus()).append("\n");
                report.append("   Time: ").append(order.getTimestamp()).append("\n");
                report.append("\n");
            }

            report.append("_Updated: ").append(getCurrentTime()).append("_");
            return report.toString();
        } catch (Exception e) {
            log.error("Error generating orders report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getMarketReport(TradePair symbol) {
        try {
            if (symbol == null) {
                symbol = systemCore.getSelectedTradePair() != null ? systemCore.getSelectedTradePair()
                        : null;
            }

            Ticker ticker = systemCore.getExchange().fetchTicker(symbol).get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (ticker == null) {
                return "❌ Unable to fetch market data for " + symbol;
            }

            StringBuilder report = new StringBuilder("*Market Info - ").append(symbol).append("*\n\n");

            report.append("📊 Bid: $").append(formatPrice(ticker.getBidPrice())).append("\n");
            report.append("🎯 Ask: $").append(formatPrice(ticker.getAskPrice())).append("\n");

            double spread = ticker.getAskPrice() - ticker.getBidPrice();
            double spreadPercent = ticker.getBidPrice() > 0 ? (spread / ticker.getBidPrice()) * 100 : 0.0;
            report.append("📈 Spread: $").append(formatPrice(spread))
                    .append(" (").append(String.format("%.4f%%", spreadPercent)).append(")\n");

            report.append("🔺 High (24h): $").append(formatPrice(ticker.getHighPrice())).append("\n");
            report.append("🔻 Low (24h): $").append(formatPrice(ticker.getLowPrice())).append("\n");

            if (ticker.getVolume() > 0) {
                report.append("📦 Volume: ").append(String.format("%.2f", ticker.getVolume())).append("\n");
            }

            report.append("\n_Updated: ").append(getCurrentTime()).append("_");

            return report.toString();
        } catch (Exception e) {
            log.error("Error generating market report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getRiskReport() {
        try {
            var exchange = systemCore.getExchange();
            Account account = exchange.tradingAccount().get(15, java.util.concurrent.TimeUnit.SECONDS);
            if (account == null) return "Account risk data unavailable.";
            return "Risk snapshot (" + exchange.getResolvedTradingMode() + ")\n"
                    + "Available balance: " + formatPrice(account.getAvailableBalance())
                    + "\nEquity reported: " + formatPrice(account.getEquity())
                    + "\nUsed margin: " + formatPrice(account.getMarginUsed())
                    + "\nAvailable margin: " + formatPrice(account.getMarginAvailable())
                    + "\nUnrealized P&L: " + formatPrice(account.getUnrealizedPnl())
                    + "\nLeverage reported: " + account.getLeverage()
                    + "\nStop protection: not verified; inspect individual orders."
                    + "\nZero values may mean the exchange does not supply the metric."
                    + "\nUpdated: " + getCurrentTime();
        } catch (Exception error) { return "Risk data unavailable. Check the desktop connection."; }
    }
    private String getStrategyReport() {
        try {

            return "*Strategy Status*\n\n" + "📊 Active Strategies: Monitoring\n" +
                    "🎯 Auto Trading: " +
                    (systemCore.isAutoTradingEnabled() ? "✅ Enabled\n" : "❌ Disabled\n") +
                    "🔄 Streaming: " +
                    (systemCore.isStreaming() ? "🔴 Active\n" : "⚪ Inactive\n") +
                    "🤖 AI Reasoning: " +
                    (systemCore.isAiReasoningEnabled() ? "✅ Enabled\n" : "❌ Disabled\n") +
                    "\n_Updated: " + getCurrentTime() + "_";
        } catch (Exception e) {
            log.error("Error generating strategy report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String getHealthReport() {
        try {

            return "System health\nExchange: " + systemCore.getExchange().getName()
                    + "\nMode: " + systemCore.getExchange().getResolvedTradingMode()
                    + "\nAuthenticated connection: " + systemCore.getExchange().isAuthenticatedSessionConnected()
                    + "\nStreaming: " + systemCore.isStreaming()
                    + "\nAuto trading: " + systemCore.isAutoTradingEnabled()
                    + "\nUpdated: " + getCurrentTime();
        } catch (Exception e) {
            log.error("Error generating health report", e);
            return "Report unavailable. Check the desktop connection.";
        }
    }

    private String formatPrice(double price) {
        return String.format(Locale.ROOT, "%.8g", price);
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Capture the current JavaFX scene and send as screenshot to Telegram.
     * Returns message text to be sent to user.
     * <p>
     * IMPORTANT: This method is called from TelegramPollingThread (background
     * thread)
     * but JavaFX operations must happen on the JavaFX Application Thread.
     * Uses Platform.runLater() to execute screenshot capture on the correct thread.
     */
    private @NotNull String captureAndSendScreenshot() {
        if (primaryStage == null) return "UI is not initialized.";
        if (Platform.isFxApplicationThread()) return "Screenshot requests must run on the background command thread.";
        var captured = new java.util.concurrent.CompletableFuture<WritableImage>();
        Path temporary = null;
        try {
            Platform.runLater(() -> {
                if (captured.isDone()) return;
                try {
                    Scene scene = primaryStage.getScene();
                    if (scene == null) throw new IllegalStateException("No scene available");
                    captured.complete(scene.snapshot(null));
                } catch (Exception error) { captured.completeExceptionally(error); }
            });
            WritableImage image = captured.get(10, java.util.concurrent.TimeUnit.SECONDS);
            // Encoding, disk access and network upload must not block the JavaFX thread.
            temporary = Files.createTempFile("investpro_screenshot_", ".png");
            ImageIO.write(getBufferedImage(image), "png", temporary.toFile());
            String caption = "InvestPro screenshot | " + getCurrentTime() + " | "
                    + (int) image.getWidth() + "x" + (int) image.getHeight();
            return telegramNotifier.sendPhoto(temporary, caption) ? "Screenshot sent." : "Screenshot upload failed.";
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "Screenshot interrupted.";
        } catch (Exception error) {
            return "Screenshot unavailable. Check that the desktop window is open.";
        } finally {
            captured.cancel(false);
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException error) { log.debug("Unable to remove temporary screenshot"); }
            }
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
