package com.ynz.demo.timeseries;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;

public class ShowPVChartInFX extends Application {
    private final PriceVolumeCandleChart priceVolumeChart;

    {
        priceVolumeChart = new PriceVolumeCandleChart("Price/Volume in FX");
    }

    @Override
    public void start(@NotNull Stage stage) throws Exception {
        stage.setTitle("Show PV in FX");

        JFreeChart chart = priceVolumeChart.createPriceVolumeCombinedChart("Price/Volume Chart");
        ChartViewer viewer = new ChartViewer(chart);
        Scene scene = new Scene(viewer);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
