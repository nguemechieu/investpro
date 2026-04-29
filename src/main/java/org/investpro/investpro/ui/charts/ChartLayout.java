package org.investpro.investpro.ui.charts;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.layout.Region;
import lombok.Getter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.models.TradePair;

import java.io.IOException;

@Getter
public class ChartLayout extends Region {

    private final CandleStickChart chart;

    public ChartLayout(Exchange exchange,
                       TradePair tradePair,
                       CandleDataSupplier candleDataSupplier,
                       boolean liveSyncing,
                       int secondsPerCandle,
                       ReadOnlyDoubleProperty widthProperty,
                       ReadOnlyDoubleProperty heightProperty,
                       String token) {
        try {
            this.chart = new CandleStickChart(
                    exchange,
                    tradePair,
                    candleDataSupplier,
                    liveSyncing,
                    secondsPerCandle,
                    widthProperty,
                    heightProperty,
                    token
            );
            chart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            getChildren().setAll(chart);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create chart layout", e);
        }
    }

    @Override
    protected void layoutChildren() {
        chart.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    @Override
    protected double computePrefWidth(double height) {
        return chart.prefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return chart.prefHeight(width);
    }

    public void shutdown() {
        chart.shutdown();
    }
}
