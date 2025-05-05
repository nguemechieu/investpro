package org.investpro.investpro.ui.chart;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.*;
import org.investpro.investpro.ai.*;

import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.CandleStickChartToolbar;
import org.investpro.investpro.ui.chart.overlay.RSIOverlay;
import org.investpro.investpro.ui.chart.overlay.SMAOverlay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ChartLayout extends Region {

    private final Group drawingLayer;
    CandleStickChart chart;
    private CandleStickChart candleStickChart;
    private Button exportButton;

    private ChartSettingsPanel chartSettingsPanel;
    private List<CandleData> loadedCandles;
    private String currentInterval;
    private String currentSymbol;
    private PaginationManager paginationManager;
    private IndicatorManager indicatorManager;

    private Exchange exchange;
    private CandleChartDataLoader dataLoader;
    private CandleChartNavigator navigator;
    private double baseCandleWidth = 5.0;
    private LiveUpdateManager liveUpdateManager;
    private CandleStickChartToolbar chartToolbar;
    private Instant currentStartTime;
    private Instant currentEndTime;
    private ChartPerformanceOptimizer optimizer;
    private ThemeManager themeManager;
    private ChartAlertsManager alertsManager;
    private CandleData lastCandle;

    private CandleChartAIManager aiManager;


    public ChartLayout(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier, boolean liveSyncing, int secondsPerCandle, ReadOnlyDoubleProperty widthProperty, ReadOnlyDoubleProperty heightProperty, String token) {

        this.exchange = exchange;
        this.drawingLayer = new Group();

        this.themeManager = new ThemeManager(new Scene(this));

        try {
            this.chart = new CandleStickChart(exchange, tradePair, candleDataSupplier, liveSyncing, secondsPerCandle, widthProperty, heightProperty, token);

            //  themeManager.applyDarkTheme();


//            this.paginationManager = new PaginationManager(
//                    chart,
//                    (oldestTime, limit) -> dataLoader.loadMoreCandles(currentSymbol, oldestTime, currentInterval, limit),
//                    this::appendCandles
//            );


            getChildren().addAll(chart
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void toggleIndicators() {
    }

    private void resetAllSettings() {
    }

    private void appendCandles(List<CandleData> candleData) {
    }
}