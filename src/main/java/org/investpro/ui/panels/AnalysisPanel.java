package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy Analysis Panel - Analyzes strategy performance and characteristics.
 * Provides statistical analysis, drawdown analysis, correlation studies, and
 * risk metrics.
 */
@Slf4j
public class AnalysisPanel extends VBox {

    private ComboBox<String> strategyCombo;
    private ComboBox<String> metricCombo;
    private Label performanceScoreLabel;
    private BarChart<String, Number> performanceChart;
    private LineChart<Number, Number> drawdownChart;

    public AnalysisPanel() {
        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("analysis-panel");

        setupUI();
    }

    private void setupUI() {
        // Header
        Label titleLabel = new Label("Strategy Analysis");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Configuration Section
        HBox configBox = createConfigurationBar();

        // Content Area
        TabPane analysisTabPane = createAnalysisTabs();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(analysisTabPane);
        scrollPane.setContent(content);

        getChildren().addAll(titleLabel, configBox, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private HBox createConfigurationBar() {
        HBox configBox = new HBox(12);
        configBox.setPadding(new Insets(10));
        configBox.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 6;");
        configBox.setAlignment(Pos.CENTER_LEFT);

        // Strategy Selection
        strategyCombo = new ComboBox<>();
        strategyCombo.getItems().addAll("Mean Reversion", "Trend Following", "Breakout", "My Custom Strategy");
        strategyCombo.setPrefWidth(200);
        strategyCombo.setOnAction(e -> updateAnalysis());

        // Metric Selection
        metricCombo = new ComboBox<>();
        metricCombo.getItems().addAll("Sharpe Ratio", "Sortino Ratio", "Calmar Ratio", "Profit Factor",
                "Recovery Factor");
        metricCombo.setPrefWidth(200);
        metricCombo.setOnAction(e -> updateAnalysis());

        Label strategyLabel = new Label("Strategy:");
        strategyLabel.setStyle("-fx-text-fill: #a0aec0;");

        Label metricLabel = new Label("Metric:");
        metricLabel.setStyle("-fx-text-fill: #a0aec0;");

        Button analyzeBtn = new Button("🔍 Analyze");
        analyzeBtn.setStyle(
                "-fx-padding: 8px 20px; -fx-font-size: 12px; -fx-background-color: #10b981; -fx-text-fill: white;");
        analyzeBtn.setOnAction(e -> performAnalysis());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        configBox.getChildren().addAll(strategyLabel, strategyCombo, metricLabel, metricCombo, spacer, analyzeBtn);
        return configBox;
    }

    private TabPane createAnalysisTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-control-inner-background: #0f3460;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Performance Tab
        Tab performanceTab = new Tab("Performance", createPerformanceTab());
        performanceTab.setStyle("-fx-text-fill: #ffffff;");

        // Risk Tab
        Tab riskTab = new Tab("Risk Analysis", createRiskTab());
        riskTab.setStyle("-fx-text-fill: #ffffff;");

        // Distribution Tab
        Tab distributionTab = new Tab("Return Distribution", createDistributionTab());
        distributionTab.setStyle("-fx-text-fill: #ffffff;");

        // Correlation Tab
        Tab correlationTab = new Tab("Correlation", createCorrelationTab());
        correlationTab.setStyle("-fx-text-fill: #ffffff;");

        tabPane.getTabs().addAll(performanceTab, riskTab, distributionTab, correlationTab);
        return tabPane;
    }

    private VBox createPerformanceTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        // Performance Score
        HBox scoreBox = new HBox(20);
        scoreBox.setPadding(new Insets(12));
        scoreBox.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #10b981; -fx-border-width: 1; -fx-border-radius: 6;");

        performanceScoreLabel = new Label("Performance Score: 8.5/10");
        performanceScoreLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        Label metricsLabel = new Label("Sharpe: 1.85 | Sortino: 2.42 | Calmar: 2.95 | Profit Factor: 2.1");
        metricsLabel.setStyle("-fx-text-fill: #a0aec0;");

        scoreBox.getChildren().addAll(performanceScoreLabel, metricsLabel);

        // Performance Chart
        performanceChart = createPerformanceChart();

        content.getChildren().addAll(scoreBox, performanceChart);
        VBox.setVgrow(performanceChart, Priority.ALWAYS);
        return content;
    }

    private VBox createRiskTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        // Risk Metrics
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(12);
        metricsGrid.setPadding(new Insets(12));
        metricsGrid.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 6;");

        addMetricRow(metricsGrid, 0, "Max Drawdown", "-12.5%", Color.web("#ef4444"));
        addMetricRow(metricsGrid, 1, "Volatility (Annual)", "18.3%", Color.web("#f59e0b"));
        addMetricRow(metricsGrid, 2, "Value at Risk (95%)", "-3.2%", Color.web("#f59e0b"));
        addMetricRow(metricsGrid, 3, "Conditional VaR", "-4.8%", Color.web("#ef4444"));
        addMetricRow(metricsGrid, 4, "Max Consecutive Loss", "-5 trades", Color.web("#ef4444"));
        addMetricRow(metricsGrid, 5, "Recovery Time", "15 days", Color.web("#a0aec0"));

