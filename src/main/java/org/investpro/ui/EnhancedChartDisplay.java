package org.investpro.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.ChartHeaderTradingView;
import org.investpro.ui.charts.TechnicalIndicatorsPanel;
import org.investpro.ui.charts.TradeVisualizationOverlay;
import org.investpro.ui.charts.TradingViewEnhancedChart;
import org.investpro.ui.charts.TradingViewProfessionalToolbar;
import org.investpro.ui.charts.VolumeIndicatorPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Enhanced chart display integrating TradingView-like professional
 * visualization.
 * Wraps the basic CandleStickChart with professional UI components:
 * - Chart header with OHLCV data
 * - Technical indicators panel (right sidebar)
 * - Volume indicator panel (below chart)
 * - Professional toolbar with studies and drawing tools
 * - Trade visualization overlays
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Slf4j
public class EnhancedChartDisplay extends BorderPane {
    private static final String BG_COLOR = "#0a0e17";
    private static final String STYLE = "-fx-background-color: " + BG_COLOR + ";";

    private final Exchange exchange;
    private final TradePair tradePair;
    private final CandleStickChart candleStickChart;
    private final TradingService tradingService;

    private final ChartHeaderTradingView chartHeader;
    private final TechnicalIndicatorsPanel indicatorsPanel;
    private final VolumeIndicatorPanel volumePanel;
    private final TradingViewProfessionalToolbar professionalToolbar;
    private final TradeVisualizationOverlay tradeOverlay;

    private final VBox chartContentBox;
    private final VBox mainChartArea;

    public EnhancedChartDisplay(
            @NotNull Exchange exchange,
            @NotNull TradePair tradePair,
            @NotNull CandleStickChart candleStickChart,
            @NotNull TradingService tradingService) {

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.candleStickChart = candleStickChart;
        this.tradingService = tradingService;

        // Initialize components
        this.chartHeader = new ChartHeaderTradingView();
        this.indicatorsPanel = new TechnicalIndicatorsPanel();
        this.volumePanel = new VolumeIndicatorPanel();
        this.professionalToolbar = new TradingViewProfessionalToolbar();
        this.tradeOverlay = new TradeVisualizationOverlay();

        // Initialize layout containers
        this.chartContentBox = new VBox(4);
        this.mainChartArea = new VBox(0);

        initializeLayout();
        setupToolbarCallbacks();
        setupChartCallbacks();
    }

    private void initializeLayout() {
        setPrefSize(1200, 800);
        setStyle(STYLE);

        // Top: Professional toolbar with studies, drawing tools, etc.
        setTop(professionalToolbar);

        // Second row: Chart header with OHLCV data
        setCenter(createCenterLayout());

        // Styling
        setBorder(null);
    }

    private VBox createCenterLayout() {
        mainChartArea.setStyle(STYLE);
        mainChartArea.setSpacing(4);

        // Row 1: Chart header
        VBox headerBox = new VBox(chartHeader);
        headerBox.setStyle(STYLE);

        // Row 2: Main chart area with indicators sidebar
        VBox contentWithIndicators = createChartWithSidebar();
        VBox.setVgrow(contentWithIndicators, Priority.ALWAYS);

        // Row 3: Volume indicator
        volumePanel.setPrefHeight(100);
        volumePanel.setMinHeight(80);

        // Assemble main layout
        mainChartArea.getChildren().addAll(
                headerBox,
                contentWithIndicators,
                volumePanel);

        return mainChartArea;
    }

    private VBox createChartWithSidebar() {
        // Left: Candlestick chart
        VBox chartBox = new VBox(0);
        chartBox.setStyle(STYLE);
        chartBox.setPadding(new Insets(4));
        chartBox.getChildren().add(candleStickChart);
        VBox.setVgrow(candleStickChart, Priority.ALWAYS);

        // Assemble left+right (chart + indicators)
        javafx.scene.layout.HBox chartWithIndicators = new javafx.scene.layout.HBox(0);
        chartWithIndicators.setStyle(STYLE);
        chartWithIndicators.getChildren().addAll(chartBox, indicatorsPanel);
        javafx.scene.layout.HBox.setHgrow(chartBox, Priority.ALWAYS);

        chartContentBox.getChildren().add(chartWithIndicators);
        VBox.setVgrow(chartWithIndicators, Priority.ALWAYS);

        return chartContentBox;
    }

