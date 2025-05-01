package org.investpro.investpro.chart;

import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleStickChartOptions;
import org.investpro.investpro.StableTicksAxis;
import org.investpro.investpro.model.Candle;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class CandleStickChart extends XYChart<String, Number> {

    private final Map<Data<String, Number>, CandleNode> candleNodes = new HashMap<>();
    @Setter
    private boolean autoScroll = false;

    public CandleStickChart(Axis xAxis, StableTicksAxis yAxis) {
        super(xAxis, yAxis);
        setAnimated(false);
        setLegendVisible(false);
        setAlternativeRowFillVisible(false);
        setAlternativeColumnFillVisible(false);
        setHorizontalGridLinesVisible(true);
        setVerticalGridLinesVisible(true);
    }

    @Override
    protected void layoutPlotChildren() {
        for (Series<String, Number> series : getData()) {
            for (Data<String, Number> item : series.getData()) {
                CandleNode candle = candleNodes.get(item);
                if (candle == null) continue;

                double x = getXAxis().getDisplayPosition(item.getXValue());
                double y = getYAxis().getDisplayPosition(item.getYValue());
                candle.update(x, y, getYAxis());
            }
        }
    }

    @Override
    protected void dataItemAdded(Series<String, Number> series, int itemIndex, Data<String, Number> item) {
        if (item.getExtraValue() instanceof Candle candle) {
            CandleNode candleNode = new CandleNode(candle, 5.0); // default width
            getPlotChildren().add(candleNode);
            item.setNode(candleNode);
            candleNodes.put(item, candleNode);
        }
    }

    @Override
    protected void dataItemRemoved(Data<String, Number> item, Series<String, Number> series) {
        CandleNode candleNode = candleNodes.remove(item);
        getPlotChildren().remove(candleNode);
    }

    @Override
    protected void dataItemChanged(Data<String, Number> item) {
        CandleNode candleNode = candleNodes.get(item);
        if (candleNode != null && item.getExtraValue() instanceof Candle candle) {
            candleNode.updateCandle(candle);
        }
    }

    @Override
    protected void seriesAdded(@NotNull Series<String, Number> series, int seriesIndex) {
        for (Data<String, Number> item : series.getData()) {
            dataItemAdded(series, -1, item);
        }
    }

    @Override
    protected void seriesRemoved(Series<String, Number> series) {
        for (Data<String, Number> item : series.getData()) {
            dataItemRemoved(item, series);
        }
    }

    @Override
    public Node getStyleableNode() {
        return super.getStyleableNode();
    }

    public @NotNull CandleStickChartOptions getChartOptions() {
        return new CandleStickChartOptions();
    }

    public void clearChart() {
        getData().clear();
        candleNodes.clear();
        getPlotChildren().clear();
    }

    protected String formatTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(java.time.Instant.ofEpochMilli(epochMillis)
                        .atZone(java.time.ZoneId.systemDefault()));
    }

    public List<Candle> getLoadedCandles() {
        return getData().stream()
                .flatMap(series -> series.getData().stream())
                .filter(data -> data.getExtraValue() instanceof Candle)
                .map(data -> (Candle) data.getExtraValue())
                .collect(Collectors.toList());
    }
}
