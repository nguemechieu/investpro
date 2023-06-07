package com.ynz.demo.timeseries;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.general.Series;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class PriceVolumeCandleChart extends ApplicationFrame {

    private final Map<String, Series> fakedPriceVolumeDataSet;

    {
        fakedPriceVolumeDataSet = createPriceVolumeDataSet();
    }

    public PriceVolumeCandleChart(String title) {
        super(title);

        JFreeChart chart = createPriceVolumeCombinedChart("combined price and volume");

        //create a chart panel to hold the chart; ChartPanel is a JPanel
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(1200, 500));

        //enable zooming via chartPanel
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setMouseZoomable(true);

        add(chartPanel, BorderLayout.CENTER);
    }

    public JFreeChart createPriceVolumeCombinedChart(String title) {
        //price subplot
        OHLCSeriesCollection priceDateCollection = new OHLCSeriesCollection();
        priceDateCollection.addSeries((OHLCSeries) fakedPriceVolumeDataSet.get("priceDateData"));

        CandlestickRenderer candlestickRenderer = new CandlestickRenderer();
        NumberAxis priceAxis = new NumberAxis("price");
        XYPlot pricePlot = new XYPlot(priceDateCollection, null, priceAxis, candlestickRenderer);

        //volume subplot
        TimeSeriesCollection volumeDateCollection = new TimeSeriesCollection();
        volumeDateCollection.addSeries((TimeSeries) fakedPriceVolumeDataSet.get("volumeDateData"));

        XYBarRenderer barRenderer = new XYBarRenderer();
        NumberAxis volumeAxis = new NumberAxis("volume");
        XYPlot volumePlot = new XYPlot(volumeDateCollection, null, volumeAxis, barRenderer);

        //combine price and volume into a single plot
        DateAxis dateAxis = new DateAxis("Trade Day");
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(dateAxis);
        combinedPlot.add(pricePlot, 3);
        combinedPlot.add(volumePlot, 1);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);

        return new JFreeChart(title, combinedPlot);
    }

    public Map<String, Series> createPriceVolumeDataSet() {

        Map<String, Series> resultMap = new HashMap<>();

        //price candleStick data series, including day, high, low, open, close, volume
        OHLCSeries priceDateSeries = new OHLCSeries("Price");

        //volume bar data series, including day and volume
        TimeSeries volumeDateSeries = new TimeSeries("volume");

        final Day[] days = new Day[47];
        final double[] high = new double[47];
        final double[] low = new double[47];
        final double[] open = new double[47];
        final double[] close = new double[47];
        final double[] volume = new double[47];

        final int jan = 1;
        final int feb = 2;

        days[0] = new Day(4, jan, 2001);
        high[0] = 47.0;
        low[0] = 33.0;
        open[0] = 35.0;
        close[0] = 33.0;
        volume[0] = 100.0;

        days[1] = new Day(5, jan, 2001);
        high[1] = 47.0;
        low[1] = 32.0;
        open[1] = 41.0;
        close[1] = 37.0;
        volume[1] = 150.0;

        days[2] = new Day(6, jan, 2001);
        high[2] = 49.0;
        low[2] = 43.0;
        open[2] = 46.0;
        close[2] = 48.0;
        volume[2] = 70.0;

        days[3] = new Day(7, jan, 2001);
        high[3] = 51.0;
        low[3] = 39.0;
        open[3] = 40.0;
        close[3] = 47.0;
        volume[3] = 200.0;

        days[4] = new Day(8, jan, 2001);
        high[4] = 60.0;
        low[4] = 40.0;
        open[4] = 46.0;
        close[4] = 53.0;
        volume[4] = 120.0;

        days[5] = new Day(9, jan, 2001);
        high[5] = 62.0;
        low[5] = 55.0;
        open[5] = 57.0;
        close[5] = 61.0;
        volume[5] = 110.0;

        days[6] = new Day(10, jan, 2001);
        high[6] = 65.0;
        low[6] = 56.0;
        open[6] = 62.0;
        close[6] = 59.0;
        volume[6] = 70.0;

        days[7] = new Day(11, jan, 2001);
        high[7] = 55.0;
        low[7] = 43.0;
        open[7] = 45.0;
        close[7] = 47.0;
        volume[7] = 20.0;

        days[8] = new Day(12, jan, 2001);
        high[8] = 54.0;
        low[8] = 33.0;
        open[8] = 40.0;
        close[8] = 51.0;
        volume[8] = 30.0;

        days[9] = new Day(13, jan, 2001);
        high[9] = 47.0;
        low[9] = 33.0;
        open[9] = 35.0;
        close[9] = 33.0;
        volume[9] = 100.0;

        days[10] = new Day(14, jan, 2001);
        high[10] = 54.0;
        low[10] = 38.0;
        open[10] = 43.0;
        close[10] = 52.0;
        volume[10] = 50.0;

        days[11] = new Day(15, jan, 2001);
        high[11] = 48.0;
        low[11] = 41.0;
        open[11] = 44.0;
        close[11] = 41.0;
        volume[11] = 80.0;

        days[12] = new Day(16, jan, 2001);
        high[12] = 60.0;
        low[12] = 30.0;
        open[12] = 34.0;
        close[12] = 44.0;
        volume[12] = 90.0;

        days[13] = new Day(17, jan, 2001);
        high[13] = 58.0;
        low[13] = 44.0;
        open[13] = 54.0;
        close[13] = 56.0;
        volume[13] = 20.0;

        days[14] = new Day(18, jan, 2001);
        high[14] = 54.0;
        low[14] = 32.0;
        open[14] = 42.0;
        close[14] = 53.0;
        volume[14] = 70.0;

        days[15] = new Day(19, jan, 2001);
        high[15] = 53.0;
        low[15] = 39.0;
        open[15] = 50.0;
        close[15] = 49.0;
        volume[15] = 60.0;

        days[16] = new Day(20, jan, 2001);
        high[16] = 47.0;
        low[16] = 33.0;
        open[16] = 41.0;
        close[16] = 40.0;
        volume[16] = 30.0;

        days[17] = new Day(21, jan, 2001);
        high[17] = 55.0;
        low[17] = 37.0;
        open[17] = 43.0;
        close[17] = 45.0;
        volume[17] = 90.0;

        days[18] = new Day(22, jan, 2001);
        high[18] = 54.0;
        low[18] = 42.0;
        open[18] = 50.0;
        close[18] = 42.0;
        volume[18] = 150.0;

        days[19] = new Day(23, jan, 2001);
        high[19] = 48.0;
        low[19] = 37.0;
        open[19] = 37.0;
        close[19] = 47.0;
        volume[19] = 120.0;

        days[20] = new Day(24, jan, 2001);
        high[20] = 58.0;
        low[20] = 33.0;
        open[20] = 39.0;
        close[20] = 41.0;
        volume[20] = 80.0;

        days[21] = new Day(25, jan, 2001);
        high[21] = 47.0;
        low[21] = 31.0;
        open[21] = 36.0;
        close[21] = 41.0;
        volume[21] = 40.0;

        days[22] = new Day(26, jan, 2001);
        high[22] = 58.0;
        low[22] = 44.0;
        open[22] = 49.0;
        close[22] = 44.0;
        volume[22] = 20.0;

        days[23] = new Day(27, jan, 2001);
        high[23] = 46.0;
        low[23] = 41.0;
        open[23] = 43.0;
        close[23] = 44.0;
        volume[23] = 60.0;

        days[24] = new Day(28, jan, 2001);
        high[24] = 56.0;
        low[24] = 39.0;
        open[24] = 39.0;
        close[24] = 51.0;
        volume[24] = 40.0;

        days[25] = new Day(29, jan, 2001);
        high[25] = 56.0;
        low[25] = 39.0;
        open[25] = 47.0;
        close[25] = 49.0;
        volume[25] = 70.0;

        days[26] = new Day(30, jan, 2001);
        high[26] = 53.0;
        low[26] = 39.0;
        open[26] = 52.0;
        close[26] = 47.0;
        volume[26] = 60.0;

        days[27] = new Day(1, feb, 2001);
        high[27] = 51.0;
        low[27] = 30.0;
        open[27] = 45.0;
        close[27] = 47.0;
        volume[27] = 90.0;

        days[28] = new Day(2, feb, 2001);
        high[28] = 47.0;
        low[28] = 30.0;
        open[28] = 34.0;
        close[28] = 46.0;
        volume[28] = 100.0;

        days[29] = new Day(3, feb, 2001);
        high[29] = 57.0;
        low[29] = 37.0;
        open[29] = 44.0;
        close[29] = 56.0;
        volume[29] = 20.0;

        days[30] = new Day(4, feb, 2001);
        high[30] = 49.0;
        low[30] = 40.0;
        open[30] = 47.0;
        close[30] = 44.0;
        volume[30] = 50.0;

        days[31] = new Day(5, feb, 2001);
        high[31] = 46.0;
        low[31] = 38.0;
        open[31] = 43.0;
        close[31] = 40.0;
        volume[31] = 70.0;

        days[32] = new Day(6, feb, 2001);
        high[32] = 55.0;
        low[32] = 38.0;
        open[32] = 39.0;
        close[32] = 53.0;
        volume[32] = 120.0;

        days[33] = new Day(7, feb, 2001);
        high[33] = 50.0;
        low[33] = 33.0;
        open[33] = 37.0;
        close[33] = 37.0;
        volume[33] = 140.0;

        days[34] = new Day(8, feb, 2001);
        high[34] = 59.0;
        low[34] = 34.0;
        open[34] = 57.0;
        close[34] = 43.0;
        volume[34] = 70.0;

        days[35] = new Day(9, feb, 2001);
        high[35] = 48.0;
        low[35] = 39.0;
        open[35] = 46.0;
        close[35] = 47.0;
        volume[35] = 70.0;

        days[36] = new Day(10, feb, 2001);
        high[36] = 55.0;
        low[36] = 30.0;
        open[36] = 37.0;
        close[36] = 30.0;
        volume[36] = 30.0;

        days[37] = new Day(11, feb, 2001);
        high[37] = 60.0;
        low[37] = 32.0;
        open[37] = 56.0;
        close[37] = 36.0;
        volume[37] = 70.0;

        days[38] = new Day(12, feb, 2001);
        high[38] = 56.0;
        low[38] = 42.0;
        open[38] = 53.0;
        close[38] = 54.0;
        volume[38] = 40.0;

        days[39] = new Day(13, feb, 2001);
        high[39] = 49.0;
        low[39] = 42.0;
        open[39] = 45.0;
        close[39] = 42.0;
        volume[39] = 90.0;

        days[40] = new Day(14, feb, 2001);
        high[40] = 55.0;
        low[40] = 42.0;
        open[40] = 47.0;
        close[40] = 54.0;
        volume[40] = 70.0;

        days[41] = new Day(15, feb, 2021);
        high[41] = 49.0;
        low[41] = 35.0;
        open[41] = 8.0;
        close[41] = 5.0;
        volume[41] = 20.0;

        days[42] = new Day(16, feb, 2023);
        high[42] = 4.0;
        low[42] = 3.0;
        open[42] = 63.0;
        close[42] = 8.0;
        volume[42] = 234.0;

        days[43] = new Day(17, feb, 2001);
        high[43] = 53.0;
        low[43] = 42.0;
        open[43] = 47.0;
        close[43] = 48.0;
        volume[43] = 20.0;

        days[44] = new Day(18, feb, 2001);
        high[44] = 47.0;
        low[44] = 44.0;
        open[44] = 46.0;
        close[44] = 44.0;
        volume[44] = 30.0;

        days[45] = new Day(19, feb, 2001);
        high[45] = 46.0;
        low[45] = 40.0;
        open[45] = 43.0;
        close[45] = 44.0;
        volume[45] = 50.0;

        days[46] = new Day(20, feb, 2001);
        high[46] = 48.0;
        low[46] = 41.0;
        open[46] = 46.0;
        close[46] = 41.0;
        volume[46] = 100.0;

        IntStream.range(0, days.length).forEach(i -> {
            priceDateSeries.add(days[i], open[i], high[i], low[i], close[i]);
            volumeDateSeries.add(days[i], volume[i]);
        });

        resultMap.put("priceDateData", priceDateSeries);
        resultMap.put("volumeDateData", volumeDateSeries);

        return resultMap;
    }

}