    private void setupToolbarCallbacks() {
        // Study selection
        professionalToolbar.setOnStudySelected(study -> {
            log.info("Study selected: {}", study);
            if ("None".equals(study)) {
                indicatorsPanel.clearIndicators();
            } else {
                // In real implementation, load actual study data
                // For now, just update UI
                updateIndicatorDisplay(study);
            }
        });

        // Drawing tool selection
        professionalToolbar.setOnDrawingToolSelected(tool -> {
            log.info("Drawing tool selected: {}", tool);
            // Implement drawing tool activation in chart
        });

        // Timeframe change
        professionalToolbar.setOnTimeframeChanged(timeframe -> {
            log.info("Timeframe changed: {}", timeframe);
            chartHeader.setTimeframe(timeframe);
        });

        // Chart type change
        professionalToolbar.setOnChartTypeChanged(type -> {
            log.info("Chart type changed: {}", type);
            // Switch between candle, bar, line chart types
        });

        // Zoom controls
        professionalToolbar.setOnZoomIn(() -> {
            log.info("Zoom in");
            if (candleStickChart != null) {
                candleStickChart.zoomIn();
            }
        });

        professionalToolbar.setOnZoomOut(() -> {
            log.info("Zoom out");
            if (candleStickChart != null) {
                candleStickChart.zoomOut();
            }
        });

        professionalToolbar.setOnZoomReset(() -> {
            log.info("Reset zoom");
            if (candleStickChart != null) {
                candleStickChart.resetZoom();
            }
        });
    }

    private void setupChartCallbacks() {
        // Update header when candlestick data updates
        if (candleStickChart != null) {
            candleStickChart.setCandleSelectionCallback(candle -> {
                updateChartHeaderFromCandle(candle);
            });
        }
    }

    private void updateChartHeaderFromCandle(@NotNull CandleData candle) {
        if (chartHeader == null) {
            return;
        }

        double change = candle.close() - candle.open();
        double changePercent = (change / candle.open()) * 100;

        chartHeader.updateWithCandle(
                tradePair.toString(),
                candle.close(),
                change,
                changePercent,
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                professionalToolbar.getSelectedTimeframe());
    }

    private void updateIndicatorDisplay(@NotNull String study) {
        // Update indicator values based on selected study
        // This is a placeholder - real implementation would calculate actual values
        Map<String, String> values = Map.of(
                "MA(20)", "—",
                "MA(50)", "—",
                "MA(200)", "—",
                "RSI(14)", "—",
                "MACD", "—");

        Map<String, String> colors = Map.of(
                "MA(20)", "#9aa7ba",
                "MA(50)", "#9aa7ba",
                "MA(200)", "#9aa7ba",
                "RSI(14)", "#9aa7ba",
                "MACD", "#9aa7ba");

        indicatorsPanel.updateIndicators(values, colors);
    }

    /**
     * Update chart header with specific values
     */
    public void updateHeader(String symbol, double price, double change, double changePercent,
            double open, double high, double low, double close, double volume) {
        chartHeader.updateWithCandle(symbol, price, change, changePercent, open, high, low, close, volume,
                professionalToolbar.getSelectedTimeframe());
    }

    /**
     * Update a technical indicator value
     */
    public void updateIndicator(@NotNull String name, @NotNull String value, String color) {
        indicatorsPanel.updateIndicator(name, value, color);
    }

    /**
     * Add volume bar to volume panel
     */
    public void addVolumeBar(@NotNull VolumeIndicatorPanel.VolumeBar bar) {
        volumePanel.addVolumeBar(bar);
    }

    /**
     * Add trade marker (entry/exit)
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
     * Update volume panel MA20
     */
    public void updateVolumeMA() {
        volumePanel.updateVolumeMA20();
    }

    /**
     * Clear all overlays and reset indicators
     */
    public void reset() {
        tradeOverlay.clear();
        indicatorsPanel.clearIndicators();
        volumePanel.clear();
    }

    /**
     * Dispose resources
     */
    public void dispose() {
        if (candleStickChart != null) {
            candleStickChart.dispose();
        }
        tradeOverlay.clear();
        volumePanel.clear();
    }
}
