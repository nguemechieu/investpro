package com.ynz.demo;

import com.ynz.demo.timeseries.PriceVolumeCandleChart;

public class ShowPriceVolumeChart {

    public static void main(String[] args) {
        PriceVolumeCandleChart timeSeriesChartFXDemo = new PriceVolumeCandleChart("demo a candle chart");
        timeSeriesChartFXDemo.pack();
        timeSeriesChartFXDemo.setVisible(true);
    }

}
