package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import lombok.extern.slf4j.Slf4j;
import org.investpro.backtesting.InstitutionalBacktestMetrics;
import org.investpro.i18n.LocalizationService;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.util.Objects;

/**
 * Professional backtesting report panel displaying institutional-grade metrics.
 * Shows comprehensive performance analysis with color-coded indicators.
 */
@Slf4j
public class BacktestReportPanel extends ScrollPane {

    private final VBox reportContent;
    private InstitutionalBacktestMetrics metrics;

    // Color scheme for metrics
    private static final String COLOR_EXCELLENT = "#10b981"; // Green
    private static final String COLOR_GOOD = "#3b82f6"; // Blue
    private static final String COLOR_FAIR = "#f59e0b"; // Amber
    private static final String COLOR_POOR = "#ef4444"; // Red

    public BacktestReportPanel() {
        reportContent = new VBox(12);
        reportContent.setPadding(new Insets(16));
        reportContent.setStyle("-fx-background-color: #0f3460;");

        setContent(reportContent);
        setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        setFitToWidth(true);
        LocalizationService.applyTranslations(this);
    }

    public void displayReport(@NonNull InstitutionalBacktestMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");

        // Add all sections
        reportContent.getChildren().setAll(
                createHeader(),
                createPerformanceSection(),
                createTradeStatisticsSection(),
                createRiskMetricsSection(),
                createAdvancedMetricsSection(),
                createConfidenceScoreSection(),
                createSummarySection());
        LocalizationService.applyTranslations(this);
    }

