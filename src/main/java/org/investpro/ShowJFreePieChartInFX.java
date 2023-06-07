package com.ynz.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.data.general.DefaultPieDataset;

public class ShowJFreePieChartInFX extends Application {


    private @NotNull JFreeChart createJFreePieChart() {
        DefaultPieDataset dataSet = new DefaultPieDataset();
        dataSet.setValue("China", 1344.0);
        dataSet.setValue("India", 1241.0);
        dataSet.setValue("United States", 310.5);

        return ChartFactory.createPieChart(
                "Population 2011", dataSet, true, true, false
        );

    }

    @Override
    public void start(Stage stage) throws Exception {
        ChartViewer pieViewer = new ChartViewer(createJFreePieChart());

        Scene scene = new Scene(pieViewer, 400, 200);
        stage.setScene(scene);
        stage.setWidth(800);
        stage.setHeight(400);
        stage.setTitle("Show JFreePie in FX");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
