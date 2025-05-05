package org.investpro.investpro.ui.chart;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleChartAIManager;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.CandleStickChartToolbar;

import java.io.IOException;
import java.time.Instant;
import java.util.List;


@Getter
@Setter
public class ChartLayout extends Region {


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


        //   this.themeManager = new ThemeManager(new Scene(this));

        try {
            this.chart = new CandleStickChart(exchange, tradePair, candleDataSupplier, liveSyncing, secondsPerCandle, widthProperty, heightProperty, token);

            //  themeManager.applyDarkTheme();

//
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


}