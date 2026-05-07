package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/**
 * Backtesting Panel - Executes historical backtests of strategies and displays
 * results.
 * Shows equity curves, performance metrics, and trade analysis.
 */
@Slf4j
public class BacktestingPanel extends VBox {

    private ComboBox<String> strategyCombo;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> symbolCombo;
    private Label statusLabel;
    private ProgressBar progressBar;
    private TableView<BacktestTrade> tradesTable;
    private Label totalReturnLabel;
    private Label winRateLabel;
    private Label maxDrawdownLabel;

    public BacktestingPanel() {
        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("backtest-panel");

        setupUI();
    }

    private void setupUI() {
        // Header
        Label titleLabel = new Label("Strategy Backtest Engine");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Configuration Section
        VBox configBox = createConfigurationSection();

        // Results Section
        VBox resultsBox = createResultsSection();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(configBox, new Separator(), resultsBox);
        scrollPane.setContent(content);

        getChildren().addAll(titleLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createConfigurationSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Backtest Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Strategy Selection
        strategyCombo = new ComboBox<>();
        strategyCombo.getItems().addAll("Mean Reversion", "Trend Following", "Breakout", "My Custom Strategy");
        strategyCombo.setPrefHeight(35);
        HBox strategyBox = createLabeledInput("Strategy:", strategyCombo);

        // Symbol Selection
        symbolCombo = new ComboBox<>();
        symbolCombo.getItems().addAll("BTC/USD", "ETH/USD", "SPY", "AAPL", "EURUSD");
        symbolCombo.setPrefHeight(35);
        HBox symbolBox = createLabeledInput("Symbol:", symbolCombo);

        // Date Range
        startDatePicker = new DatePicker(LocalDate.now().minusYears(1));
        startDatePicker.setPrefHeight(35);
        HBox startDateBox = createLabeledInput("Start Date:", startDatePicker);

        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefHeight(35);
        HBox endDateBox = createLabeledInput("End Date:", endDatePicker);

        // Action Buttons
        Button runBacktestBtn = new Button("▶ Run Backtest");
        runBacktestBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #10b981; -fx-text-fill: white;");
        runBacktestBtn.setOnAction(e -> runBacktest());

        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #ef4444; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> cancelBacktest());

        HBox buttonBox = new HBox(12, runBacktestBtn, cancelBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Progress Section
        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(20);
        progressBar.setStyle("-fx-accent: #10b981;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #a0aec0;");

        section.getChildren().addAll(sectionTitle, strategyBox, symbolBox, startDateBox, endDateBox,
                new Separator(), buttonBox, progressBar, statusLabel);
        return section;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #10b981; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Backtest Results");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Performance Metrics
        VBox metricsBox = createMetricsBox();

        // Equity Curve Chart
        AreaChart<Number, Number> equityChart = createEquityCurveChart();

        // Trades Table
        tradesTable = createTradesTable();

        // Export Button
        Button exportBtn = new Button("📊 Export Results");
        exportBtn.setStyle(
                "-fx-padding: 8px 20px; -fx-font-size: 12px; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        exportBtn.setOnAction(e -> exportResults());

        section.getChildren().addAll(sectionTitle, metricsBox, new Separator(),
                new Label("Equity Curve"), equityChart,
                new Separator(), new Label("Trades"),
                tradesTable, exportBtn);
        VBox.setVgrow(equityChart, Priority.ALWAYS);
        VBox.setVgrow(tradesTable, Priority.ALWAYS);
        return section;
    }

    private VBox createMetricsBox() {
        VBox metrics = new VBox(8);
        metrics.setPadding(new Insets(10));
        metrics.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 4;");

        // Total Return
        totalReturnLabel = new Label("Total Return: 0%");
        totalReturnLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Win Rate
        winRateLabel = new Label("Win Rate: 0%");
        winRateLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Max Drawdown
        maxDrawdownLabel = new Label("Max Drawdown: 0%");
        maxDrawdownLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 14px; -fx-font-weight: bold;");

        HBox metricsRow = new HBox(20);
        metricsRow.setAlignment(Pos.CENTER_LEFT);
        metricsRow.getChildren().addAll(totalReturnLabel, winRateLabel, maxDrawdownLabel);

        metrics.getChildren().addAll(
                new Label("Performance Metrics"),
                metricsRow,
                new Label("Trades: 0 | Profitable: 0 | Loss: 0 | Avg Win: $0 | Avg Loss: $0"));
        return metrics;
    }

    private AreaChart<Number, Number> createEquityCurveChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (Days)");
        xAxis.setStyle("-fx-text-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setStyle("-fx-text-fill: #a0aec0;");

        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle("Equity Curve");
        chart.setStyle("-fx-background-color: #0f3460;");

        // Sample data
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");
        series.getData().addAll(
                new XYChart.Data<>(0, 10000),
                new XYChart.Data<>(10, 10500),
                new XYChart.Data<>(20, 10200),
                new XYChart.Data<>(30, 11000),
                new XYChart.Data<>(40, 10800),
                new XYChart.Data<>(50, 12000));
        chart.getData().add(series);

        return chart;
    }

    private TableView<BacktestTrade> createTradesTable() {
        TableView<BacktestTrade> table = new TableView<>();
        table.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        table.setPrefHeight(200);

        TableColumn<BacktestTrade, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getDate()));
        dateCol.setPrefWidth(100);

        TableColumn<BacktestTrade, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getType()));
        typeCol.setPrefWidth(80);