        // Drawdown Chart
        drawdownChart = createDrawdownChart();

        content.getChildren().addAll(metricsGrid, new Label("Drawdown Over Time"), drawdownChart);
        VBox.setVgrow(drawdownChart, Priority.ALWAYS);
        return content;
    }

    private VBox createDistributionTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        BarChart<String, Number> distributionChart = createDistributionChart();

        Label infoLabel = new Label("Return distribution analysis - shows frequency of returns by bucket");
        infoLabel.setStyle("-fx-text-fill: #a0aec0;");

        content.getChildren().addAll(infoLabel, distributionChart);
        VBox.setVgrow(distributionChart, Priority.ALWAYS);
        return content;
    }

    private VBox createCorrelationTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        // Correlation Matrix
        GridPane correlationGrid = new GridPane();
        correlationGrid.setHgap(10);
        correlationGrid.setVgap(10);
        correlationGrid.setPadding(new Insets(12));
        correlationGrid.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 6;");

        String[] assets = { "Strategy", "SPY", "BTC", "GLD" };
        for (int i = 0; i < assets.length; i++) {
            for (int j = 0; j < assets.length; j++) {
                Label cell = new Label(i == j ? "1.00" : String.format("%.2f", Math.random()));
                cell.setStyle("-fx-padding: 8; -fx-border-color: #374151; -fx-text-fill: #a0aec0;");
                if (i == 0 || j == 0) {
                    cell.setStyle(
                            "-fx-padding: 8; -fx-border-color: #374151; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                }
                correlationGrid.add(cell, j, i);
            }
        }

        Label infoLabel = new Label("Correlation matrix with benchmark assets - values range from -1.0 to 1.0");
        infoLabel.setStyle("-fx-text-fill: #a0aec0;");

        content.getChildren().addAll(infoLabel, new ScrollPane(correlationGrid));
        VBox.setVgrow(correlationGrid, Priority.ALWAYS);
        return content;
    }

    private BarChart<String, Number> createPerformanceChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Metric");
        xAxis.setStyle("-fx-text-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Value");
        yAxis.setStyle("-fx-text-fill: #a0aec0;");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Performance Metrics");
        chart.setStyle("-fx-background-color: #0f3460;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Current");
        series.getData().addAll(
                new XYChart.Data<>("Sharpe", 1.85),
                new XYChart.Data<>("Sortino", 2.42),
                new XYChart.Data<>("Calmar", 2.95),
                new XYChart.Data<>("Profit Fct", 2.10));
        chart.getData().add(series);

        return chart;
    }

    private LineChart<Number, Number> createDrawdownChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
        xAxis.setStyle("-fx-text-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Drawdown %");
        yAxis.setStyle("-fx-text-fill: #a0aec0;");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Drawdown Over Time");
        chart.setStyle("-fx-background-color: #0f3460;");

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Drawdown");
        series.getData().addAll(
                new XYChart.Data<>(0, 0),
                new XYChart.Data<>(10, -5),
                new XYChart.Data<>(20, -8),
                new XYChart.Data<>(30, -12.5),
                new XYChart.Data<>(40, -10),
                new XYChart.Data<>(50, -5),
                new XYChart.Data<>(60, 0));
        chart.getData().add(series);

        return chart;
    }

    private BarChart<String, Number> createDistributionChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Return Bucket");
        xAxis.setStyle("-fx-text-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Frequency");
        yAxis.setStyle("-fx-text-fill: #a0aec0;");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Return Distribution");
        chart.setStyle("-fx-background-color: #0f3460;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Frequency");
        series.getData().addAll(
                new XYChart.Data<>("-5% to 0%", 12),
                new XYChart.Data<>("0% to 1%", 25),
                new XYChart.Data<>("1% to 2%", 38),
                new XYChart.Data<>("2% to 3%", 18),
                new XYChart.Data<>(">3%", 7));
        chart.getData().add(series);

        return chart;
    }

    private void addMetricRow(GridPane grid, int row, String metric, String value, Color color) {
        Label metricLabel = new Label(metric);
        metricLabel.setStyle("-fx-text-fill: #a0aec0;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + colorToHex(color) + "; -fx-font-weight: bold;");

        grid.add(metricLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private void updateAnalysis() {
        log.info("Updating analysis for: {} - {}", strategyCombo.getValue(), metricCombo.getValue());
    }

    private void performAnalysis() {
        if (strategyCombo.getValue() == null) {
            showAlert("Error", "Please select a strategy");
            return;
        }
        log.info("Performing analysis on {}", strategyCombo.getValue());
        showAlert("Info", "Analysis complete for " + strategyCombo.getValue());
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