    private @NotNull VBox createHeader() {
        VBox header = new VBox(8);
        header.setStyle("-fx-border-color: #3b82f6; -fx-border-width: 0 0 2 0; -fx-padding: 0 0 12 0;");

        Label title = new Label("INSTITUTIONAL BACKTESTING REPORT");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#3b82f6"));

        Label subtitle = new Label("Professional-Grade Performance Analysis");
        subtitle.setFont(Font.font("Monospace", 11));
        subtitle.setTextFill(Color.web("#9ca3af"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private VBox createPerformanceSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("PERFORMANCE METRICS");
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-padding: 8;");

        int row = 0;
        addMetricRow(grid, row++, "Initial Balance", createMetricValue(formatCurrency(metrics.getInitialBalance())));

        addMetricRow(grid, row++, "Final Balance", createMetricValue(formatCurrency(metrics.getFinalBalance())));

        String returnStr = formatCurrency(metrics.getTotalReturn()) + " (" +
                String.format("%.2f%%", metrics.getTotalReturnPercent()) + ")";
        addMetricRow(grid, row++, "Total Return", createMetricValueColored(returnStr, metrics.getTotalReturn() >= 0));

        addMetricRow(grid, row+1, "Annualized Return", createMetricValue(String.format("%.2f%%", metrics.getAnnualizedReturn())));

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createTradeStatisticsSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("TRADE STATISTICS");
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-padding: 8;");

        int row = 0;
        addMetricRow(grid, row++, "Total Trades", createMetricValue(String.valueOf(metrics.getTotalTrades())));

        addMetricRow(grid, row++, "Winning Trades", createMetricValue(metrics.getWinningTrades() + " (" +
                String.format("%.1f%%", metrics.getWinRate()) + ")"));

        addMetricRow(grid, row++, "Losing Trades", createMetricValue(String.valueOf(metrics.getLosingTrades())));

        String avgStr = formatCurrency(metrics.getAvgWinSize()) + " / " +
                formatCurrency(metrics.getAvgLossSize());
        addMetricRow(grid, row++, "Avg Win / Loss", createMetricValue(avgStr));

        addMetricRow(grid, row++, "Profit Factor", createMetricValueColored(String.format("%.2f", metrics.getProfitFactor()),
                metrics.getProfitFactor() >= 1.5));

        addMetricRow(grid, row+1, "Expectancy / Trade", createMetricValue(formatCurrency(metrics.getExpectancy())));

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createRiskMetricsSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("RISK METRICS");
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-padding: 8;");

        int row = 0;
        String ddStr = String.format("%.2f%%", metrics.getMaxDrawdownPercent()) + " (" +
                formatCurrency(metrics.getMaxDrawdown()) + ")";
        addMetricRow(grid, row++, "Max Drawdown", createMetricValueColored(ddStr, metrics.getMaxDrawdownPercent() <= 20));

        addMetricRow(grid, row++, "Avg Drawdown", createMetricValue(String.format("%.2f%%", metrics.getAvgDrawdown())));

        addMetricRow(grid, row++, "Sharpe Ratio", createMetricValueColored(String.format("%.2f", metrics.getSharpeRatio()),
                metrics.getSharpeRatio() >= 1.0));

        addMetricRow(grid, row++, "Sortino Ratio", createMetricValueColored(String.format("%.2f", metrics.getSortinoRatio()),
                metrics.getSortinoRatio() >= 1.5));

        addMetricRow(grid, row++, "Calmar Ratio", createMetricValue(String.format("%.2f", metrics.getCalmarRatio())));

        addMetricRow(grid, row+1, "Recovery Factor", createMetricValueColored(String.format("%.2f", metrics.getRecoveryFactor()),
                metrics.getRecoveryFactor() >= 2.0));

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createAdvancedMetricsSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("ADVANCED METRICS");
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-padding: 8;");

        int row = 0;
        addMetricRow(grid, row++, "Max Consecutive Wins", createMetricValue(String.valueOf(metrics.getMaxConsecutiveWins())));

        addMetricRow(grid, row++, "Max Consecutive Losses", createMetricValueColored(String.valueOf(metrics.getMaxConsecutiveLosses()),
                metrics.getMaxConsecutiveLosses() <= 5));

        addMetricRow(grid, row++, "Profit Std Dev", createMetricValue(formatCurrency(metrics.getProfitStdDev())));

        addMetricRow(grid, row++, "Skewness", createMetricValueColored(String.format("%.2f", metrics.getSkewness()),
                metrics.getSkewness() > 0));

        addMetricRow(grid, row++, "Kurtosis", createMetricValue(String.format("%.2f", metrics.getKurtosis())));

        // The model returns signed lower-tail trade P/L, so negative values indicate losses.
        addMetricRow(grid, row++, "VaR (95%)", createMetricValueColored(formatCurrency(metrics.getVar95()),
                metrics.getVar95() >= 0));

        addMetricRow(grid, row+1, "CVaR (95%)", createMetricValueColored(formatCurrency(metrics.getCvar95()),
                metrics.getCvar95() >= 0));

        section.getChildren().addAll(title, grid);
        return section;
    }

    private @NonNull VBox createConfidenceScoreSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("STRATEGY CONFIDENCE SCORE");

        double confidenceScore = Math.clamp(metrics.getConfidenceScore(), 0.0, 100.0);
        ProgressBar progressBar = new ProgressBar(confidenceScore / 100.0);
        progressBar.setStyle("-fx-padding: 8; -fx-min-height: 30;");
        progressBar.setPrefWidth(300);

        Label scoreLabel = new Label(String.format("%.1f / 100.0", confidenceScore));
        scoreLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        scoreLabel.setTextFill(getColorForScore(confidenceScore));

        Label description = new Label(getScoreDescription(confidenceScore));
        description.setFont(Font.font("Monospace", 11));
        description.setTextFill(Color.web("#9ca3af"));
        description.setWrapText(true);

        section.getChildren().addAll(title, progressBar, scoreLabel, description);
        return section;
    }

    private VBox createSummarySection() {
        VBox section = new VBox(12);
        section.setStyle(
                "-fx-border-color: #3b82f6; -fx-border-width: 2 0 0 0; -fx-padding: 12 0 0 0;");

        Label title = new Label("ANALYSIS SUMMARY");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#3b82f6"));

        TextArea summary = new TextArea();
        summary.setText(metrics.getSummary());
        summary.setWrapText(true);
        summary.setEditable(false);
        summary.setPrefHeight(300);
        summary.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
                "-fx-control-inner-background: #1a2332; -fx-text-fill: #a0aec0;");

        section.getChildren().addAll(title, summary);
        return section;
    }

    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        label.setTextFill(Color.web("#3b82f6"));
        return label;
    }

    private void addMetricRow(GridPane grid, int row, String label, Label value) {
        grid.addRow(row, createMetricLabel(label), value);
    }

    private Label createMetricLabel(String text) {
        Label label = new Label(text + ":");
        label.setFont(Font.font("Monospace", 11));
        label.setTextFill(Color.web("#9ca3af"));
        return label;
    }

    private Label createMetricValue(String value) {
        Label label = new Label(value);
        label.setFont(Font.font("Monospace", FontWeight.SEMI_BOLD, 11));
        label.setTextFill(Color.web("#e5e7eb"));
        return label;
    }

    private Label createMetricValueColored(String value, boolean isPositive) {
        Label label = new Label(value);
        label.setFont(Font.font("Monospace", FontWeight.SEMI_BOLD, 11));
        label.setTextFill(Color.web(isPositive ? COLOR_EXCELLENT : COLOR_POOR));
        return label;
    }

    private Color getColorForScore(double score) {
        if (score >= 80)
            return Color.web(COLOR_EXCELLENT);
        if (score >= 60)
            return Color.web(COLOR_GOOD);
        if (score >= 40)
            return Color.web(COLOR_FAIR);
        return Color.web(COLOR_POOR);
    }

    private String getScoreDescription(double score) {
        if (score >= 80)
            return "Excellent - This strategy shows strong potential with good risk-adjusted returns";
        if (score >= 60)
            return "Good - This strategy demonstrates solid performance and acceptable risk management";
        if (score >= 40)
            return "Fair - This strategy shows promise but may need refinement in key areas";
        return "Poor - This strategy needs significant improvements in performance or risk management";
    }

    private String formatCurrency(double value) {
        return String.format("$%.2f", value);
    }
}