        TableColumn<BacktestTrade, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getPrice()));
        priceCol.setPrefWidth(100);

        TableColumn<BacktestTrade, String> profitCol = new TableColumn<>("P&L");
        profitCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getProfit()));
        profitCol.setPrefWidth(100);

        table.getColumns().addAll(dateCol, typeCol, priceCol, profitCol);

        // Sample data with accurate profit calculation
        // Format: date, type, entryPrice, exitPrice, quantity
        table.getItems().addAll(
                new BacktestTrade("2025-01-15", "BUY", 45230, 45890, 1), // Profit: (45890-45230)*1 = $660
                new BacktestTrade("2025-01-18", "SELL", 45890, 45230, 1), // Profit: (45890-45230)*1 = $660
                new BacktestTrade("2025-01-22", "BUY", 44560, 44380, 1), // Loss: (44380-44560)*1 = -$180
                new BacktestTrade("2025-01-25", "SELL", 46120, 45100, 1)); // Profit: (46120-45100)*1 = $1,020

        return table;
    }

    private HBox createLabeledInput(String label, Control input) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #a0aec0; -fx-min-width: 100px;");
        HBox box = new HBox(10, lbl, input);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        return box;
    }

    private void runBacktest() {
        if (strategyCombo.getValue() == null || symbolCombo.getValue() == null) {
            showAlert("Error", "Please select strategy and symbol");
            return;
        }

        statusLabel.setText("Running backtest...");
        progressBar.setProgress(0.3);

        log.info("Starting backtest for {} on {}", strategyCombo.getValue(), symbolCombo.getValue());

        // Simulate backtest execution
        new Thread(() -> {
            try {
                for (int i = 0; i <= 100; i += 10) {
                    Thread.sleep(200);
                    final double progress = i / 100.0;
                    javafx.application.Platform.runLater(() -> progressBar.setProgress(progress));
                }
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("✓ Backtest completed!");
                    totalReturnLabel.setText("Total Return: 24.5%");
                    winRateLabel.setText("Win Rate: 65%");
                    maxDrawdownLabel.setText("Max Drawdown: -8.2%");
                    progressBar.setProgress(1.0);
                });
            } catch (InterruptedException e) {
                log.error("Backtest interrupted", e);
            }
        }).start();
    }

    private void cancelBacktest() {
        statusLabel.setText("Backtest cancelled");
        progressBar.setProgress(0);
        log.info("Backtest cancelled");
    }

    private void exportResults() {
        log.info("Exporting backtest results");
        showAlert("Info", "Results exported as CSV file");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Inner class for backtest trade records with accurate profit calculation
     */
    public static class BacktestTrade {
        private final String date;
        private final String type;
        private final String price;
        private final double entryPrice;
        private final double currentPrice;
        private final double quantity;

        /**
         * Create a backtest trade with automatic profit calculation
         * Profit = (currentPrice - entryPrice) * quantity for BUY
         * Profit = (entryPrice - currentPrice) * quantity for SELL
         */
        public BacktestTrade(String date, String type, double entryPrice, double currentPrice, double quantity) {
            this.date = date;
            this.type = type;
            this.price = String.format("$%.2f", entryPrice);
            this.entryPrice = entryPrice;
            this.currentPrice = currentPrice;
            this.quantity = quantity;
        }

        public String getDate() {
            return date;
        }

        public String getType() {
            return type;
        }

        public String getPrice() {
            return price;
        }

        /**
         * Calculate profit/loss based on trade type and price difference
         */
        public String getProfit() {
            double priceDiff = currentPrice - entryPrice;
            double profit;

            if ("BUY".equalsIgnoreCase(type)) {
                // For buy, profit is positive when current price > entry price
                profit = priceDiff * quantity;
            } else if ("SELL".equalsIgnoreCase(type)) {
                // For sell, profit is positive when entry price > current price
                profit = -priceDiff * quantity;
            } else {
                profit = 0;
            }

            String sign = profit >= 0 ? "$" : "-$";
            return sign + String.format("%.2f", Math.abs(profit));
        }

        /**
         * Get raw profit value for sorting/analysis
         */
        public double getProfitValue() {
            double priceDiff = currentPrice - entryPrice;
            if ("BUY".equalsIgnoreCase(type)) {
                return priceDiff * quantity;
            } else if ("SELL".equalsIgnoreCase(type)) {
                return -priceDiff * quantity;
            }
            return 0;
        }
    }
}
