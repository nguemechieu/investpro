package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.backtesting.InstitutionalBacktestMetrics;
import org.investpro.i18n.LocalizationService;
import org.jetbrains.annotations.NotNull;

/**
 * Professional backtesting report panel displaying institutional-grade metrics.
 * Shows comprehensive performance analysis with color-coded indicators.
 */
@Slf4j
@Getter
@Setter
public class BacktestReportPanel extends ScrollPane {

    private final VBox reportContent;
    private InstitutionalBacktestMetrics metrics;

    // Color scheme for metrics
    private static final String COLOR_EXCELLENT = "#10b981"; // Green
    private static final String COLOR_GOOD = "#3b82f6"; // Blue
    private static final String COLOR_FAIR = "#f59e0b"; // Amber
    private static final String COLOR_POOR = "#ef4444"; // Red
    private static final String COLOR_NEUTRAL = "#6b7280"; // Gray

    public BacktestReportPanel() {
        reportContent = new VBox(12);
        reportContent.setPadding(new Insets(16));
        reportContent.setStyle("-fx-background-color: #0f3460;");

        setContent(reportContent);
        setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        setFitToWidth(true);
        LocalizationService.applyTranslations(this);
    }

    public void displayReport(InstitutionalBacktestMetrics metrics) {
        this.metrics = metrics;
        reportContent.getChildren().clear();

        // Add all sections
        reportContent.getChildren().addAll(
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
        grid.add(createMetricLabel("Initial Balance"), 0, row);
        grid.add(createMetricValue(formatCurrency(metrics.getInitialBalance())), 1, row++);

        grid.add(createMetricLabel("Final Balance"), 0, row);
        grid.add(createMetricValue(formatCurrency(metrics.getFinalBalance())), 1, row++);

        grid.add(createMetricLabel("Total Return"), 0, row);
        String returnStr = formatCurrency(metrics.getTotalReturn()) + " (" +
                String.format("%.2f%%", metrics.getTotalReturnPercent()) + ")";
        grid.add(createMetricValueColored(returnStr, metrics.getTotalReturn() >= 0), 1, row++);

        grid.add(createMetricLabel("Annualized Return"), 0, row);
        grid.add(createMetricValue(String.format("%.2f%%", metrics.getAnnualizedReturn())), 1, row++);

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
        grid.add(createMetricLabel("Total Trades"), 0, row);
        grid.add(createMetricValue(String.valueOf(metrics.getTotalTrades())), 1, row++);

        grid.add(createMetricLabel("Winning Trades"), 0, row);
        grid.add(createMetricValue(metrics.getWinningTrades() + " (" +
                String.format("%.1f%%", metrics.getWinRate()) + ")"), 1, row++);

        grid.add(createMetricLabel("Losing Trades"), 0, row);
        grid.add(createMetricValue(String.valueOf(metrics.getLosingTrades())), 1, row++);

        grid.add(createMetricLabel("Avg Win / Loss"), 0, row);
        String avgStr = formatCurrency(metrics.getAvgWinSize()) + " / " +
                formatCurrency(metrics.getAvgLossSize());
        grid.add(createMetricValue(avgStr), 1, row++);

        grid.add(createMetricLabel("Profit Factor"), 0, row);
        grid.add(createMetricValueColored(String.format("%.2f", metrics.getProfitFactor()),
                metrics.getProfitFactor() >= 1.5), 1, row++);

        grid.add(createMetricLabel("Expectancy / Trade"), 0, row);
        grid.add(createMetricValue(formatCurrency(metrics.getExpectancy())), 1, row++);

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
        grid.add(createMetricLabel("Max Drawdown"), 0, row);
        String ddStr = String.format("%.2f%%", metrics.getMaxDrawdownPercent()) + " (" +
                formatCurrency(metrics.getMaxDrawdown()) + ")";
        grid.add(createMetricValueColored(ddStr, metrics.getMaxDrawdownPercent() <= 20), 1, row++);

        grid.add(createMetricLabel("Avg Drawdown"), 0, row);
        grid.add(createMetricValue(String.format("%.2f%%", metrics.getAvgDrawdown())), 1, row++);

        grid.add(createMetricLabel("Sharpe Ratio"), 0, row);
        grid.add(createMetricValueColored(String.format("%.2f", metrics.getSharpeRatio()),
                metrics.getSharpeRatio() >= 1.0), 1, row++);

        grid.add(createMetricLabel("Sortino Ratio"), 0, row);
        grid.add(createMetricValueColored(String.format("%.2f", metrics.getSortinoRatio()),
                metrics.getSortinoRatio() >= 1.5), 1, row++);

        grid.add(createMetricLabel("Calmar Ratio"), 0, row);
        grid.add(createMetricValue(String.format("%.2f", metrics.getCalmarRatio())), 1, row++);

        grid.add(createMetricLabel("Recovery Factor"), 0, row);
        grid.add(createMetricValueColored(String.format("%.2f", metrics.getRecoveryFactor()),
                metrics.getRecoveryFactor() >= 2.0), 1, row++);

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
        grid.add(createMetricLabel("Max Consecutive Wins"), 0, row);
        grid.add(createMetricValue(String.valueOf(metrics.getMaxConsecutiveWins())), 1, row++);

        grid.add(createMetricLabel("Max Consecutive Losses"), 0, row);
        grid.add(createMetricValueColored(String.valueOf(metrics.getMaxConsecutiveLosses()),
                metrics.getMaxConsecutiveLosses() <= 5), 1, row++);

        grid.add(createMetricLabel("Profit Std Dev"), 0, row);
        grid.add(createMetricValue(formatCurrency(metrics.getProfitStdDev())), 1, row++);

        grid.add(createMetricLabel("Skewness"), 0, row);
        grid.add(createMetricValueColored(String.format("%.2f", metrics.getSkewness()),
                metrics.getSkewness() > 0), 1, row++);

        grid.add(createMetricLabel("Kurtosis"), 0, row);
        grid.add(createMetricValue(String.format("%.2f", metrics.getKurtosis())), 1, row++);

        grid.add(createMetricLabel("VaR (95%)"), 0, row);
        grid.add(createMetricValueColored(formatCurrency(metrics.getVar95()),
                metrics.getVar95() >= 0), 1, row++);

        grid.add(createMetricLabel("CVaR (95%)"), 0, row);
        grid.add(createMetricValueColored(formatCurrency(metrics.getCvar95()),
                metrics.getCvar95() >= 0), 1, row++);

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createConfidenceScoreSection() {
        VBox section = new VBox(12);
        section.setStyle("-fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 12;");

        Label title = createSectionTitle("STRATEGY CONFIDENCE SCORE");

        double confidenceScore = metrics.getConfidenceScore();
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
                "-fx-border-color: #3b82f6; -fx-border-width: 2 0 0 0; -fx-padding: 12 0 0 0; -fx-margin: 12 0 0 0;");

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
