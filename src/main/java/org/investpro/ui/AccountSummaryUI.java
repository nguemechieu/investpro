package org.investpro.ui;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Getter;
import lombok.Setter;
import org.investpro.Account;
import org.investpro.Exchange;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class AccountSummaryUI extends Region {
    private static final Logger logger = LoggerFactory.getLogger(AccountSummaryUI.class);
    private final Exchange exchange;
    private final ScrollPane scrollPane;
    private final Canvas canvas;
    private final ScheduledExecutorService scheduler;
    private final List<Account> accountSummaryList;

    public AccountSummaryUI(Exchange exchange) {
        this.exchange = exchange;
        this.canvas = new Canvas(1300, 780); // Increased width for better readability


        this.scrollPane = createScrollPane();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.accountSummaryList = new CopyOnWriteArrayList<>();

        setupUI();
        startUpdating();
        logger.info("Account summary initialized.");
    }

    /**
     * ✅ Creates a scrollable container
     */
    private @NotNull ScrollPane createScrollPane() {
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setPrefSize(1300, 1000);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setBackground(Background.fill(Color.DARKBLUE));  // ScrollPane background
        return scrollPane;
    }

    /**
     * ✅ Set up the UI
     */
    private void setupUI() {
        getChildren().add(scrollPane);
    }

    /**
     * ✅ Start fetching account summary updates
     */
    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            List<Account> updatedAccounts = exchange.getAccountSummary();
            synchronized (accountSummaryList) {
                accountSummaryList.clear();
                accountSummaryList.addAll(updatedAccounts);
            }

            Platform.runLater(() -> {
                synchronized (accountSummaryList) {
                    drawAccountSummary(canvas.getGraphicsContext2D(), accountSummaryList);
                }
            });

        }, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * ✅ Draws the account summary data
     */
    private void drawAccountSummary(@NotNull GraphicsContext gc, @NotNull List<Account> accounts) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFont(Font.font("Arial", 20));
        canvas.getGraphicsContext2D().setStroke(Color.BLACK);

        canvas.getGraphicsContext2D().fillRect(0, 0, 1300, 800);


        int yOffset = 50;
        gc.setFill(Color.YELLOWGREEN);

        gc.fillText("🔹 Account Summary", 50, yOffset);

        gc.strokeLine(50, yOffset + 10, 1100, yOffset + 10);
        yOffset += 40;


        for (Account account : accounts) {
            gc.strokeLine(50, yOffset, 1300, yOffset);
            yOffset += 30;
            gc.fillText("📌 Account ID: " + account.getId(), 50, yOffset);
            gc.fillText("📌 Alias: " + account.getAlias(), 50, yOffset + 30);
            gc.fillText("📌 Currency: " + account.getCurrency(), 50, yOffset + 60);
            gc.fillText("📌 Balance: $" + String.format("%.2f", account.getBalance()), 50, yOffset + 90);
            gc.fillText("📌 Equity: $" + String.format("%.2f", account.getEquity()), 50, yOffset + 120);
            gc.fillText("📌 Free Margin: $" + String.format("%.2f", account.getMarginAvailable()), 50, yOffset + 150);
            gc.fillText("📌 Margin Used: $" + String.format("%.2f", account.getMarginUsed()), 50, yOffset + 180);
            gc.fillText("📌 NAV: $" + String.format("%.2f", account.getNAV()), 50, yOffset + 210);
            gc.fillText("📌 Leverage: " + account.getLeverage() + "x", 50, yOffset + 240);
            gc.fillText("📌 Open Positions: " + account.getOpenPositionCount(), 50, yOffset + 270);
            gc.fillText("📌 Open Trades: " + account.getOpenTradeCount(), 50, yOffset + 300);
            gc.fillText("📌 Pending Orders: " + account.getPendingOrderCount(), 50, yOffset + 330);
            gc.fillText("📌 Profit/Loss: $" + String.format("%.2f", account.getPl()), 50, yOffset + 360);
            gc.fillText("📌 Unrealized P/L: $" + String.format("%.2f", account.getUnrealizedPL()), 50, yOffset + 390);
            gc.fillText("📌 Resettable P/L: $" + String.format("%.2f", account.getResettablePL()), 50, yOffset + 420);
            gc.fillText("📌 Withdrawal Limit: $" + String.format("%.2f", account.getWithdrawalLimit()), 50, yOffset + 450);
            gc.fillText("📌 Margin Closeout Percent: " + String.format("%.2f", account.getMarginCloseoutPercent()) + "%", 50, yOffset + 480);

            // Separator for next account
            gc.fillText("=====================================================================================", 50, yOffset + 500);
            gc.setFill(Color.BLACK);
            yOffset += 540; // Adjust for next account
        }

        // Adjust scroll area dynamically
        canvas.setHeight(yOffset + 100);
    }
}
