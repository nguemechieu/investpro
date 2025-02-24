package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Getter;
import lombok.Setter;
import org.investpro.Exchange;
import org.investpro.Position;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class PositionsUI extends Region {
    private static final Logger logger = LoggerFactory.getLogger(PositionsUI.class);
    private final Exchange exchange;
    private final ScrollPane scrollPane;
    private final Canvas canvas;
    private final ScheduledExecutorService scheduler;
    private final List<Position> positionsList;

    private final BarChart<String, Number> profitLossChart;
    private final LineChart<Number, Number> equityChart;
    private final Label riskInfoLabel;
    private final XYChart.Series<Number, Number> equitySeries;
    private final AtomicInteger timeCounter = new AtomicInteger(0);

    public PositionsUI(Exchange exchange) {
        this.exchange = exchange;
        this.canvas = new Canvas(800, 700);  // Canvas for Long/Short Positions
        canvas.setStyle(
                "-fx-background-color: #212121;" +
                        "-fx-border-color: #333;" +
                        "-fx-border-width: 1;" +
                        "-fx-padding: 10;"
        );
        canvas.getGraphicsContext2D().setStroke(Color.BLACK);
        this.scrollPane = createScrollPane();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.positionsList = new CopyOnWriteArrayList<>();

        // **Charts Initialization**
        this.profitLossChart = createBarChart();
        this.equityChart = createLineChart();
        this.equitySeries = new XYChart.Series<>();
        this.equitySeries.setName("Equity Trend");
        this.equityChart.getData().add(equitySeries);

        // **Risk Management Panel**
        this.riskInfoLabel = new Label();
        this.riskInfoLabel.setStyle("-fx-text-fill: rgba(13,53,48,0.73); -fx-font-size: 16px; -fx-font-weight: bold;");

        setupUI();
        startUpdating();
        logger.info("Positions UI initialized.");
    }

    /**
     * ✅ Creates a scrollable container for the Canvas
     */
    private @NotNull ScrollPane createScrollPane() {
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setPrefSize(800, 800);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        return scrollPane;
    }

    /**
     * ✅ Create a Bar Chart
     */
    private @NotNull BarChart<String, Number> createBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Trade");
        yAxis.setLabel("Profit/Loss ($)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setPrefSize(600, 300);
        chart.setStyle("-fx-background-color: black;");

        return chart;
    }

    /**
     * ✅ Create a Line Chart
     */
    private @NotNull LineChart<Number, Number> createLineChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (seconds)");
        yAxis.setLabel("Equity ($)");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefSize(600, 300);
        chart.setTitle("Equity Over Time");
        chart.setStyle("-fx-background-color: black;");
        return chart;
    }

    /**
     * ✅ Set up the UI Layout
     */
    private void setupUI() {
        VBox canvasContainer = new VBox(new Label("📊 Positions Overview"), scrollPane);
        canvasContainer.setPadding(new Insets(10));

        VBox chartsContainer = new VBox(20, profitLossChart, equityChart, riskInfoLabel);
        chartsContainer.setPadding(new Insets(15));

        HBox mainContainer = new HBox(20, canvasContainer, chartsContainer);
        getChildren().add(mainContainer);
    }

    /**
     * ✅ Start updating data
     */
    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            List<Position> updatedPositions;
            try {
                updatedPositions = exchange.getPositions();
            } catch (IOException | InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            synchronized (positionsList) {
                positionsList.clear();
                positionsList.addAll(updatedPositions);
            }

            Platform.runLater(() -> {
                synchronized (positionsList) {
                    drawPositions(canvas.getGraphicsContext2D(), positionsList);
                    updateProfitLossChart();
                    updateEquityChart();
                }
            });

        }, 5, 10, TimeUnit.SECONDS);
    }

    /**
     * ✅ Draws the positions (Long first, then Short)
     */
    private void drawPositions(@NotNull GraphicsContext gc, @NotNull List<Position> positions) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.GREEN);
        gc.setFont(Font.font("Arial", 20));

        int yOffset = 50;
        gc.fillText("🔹 Positions Overview", 50, yOffset);
        gc.setStroke(Color.PURPLE);
        gc.strokeLine(50, yOffset + 10, 800, yOffset + 10);
        yOffset += 40;

        // **Long Positions**
        gc.setFill(Color.LIMEGREEN);
        gc.fillText("📈 Long Positions:", 50, yOffset);
        yOffset += 30;
        int longCount = 0;

        for (Position position : positions) {
            if (position.getLongPosition().getPl() > 0) {
                drawPosition(gc, position, yOffset, true);
                yOffset += 40;
                longCount++;
            }
        }

        if (longCount == 0) {
            gc.setFill(Color.GRAY);
            gc.fillText("No Long Positions", 50, yOffset);
            yOffset += 30;
        }

        yOffset += 20; // Spacing

        // **Short Positions**
        gc.setFill(Color.RED);
        gc.fillText("📉 Short Positions:", 50, yOffset);
        yOffset += 30;
        int shortCount = 0;

        for (Position position : positions) {
            if (position.getShortPosition().getPl() < 0) {
                drawPosition(gc, position, yOffset, false);
                yOffset += 40;
                shortCount++;
            }
        }

        if (shortCount == 0) {
            gc.setFill(Color.GRAY);
            gc.fillText("No Short Positions", 50, yOffset);
            yOffset += 30;
        }

        // Adjust canvas height dynamically
        canvas.setHeight(yOffset + 100);
    }

    /**
     * ✅ Draws a single position
     */
    private void drawPosition(@NotNull GraphicsContext gc, @NotNull Position position, int yOffset, boolean isLong) {
        gc.setFill(isLong ? Color.LIME : Color.RED);
        gc.fillText(position.getInstrument() + " | P/L: $" +
                        String.format("%.2f", isLong ? position.getLongPosition().getPl() : position.getShortPosition().getPl()) +
                        " | Unrealized: $" +
                        String.format("%.2f", isLong ? position.getLongPosition().getUnrealizedPL() : position.getShortPosition().getUnrealizedPL()) +
                        " | Financing: $" +
                        String.format("%.2f", isLong ? position.getLongPosition().getFinancing() : position.getShortPosition().getFinancing()),
                50, yOffset);
    }

    /**
     * ✅ Updates Profit/Loss Chart
     */
    private void updateProfitLossChart() {
        profitLossChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Profit/Loss Per Trade");

        for (Position position : positionsList) {
            series.getData().add(new XYChart.Data<>(position.getInstrument(), position.getProfitOrLoss()));
        }

        profitLossChart.getData().add(series);
    }

    /**
     * ✅ Updates Equity Chart
     */
    private void updateEquityChart() {
        double totalEquity = positionsList.stream().mapToDouble(Position::getValue).sum();
        equitySeries.getData().add(new XYChart.Data<>(timeCounter.incrementAndGet(), totalEquity));

        if (equitySeries.getData().size() > 100) {
            equitySeries.getData().removeFirst();
        }
    }
}
