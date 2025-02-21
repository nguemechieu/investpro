package org.investpro.ui;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Getter;
import lombok.Setter;
import org.investpro.CoinInfo;
import org.investpro.Exchange;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class CoinInfoUI extends Region {
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final List<CoinInfo> coinInfoList;
    private final ScheduledExecutorService scheduler;
    private final Exchange exchange;

    public CoinInfoUI(Exchange exchange) {
        this.exchange = exchange;
        this.canvas = new Canvas(1000, 800);
        this.scrollPane = new ScrollPane(canvas);
        this.coinInfoList = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        setupScrollPane();

        fetchCoinData();  // Start updating after UI setup
        getChildren().add(scrollPane);
    }

    private void setupScrollPane() {
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
    }

    private void fetchCoinData() {
        scheduler.scheduleAtFixedRate(() -> {
            List<CoinInfo> updatedCoinInfo = exchange.getCoinInfoList();
            if (!updatedCoinInfo.isEmpty()) {
                synchronized (coinInfoList) {
                    coinInfoList.clear();
                    coinInfoList.addAll(updatedCoinInfo);
                }

            }
            Platform.runLater(() -> drawCoinData(canvas.getGraphicsContext2D()));
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void drawCoinData(@NotNull GraphicsContext gc) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight()); // Clear before redrawing
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight()); // Background color
        gc.setFill(Color.YELLOWGREEN);
        gc.setFont(Font.font("Arial", 18));

        int yOffset = 40;
        gc.fillText("📊 Cryptocurrency Market Data 📊", 20, yOffset);
        gc.strokeLine(20, yOffset + 10, 1400, yOffset + 10);
        yOffset += 30;

        // **If no data, show error message**
        if (coinInfoList.isEmpty()) {
            gc.setFill(Color.RED);
            gc.fillText("❌ No market data available.", 20, yOffset);
            return;
        }

        // **Table Headers**
        gc.setFont(Font.font("Arial", 16));
        gc.setFill(Color.WHEAT);
        gc.fillText("ID", 20, yOffset);
        gc.fillText("Symbol", 150, yOffset);
        gc.fillText("Name", 250, yOffset);
        gc.fillText("Current Price", 450, yOffset);
        gc.fillText("Market Cap", 600, yOffset);
        gc.fillText("Rank", 800, yOffset);
        gc.fillText("Last Updated", 900, yOffset);
        gc.strokeLine(20, yOffset + 10, 1400, yOffset + 10);
        yOffset += 30;

        // **Iterate over data**
        for (CoinInfo coin : coinInfoList) {
            gc.setFill(Color.GREEN);
            gc.fillText(coin.getId(), 20, yOffset);
            gc.fillText(coin.getSymbol(), 150, yOffset);
            gc.fillText(coin.getName(), 250, yOffset);
            gc.fillText(String.format("$%.2f", coin.getCurrent_price()), 450, yOffset);
            gc.fillText(String.valueOf(coin.getMarket_cap()), 600, yOffset);
            gc.fillText(String.valueOf(coin.getMarket_cap_rank()), 800, yOffset);
            gc.fillText(String.valueOf(coin.getLast_updated()), 900, yOffset);

            yOffset += 30;
            gc.setStroke(Color.GRAY);
            gc.strokeLine(20, yOffset, 1400, yOffset);
            yOffset += 10;
        }

        // **Adjust canvas size based on content**
        canvas.setHeight(yOffset + 50);
    }
}
