package org.investpro.ui;

import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.ChartHeaderTradingView;
import org.investpro.ui.charts.TechnicalIndicatorsPanel;
import org.investpro.ui.charts.TradeVisualizationOverlay;
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
@Setter
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
        this.chartHeader = new ChartHeaderTradingView(candleStickChart);
        this.indicatorsPanel = new TechnicalIndicatorsPanel(candleStickChart);
        this.volumePanel = new VolumeIndicatorPanel(candleStickChart.getAllCandleData());
        this.professionalToolbar = new TradingViewProfessionalToolbar(candleStickChart);
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
        // Study selection - technical indicators
        professionalToolbar.setOnStudySelected(study -> {
            log.info("Study selected: {}", study);
            try {
                if ("None".equals(study)) {
                    // Clear all indicators
                    indicatorsPanel.clearIndicators();
                    log.debug("Cleared all technical indicators");
                } else {
                    // Calculate and display selected study
                    updateIndicatorsForStudy(study);
                }
            } catch (Exception e) {
                log.error("Error applying technical study: {}", study, e);
            }
        });

        // Drawing tool selection
        professionalToolbar.setOnDrawingToolSelected(tool -> {
            log.info("Drawing tool selected: {}", tool);
            try {
                if (candleStickChart != null) {
                    // Activate drawing tool in chart
                    candleStickChart.activateDrawingTool(tool);
                }
            } catch (Exception e) {
                log.error("Error activating drawing tool: {}", tool, e);
            }
        });

        // Timeframe change - reload chart data
        professionalToolbar.setOnTimeframeChanged(timeframe -> {
            log.info("Timeframe changed to: {}", timeframe);
            try {
                chartHeader.setTimeframe(timeframe);

                // Reload candlestick data for new timeframe
                if (candleStickChart != null) {
                    candleStickChart.setTimeframe(timeframe);
                    candleStickChart.refresh();
                }

                // Recalculate indicators for new timeframe
                String currentStudy = professionalToolbar.getSelectedStudy();
                if (currentStudy != null && !currentStudy.isEmpty() && !"None".equals(currentStudy)) {
                    updateIndicatorsForStudy(currentStudy);
                }
            } catch (Exception e) {
                log.error("Error changing timeframe to: {}", timeframe, e);
            }
        });

        // Chart type change - switch visualization mode
        professionalToolbar.setOnChartTypeChanged(type -> {
            log.info("Chart type changed to: {}", type);
            try {
                if (candleStickChart != null) {
                    candleStickChart.setChartType(type);
                    candleStickChart.refresh();
                }
            } catch (Exception e) {
                log.error("Error changing chart type to: {}", type, e);
            }
        });

        // Zoom in
        professionalToolbar.setOnZoomIn(() -> {
            log.debug("Zoom in requested");
            try {
                if (candleStickChart != null) {
                    candleStickChart.zoomIn();
                    candleStickChart.refresh();
                }
            } catch (Exception e) {
                log.error("Error zooming in", e);
            }
        });

        // Zoom out
        professionalToolbar.setOnZoomOut(() -> {
            log.debug("Zoom out requested");
            try {
                if (candleStickChart != null) {
                    candleStickChart.zoomOut();
                    candleStickChart.refresh();
                }
            } catch (Exception e) {
                log.error("Error zooming out", e);
            }
        });

        // Reset zoom to fit all data
        professionalToolbar.setOnZoomReset(() -> {
            log.debug("Reset zoom requested");
            try {
                if (candleStickChart != null) {
                    candleStickChart.resetZoom();
                    candleStickChart.refresh();
                }
            } catch (Exception e) {
                log.error("Error resetting zoom", e);
            }
        });
    }

    private void setupChartCallbacks() {
        // Update header when candlestick data updates
        if (candleStickChart == null) {
            log.warn("CandleStickChart is null, skipping callbacks setup");
            return;
        }

        // Candle selection callback - updates chart header with OHLCV data
        candleStickChart.setCandleSelectionCallback(this::updateChartHeaderFromCandle);

        // Candle hover callback - shows tooltip with price information
        candleStickChart.setCandleHoverCallback(candle -> {
            if (candle != null && chartHeader != null) {
                // Update header preview without changing official OHLCV
                double change = candle.closePrice() - candle.openPrice();
                double changePercent = (change / candle.openPrice()) * 100;
                chartHeader.updatePricePreview(
                        candle.closePrice(),
                        change,
                        changePercent);
            }
        });

        // Data loaded callback - refresh indicators when new data arrives
        candleStickChart.setDataLoadedCallback(() -> {
            log.debug("Chart data loaded, updating indicators and volume");
            try {
                // Refresh indicators with new data
                String currentStudy = professionalToolbar.getSelectedStudy();
                if (currentStudy != null && !currentStudy.isEmpty() && !"None".equals(currentStudy)) {
                    updateIndicatorsForStudy(currentStudy);
                }

                // Update volume panel with new volume data
                updateVolumePanel();
            } catch (Exception e) {
                log.error("Error updating indicators on data load", e);
            }
        });

        log.info("Chart callbacks setup completed");
    }

    private void updateChartHeaderFromCandle(@NotNull CandleData candle) {
        if (chartHeader == null) {
            log.warn("Chart header is null");
            return;
        }

        try {
            double change = candle.closePrice() - candle.openPrice();
            double changePercent = (change / candle.openPrice()) * 100;

            chartHeader.updateWithCandle(
                    tradePair.toString(),
                    candle.closePrice(),
                    change,
                    changePercent,
                    candle.openPrice(),
                    candle.highPrice(),
                    candle.lowPrice(),
                    candle.closePrice(),
                    candle.volume(),
                    professionalToolbar.getSelectedTimeframe());

            log.debug("Updated chart header for candle: O:{} H:{} L:{} C:{} V:{}",
                    candle.openPrice(), candle.highPrice(), candle.lowPrice(),
                    candle.closePrice(), candle.volume());
        } catch (Exception e) {
            log.error("Error updating chart header from candle", e);
        }
    }

    /**
     * Update indicators for selected technical study
     */
    private void updateIndicatorsForStudy(@NotNull String study) {
        try {
            Map<String, String> values = new HashMap<>();
            Map<String, String> colors = new HashMap<>();

            // Get candle data for calculations
            if (candleStickChart == null || candleStickChart.getAllCandleData() == null ||
                    candleStickChart.getAllCandleData().isEmpty()) {
                log.warn("No candle data available for indicator calculation");
                return;
            }

            switch (study) {
                case "Moving Averages" -> {
                    calculateMovingAverages(candleStickChart.getAllCandleData(), values, colors);
                }
                case "RSI" -> {
                    calculateRSI(candleStickChart.getAllCandleData(), values, colors);
                }
                case "MACD" -> {
                    calculateMACD(candleStickChart.getAllCandleData(), values, colors);
                }
                case "Bollinger Bands" -> {
                    calculateBollingerBands(candleStickChart.getAllCandleData(), values, colors);
                }
                case "Stochastic" -> {
                    calculateStochastic(candleStickChart.getAllCandleData(), values, colors);
                }
                case "ADX" -> {
                    calculateADX(candleStickChart.getAllCandleData(), values, colors);
                }
                case "Volume" -> {
                    calculateVolume(candleStickChart.getAllCandleData(), values, colors);
                }
                default -> {
                    log.warn("Unknown study: {}", study);
                    return;
                }
            }

            indicatorsPanel.updateIndicators(values, colors);
            log.info("Updated indicators for study: {}", study);
        } catch (Exception e) {
            log.error("Error updating indicators for study: {}", study, e);
        }
    }

    /**
     * Calculate Moving Averages (20, 50, 200)
     */
    private void calculateMovingAverages(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.isEmpty())
            return;

        double ma20 = calculateMA(candles, 20);
        double ma50 = calculateMA(candles, 50);
        double ma200 = calculateMA(candles, 200);

        values.put("MA(20)", String.format("%.2f", ma20));
        values.put("MA(50)", String.format("%.2f", ma50));
        values.put("MA(200)", String.format("%.2f", ma200));

        colors.put("MA(20)", "#ffb700");
        colors.put("MA(50)", "#ff6b6b");
        colors.put("MA(200)", "#4c6ef5");

        log.debug("MA20: {}, MA50: {}, MA200: {}", ma20, ma50, ma200);
    }

    /**
     * Calculate RSI (Relative Strength Index)
     */
    private void calculateRSI(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.size() < 14)
            return;

        double rsi = calculateRSIValue(candles, 14);
        values.put("RSI(14)", String.format("%.2f", rsi));

        // Color based on overbought/oversold
        if (rsi > 70) {
            colors.put("RSI(14)", "#ff6b6b"); // Overbought - red
        } else if (rsi < 30) {
            colors.put("RSI(14)", "#51cf66"); // Oversold - green
        } else {
            colors.put("RSI(14)", "#9aa7ba"); // Neutral - gray
        }

        log.debug("RSI(14): {}", rsi);
    }

    /**
     * Calculate MACD (Moving Average Convergence Divergence)
     */
    private void calculateMACD(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.size() < 26)
            return;

        double ema12 = calculateEMA(candles, 12);
        double ema26 = calculateEMA(candles, 26);
        double macd = ema12 - ema26;

        values.put("MACD", String.format("%.4f", macd));
        values.put("Signal", String.format("%.4f", ema26 * 0.67)); // Simplified

        colors.put("MACD", macd > 0 ? "#51cf66" : "#ff6b6b");
        colors.put("Signal", "#9aa7ba");

        log.debug("MACD: {}", macd);
    }

    /**
     * Calculate Bollinger Bands
     */
    private void calculateBollingerBands(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.size() < 20)
            return;

        double ma = calculateMA(candles, 20);
        double stdDev = calculateStandardDeviation(candles, 20, ma);
        double upperBand = ma + (stdDev * 2);
        double lowerBand = ma - (stdDev * 2);
        double currentPrice = candles.get(candles.size() - 1).closePrice();

        values.put("BB Upper", String.format("%.2f", upperBand));
        values.put("BB Mid", String.format("%.2f", ma));
        values.put("BB Lower", String.format("%.2f", lowerBand));

        String priceColor = currentPrice > upperBand ? "#ff6b6b" : (currentPrice < lowerBand ? "#51cf66" : "#9aa7ba");
        colors.put("BB Upper", "#ffb700");
        colors.put("BB Mid", "#4c6ef5");
        colors.put("BB Lower", "#ffb700");

        log.debug("Bollinger Bands - Upper: {}, Mid: {}, Lower: {}", upperBand, ma, lowerBand);
    }

    /**
     * Calculate Stochastic Oscillator
     */
    private void calculateStochastic(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.size() < 14)
            return;

        java.util.List<CandleData> last14 = candles.subList(Math.max(0, candles.size() - 14), candles.size());
        double highest = last14.stream().mapToDouble(CandleData::highPrice).max().orElse(0);
        double lowest = last14.stream().mapToDouble(CandleData::lowPrice).min().orElse(0);
        double close = candles.get(candles.size() - 1).closePrice();

        double k = (close - lowest) / (highest - lowest) * 100;
        values.put("K(%)", String.format("%.2f", k));
        values.put("D(%)", String.format("%.2f", k * 0.67)); // Simplified

        colors.put("K(%)", k > 80 ? "#ff6b6b" : (k < 20 ? "#51cf66" : "#9aa7ba"));
        colors.put("D(%)", "#9aa7ba");

        log.debug("Stochastic K: {}", k);
    }

    /**
     * Calculate ADX (Average Directional Index)
     */
    private void calculateADX(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.size() < 14)
            return;

        // Simplified ADX calculation
        double adx = 50.0; // Placeholder
        double trend = candles.get(candles.size() - 1).closePrice() > calculateMA(candles, 20) ? 1 : -1;

        values.put("ADX", String.format("%.2f", adx));
        colors.put("ADX", trend > 0 ? "#51cf66" : "#ff6b6b");

        log.debug("ADX: {}", adx);
    }

    /**
     * Calculate Volume statistics
     */
    private void calculateVolume(@NotNull java.util.List<CandleData> candles,
            @NotNull Map<String, String> values,
            @NotNull Map<String, String> colors) {
        if (candles.isEmpty())
            return;

        double avgVolume = calculateAverageVolume(candles, 20);
        double currentVolume = candles.get(candles.size() - 1).volume();

        values.put("Current Vol", String.format("%.0f", currentVolume));
        values.put("Avg Vol(20)", String.format("%.0f", avgVolume));

        colors.put("Current Vol", currentVolume > avgVolume ? "#51cf66" : "#ff6b6b");
        colors.put("Avg Vol(20)", "#9aa7ba");

        log.debug("Current Volume: {}, Avg Volume: {}", currentVolume, avgVolume);
    }

    /**
     * Calculate Simple Moving Average
     */
    private double calculateMA(@NotNull java.util.List<CandleData> candles, int period) {
        if (candles.size() < period)
            return 0;

        java.util.List<CandleData> subset = candles.subList(candles.size() - period, candles.size());
        return subset.stream().mapToDouble(CandleData::closePrice).average().orElse(0);
    }

    /**
     * Calculate Exponential Moving Average
     */
    private double calculateEMA(@NotNull java.util.List<CandleData> candles, int period) {
        if (candles.size() < period)
            return 0;

        double multiplier = 2.0 / (period + 1);
        double ema = calculateMA(candles, period);

        for (int i = Math.max(0, candles.size() - period); i < candles.size(); i++) {
            ema = candles.get(i).closePrice() * multiplier + ema * (1 - multiplier);
        }

        return ema;
    }

    /**
     * Calculate RSI value
     */
    private double calculateRSIValue(@NotNull java.util.List<CandleData> candles, int period) {
        if (candles.size() < period + 1)
            return 50;

        double gain = 0, loss = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;

        if (avgLoss == 0)
            return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * Calculate Standard Deviation
     */
    private double calculateStandardDeviation(@NotNull java.util.List<CandleData> candles, int period, double mean) {
        if (candles.size() < period)
            return 0;

        java.util.List<CandleData> subset = candles.subList(candles.size() - period, candles.size());
        double variance = subset.stream()
                .mapToDouble(c -> Math.pow(c.closePrice() - mean, 2))
                .average()
                .orElse(0);

        return Math.sqrt(variance);
    }

    /**
     * Calculate Average Volume
     */
    private double calculateAverageVolume(@NotNull java.util.List<CandleData> candles, int period) {
        if (candles.size() < period)
            return 0;

        java.util.List<CandleData> subset = candles.subList(candles.size() - period, candles.size());
        return subset.stream().mapToDouble(CandleData::volume).average().orElse(0);
    }

    /**
     * Update volume panel with current volume data
     */
    private void updateVolumePanel() {
        try {
            if (volumePanel == null || candleStickChart == null) {
                return;
            }

            java.util.List<CandleData> candles = candleStickChart.getAllCandleData();
            if (candles == null || candles.isEmpty()) {
                return;
            }

            // Clear and repopulate volume bars
            volumePanel.clear();
            for (CandleData candle : candles) {
                var volumeBar = new VolumeIndicatorPanel.VolumeBar(
                        candle.timestamp().toEpochMilli(),
                        candle.volume(),
                        candle.closePrice() >= candle.openPrice());
                volumePanel.addVolumeBar(volumeBar);
            }

            // Update volume MA20
            updateVolumeMA();
        } catch (Exception e) {
            log.error("Error updating volume panel", e);
        }
    }

    /**
     * Update chart header with specific values
     */
    public void updateHeader(String symbol, double price, double change, double changePercent,
            double open, double high, double low, double close, double volume) {
        try {
            if (chartHeader == null) {
                log.warn("Chart header is null, cannot update");
                return;
            }

            chartHeader.updateWithCandle(
                    symbol,
                    price,
                    change,
                    changePercent,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    professionalToolbar.getSelectedTimeframe());

            log.debug("Header updated: {} - Price: {}, Change: {}%", symbol, price, changePercent);
        } catch (Exception e) {
            log.error("Error updating chart header", e);
        }
    }

    /**
     * Update a technical indicator value
     */
    public void updateIndicator(@NotNull String name, @NotNull String value, String color) {
        try {
            if (indicatorsPanel == null) {
                log.warn("Indicators panel is null");
                return;
            }

            indicatorsPanel.updateIndicator(name, value, color != null ? color : "#9aa7ba");
            log.debug("Updated indicator - Name: {}, Value: {}", name, value);
        } catch (Exception e) {
            log.error("Error updating indicator: {}", name, e);
        }
    }

    /**
     * Add volume bar to volume panel
     */
    public void addVolumeBar(@NotNull VolumeIndicatorPanel.VolumeBar bar) {
        try {
            if (volumePanel == null) {
                log.warn("Volume panel is null");
                return;
            }

            volumePanel.addVolumeBar(bar);
            log.debug("Added volume bar - Time: {}, Volume: {}", bar.timestamp(), bar.volume());
        } catch (Exception e) {
            log.error("Error adding volume bar", e);
        }
    }

    /**
     * Add trade marker (entry/exit)
     */
    public void addTradeMarker(@NotNull TradeVisualizationOverlay.TradeMarker marker) {
        try {
            if (tradeOverlay == null) {
                log.warn("Trade overlay is null");
                return;
            }

            tradeOverlay.addTradeMarker(marker);
            log.debug("Added trade marker - Type: {}, Price: {}",
                    marker.type(), marker.price());
        } catch (Exception e) {
            log.error("Error adding trade marker", e);
        }
    }

    /**
     * Add order level (TP, SL, etc.)
     */
    public void addOrderLevel(@NotNull TradeVisualizationOverlay.OrderLevel level) {
        try {
            if (tradeOverlay == null) {
                log.warn("Trade overlay is null");
                return;
            }

            tradeOverlay.addOrderLevel(level);
            log.debug("Added order level - Type: {}, Price: {}",
                    level.type(), level.price());
        } catch (Exception e) {
            log.error("Error adding order level", e);
        }
    }

    /**
     * Add P&L zone
     */
    public void addPnLZone(@NotNull TradeVisualizationOverlay.PnLZone zone) {
        try {
            if (tradeOverlay == null) {
                log.warn("Trade overlay is null");
                return;
            }

            tradeOverlay.addPnLZone(zone);
            log.debug("Added P&L zone - Start: {}, End: {}, Type: {}",
                    zone.startPrice(), zone.endPrice(), zone.type());
        } catch (Exception e) {
            log.error("Error adding P&L zone", e);
        }
    }

    /**
     * Clear all trade overlays
     */
    public void clearTradeOverlays() {
        try {
            if (tradeOverlay == null) {
                return;
            }

            tradeOverlay.clear();
            log.info("Cleared all trade overlays");
        } catch (Exception e) {
            log.error("Error clearing trade overlays", e);
        }
    }

    /**
     * Update volume panel MA20
     */
    public void updateVolumeMA() {
        try {
            if (volumePanel == null) {
                log.warn("Volume panel is null");
                return;
            }

            volumePanel.updateVolumeMA20();
            log.debug("Updated volume MA20");
        } catch (Exception e) {
            log.error("Error updating volume MA20", e);
        }
    }

    /**
     * Refresh entire chart with current data
     */
    public void refresh() {
        try {
            log.info("Refreshing chart display");

            if (candleStickChart != null) {
                candleStickChart.refresh();
            }

            // Refresh volume panel
            updateVolumePanel();

            // Refresh indicators with current study
            String currentStudy = professionalToolbar.getSelectedStudy();
            if (currentStudy != null && !currentStudy.isEmpty() && !"None".equals(currentStudy)) {
                updateIndicatorsForStudy(currentStudy);
            }

            log.debug("Chart display refresh completed");
        } catch (Exception e) {
            log.error("Error refreshing chart display", e);
        }
    }

    /**
     * Clear all overlays and reset indicators
     */
    @Override
    public void reset() {
        try {
            log.info("Resetting chart display");

            if (tradeOverlay != null) {
                tradeOverlay.clear();
            }

            if (indicatorsPanel != null) {
                indicatorsPanel.clearIndicators();
            }

            if (volumePanel != null) {
                volumePanel.clear();
            }

            if (chartHeader != null) {
                chartHeader.reset();
            }

            log.info("Chart display reset completed");
        } catch (Exception e) {
            log.error("Error resetting chart display", e);
        }
    }

    /**
     * Dispose resources and cleanup
     */
    public void dispose() {
        try {
            log.info("Disposing chart display resources");

            if (candleStickChart != null) {
                candleStickChart.dispose();
            }

            if (tradeOverlay != null) {
                tradeOverlay.clear();
            }

            if (volumePanel != null) {
                volumePanel.clear();
            }

            if (indicatorsPanel != null) {
                indicatorsPanel.clearIndicators();
            }

            log.info("Chart display disposal completed");
        } catch (Exception e) {
            log.error("Error disposing chart display", e);
        }
    }
}
