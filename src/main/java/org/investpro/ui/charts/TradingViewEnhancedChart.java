package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.jetbrains.annotations.NotNull;

/**
 * TradingView-enhanced professional chart display with modern layout.
 * Integrates candlestick chart, technical indicators, trade visualization,
 * and professional header for a complete trading dashboard.
 *
 * Features:
 * - Professional TradingView-like chart header with OHLCV data
 * - Real-time candlestick chart with volume
 * - Side panel with technical indicators (MA, RSI, MACD, Bollinger Bands, etc.)
 * - Trade visualization overlays (entry/exit markers, TP/SL lines)
 * - Dark professional theme matching platform design system
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
@Slf4j
public class TradingViewEnhancedChart extends BorderPane {
    private static final String BACKGROUND_COLOR = "#0a0e17";
    private static final String CHART_STYLE = "-fx-background-color: " + BACKGROUND_COLOR + ";";

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final int secondsPerCandle;
    private final String telegramToken;
    private final TradingService tradingService;

    // UI Components
    private final ChartHeaderTradingView chartHeader;
    private final CandleStickChart candleStickChart;
    private final TechnicalIndicatorsPanel technicalIndicatorsPanel;
    private final TradeVisualizationOverlay tradeOverlay;

    private final VBox chartContainer;
    private final VBox mainLayout;

    public TradingViewEnhancedChart(
            @NotNull Exchange exchange,
            @NotNull TradePair tradePair,
            boolean liveSyncing,
            int secondsPerCandle,
            String telegramToken,
            @NotNull CandleStickChart candleStickChart,
            @NotNull TradingService tradingService) {

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.liveSyncing = liveSyncing;
        this.secondsPerCandle = secondsPerCandle;
        this.telegramToken = telegramToken;
        this.candleStickChart = candleStickChart;
        this.tradingService = tradingService;

        // Initialize components
        this.chartHeader = new ChartHeaderTradingView();
        this.technicalIndicatorsPanel = new TechnicalIndicatorsPanel();
        this.tradeOverlay = new TradeVisualizationOverlay();

        // Initialize UI
        this.chartContainer = new VBox(8);
        this.mainLayout = new VBox(0);

        initializeUI();
    }

    private void initializeUI() {
        setPrefSize(1000, 700);
        setStyle(CHART_STYLE);

        // Top: Chart Header
        setTop(chartHeader);

        // Center: Chart + Indicators Side Panel
        initializeCenterLayout();

        // Styling
        setBorder(null);
    }

    private void initializeCenterLayout() {
        mainLayout.setPrefHeight(600);
        mainLayout.setStyle(CHART_STYLE);

        // Left side: Chart with toolbar
        VBox chartBox = new VBox(4);
        chartBox.setPadding(new Insets(8));
        chartBox.setStyle(CHART_STYLE);
        chartBox.getChildren().add(candleStickChart);
        VBox.setVgrow(candleStickChart, Priority.ALWAYS);

        // Right side: Technical Indicators
        technicalIndicatorsPanel.setPrefWidth(280);
        technicalIndicatorsPanel.setMinWidth(280);
        technicalIndicatorsPanel.setMaxWidth(280);

        // Add to main layout
        mainLayout.getChildren().addAll(chartBox, technicalIndicatorsPanel);
        HBox.setHgrow(chartBox, Priority.ALWAYS);

        setCenter(mainLayout);
    }

    /**
     * Update chart header with current candle data
     */
    public void updateChartHeader(String symbol, double price, double change, double changePercent,
            double open, double high, double low, double close, double volume, String timeframe) {
        chartHeader.updateWithCandle(symbol, price, change, changePercent,
                open, high, low, close, volume, timeframe);
    }

    /**
     * Update technical indicator
     */
    public void updateIndicator(@NotNull String indicatorName, @NotNull String value, String signalColor) {
        technicalIndicatorsPanel.updateIndicator(indicatorName, value, signalColor);
    }

    /**
     * Update multiple indicators
     */
    public void updateIndicators(@NotNull java.util.Map<String, String> values,
            @NotNull java.util.Map<String, String> colors) {
        technicalIndicatorsPanel.updateIndicators(values, colors);
    }

    /**
     * Add trade marker (entry/exit point)
     */
    public void addTradeMarker(@NotNull TradeVisualizationOverlay.TradeMarker marker) {
        tradeOverlay.addTradeMarker(marker);
    }

    /**
     * Add order level (TP, SL, etc.)
     */
    public void addOrderLevel(@NotNull TradeVisualizationOverlay.OrderLevel level) {
        tradeOverlay.addOrderLevel(level);
    }

    /**
     * Add P&L zone
     */
    public void addPnLZone(@NotNull TradeVisualizationOverlay.PnLZone zone) {
        tradeOverlay.addPnLZone(zone);
    }

    /**
     * Clear all trade overlays
     */
    public void clearTradeOverlays() {
        tradeOverlay.clear();
    }

    /**
     * Get the underlying candlestick chart
     */
    public CandleStickChart getChart() {
        return candleStickChart;
    }

    /**
     * Get trade visualization overlay for custom rendering
     */
    public TradeVisualizationOverlay getTradeOverlay() {
        return tradeOverlay;
    }

    /**
     * Get technical indicators panel
     */
    public TechnicalIndicatorsPanel getIndicators() {
        return technicalIndicatorsPanel;
    }

    /**
     * Get chart header
     */
    public ChartHeaderTradingView getChartHeader() {
        return chartHeader;
    }

    /**
     * Dispose resources
     */
    public void dispose() {
        if (candleStickChart != null) {
            candleStickChart.dispose();
        }
        tradeOverlay.clear();
    }
}
