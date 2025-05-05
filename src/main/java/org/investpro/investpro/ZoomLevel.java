package org.investpro.investpro;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ui.chart.CandleStickChart;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.investpro.investpro.ui.chart.CandleStickChart.*;

@Getter
@Setter


public class ZoomLevel {
    private final int zoomLevelId;

    private final int candleWidth;
    @Getter
    private final double xAxisRangeInSeconds;
    private final IntegerProperty numVisibleCandles;
    private final double secondsPerPixel;
    private final double pixelsPerSecond;
    private final InstantAxisFormatter xAxisFormatter;
    private final Map<Integer, Pair<Extrema, Extrema>> extremaForCandleRangeMap;
    static CandleStickChart chart;
    private double minXValue;
    private final int[] zoomSecondsOptions = {60, 120, 300, 600, 900, 1800, 3600}; // 1min to 1h

    public static int getNextZoomLevelId(ZoomLevel zoomLevel, ZoomDirection zoomDirection) {
        if (zoomDirection == ZoomDirection.IN) {
            return zoomLevel.zoomLevelId - 1;
        } else {
            return zoomLevel.zoomLevelId + 1;
        }
    }

    public int getNumVisibleCandles() {
        return numVisibleCandles.get();
    }

    public InstantAxisFormatter getXAxisFormatter() {
        return xAxisFormatter;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        ZoomLevel other = (ZoomLevel) object;

        return zoomLevelId == other.zoomLevelId &&
                candleWidth == other.candleWidth &&
                xAxisRangeInSeconds == other.xAxisRangeInSeconds &&
                Objects.equals(numVisibleCandles, other.numVisibleCandles) &&
                secondsPerPixel == other.secondsPerPixel &&
                pixelsPerSecond == other.pixelsPerSecond &&
                Objects.equals(xAxisFormatter, other.xAxisFormatter) &&
                minXValue == other.minXValue;
    }


    @Override
    public int hashCode() {
        return Objects.hash(zoomLevelId, candleWidth, xAxisRangeInSeconds, numVisibleCandles,
                secondsPerPixel, pixelsPerSecond, xAxisFormatter, minXValue);
    }

    @Override
    public String toString() {
        return String.format("ZoomLevel [id = %d, numVisibleCandles = %s, secondsPerPixel = %f, pixelsPerSecond = " +
                        "%f, candleWidth = %d, minXValue = %f", zoomLevelId, numVisibleCandles, secondsPerPixel,
                pixelsPerSecond, candleWidth, minXValue);
    }
    private int zoomIndex = 3; // Start at 600 seconds per candle, for example

    public ZoomLevel(CandleStickChart chart, final int zoomLevelId, final int candleWidth, final int secondsPerCandle,
                     final @NotNull DoubleProperty plotAreaWidthProperty, final InstantAxisFormatter xAxisFormatter,
                     final double minXValue) {
        this.zoomLevelId = zoomLevelId;
        this.candleWidth = candleWidth;
        numVisibleCandles = new SimpleIntegerProperty((int) (plotAreaWidthProperty.doubleValue() / candleWidth));
        numVisibleCandles.bind(Bindings.createDoubleBinding(() -> plotAreaWidthProperty.doubleValue() / candleWidth,
                plotAreaWidthProperty));
        this.secondsPerPixel = (double) secondsPerCandle / candleWidth;
        pixelsPerSecond = 1d / secondsPerPixel;
        this.xAxisFormatter = xAxisFormatter;
        this.minXValue = minXValue;
        this.xAxisRangeInSeconds = numVisibleCandles.doubleValue() * secondsPerCandle;
        extremaForCandleRangeMap = new ConcurrentHashMap<>();

        ZoomLevel.chart = chart;

    }

    public static ZoomLevel create(int zoomLevelId, int candleWidth, int secondsPerCandle,
                                   DoubleProperty widthProperty, InstantAxisFormatter formatter, double minXValue) {
        return new ZoomLevel(chart, zoomLevelId, candleWidth, secondsPerCandle, widthProperty, formatter, minXValue);
    }

    public void zoomIn() {
        if (zoomIndex > 0) {
            zoomIndex--;
            applyZoom(zoomSecondsOptions[zoomIndex]);
        }
    }

    public void zoomOut() {
        if (zoomIndex < zoomSecondsOptions.length - 1) {
            zoomIndex++;
            applyZoom(zoomSecondsOptions[zoomIndex]);
        }
    }

    private void applyZoom(int secondsPerCandle) {
        // or plotAreaWidthProperty

        InstantAxisFormatter gh = new InstantAxisFormatter(DateTimeFormatter.BASIC_ISO_DATE);
        currZoomLevel = new ZoomLevel(chart,
                zoomIndex,
                candleWidth,
                secondsPerCandle,
                chart.prefWidthProperty(), gh, // or plotAreaWidthProperty

                10
        );
        setAxisBoundsAfterZoom(); // adjust xAxis range, bounds, etc.
        chart.drawChartContents(true);
    }

    private void setAxisBoundsAfterZoom() {
        int secondsPerCandle = currZoomLevel.getSecondsPerCandle(); // derive from your constructor values if needed
        int visibleCandles = currZoomLevel.getNumVisibleCandles();
        double totalVisibleSeconds = visibleCandles * secondsPerCandle;

        // Keep current center X so zoom feels anchored
        double currentLower = xAxis.getLowerBound();
        double currentUpper = xAxis.getUpperBound();
        double center = (currentLower + currentUpper) / 2;

        double newLower = center - (totalVisibleSeconds / 2);
        double newUpper = center + (totalVisibleSeconds / 2);

        // Apply new bounds
        xAxis.setLowerBound(newLower);
        xAxis.setUpperBound(newUpper);

        logger.info("Zoom applied: secondsPerCandle={}, visibleCandles={}, newXBounds=({}, {})",
                secondsPerCandle, visibleCandles, newLower, newUpper);
    }

    private int getSecondsPerCandle() {
        return secondsPerCandle;
    }

}