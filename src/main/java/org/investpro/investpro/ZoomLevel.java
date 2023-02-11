package org.investpro.investpro;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ZoomLevel {
    private final int zoomLevelId;
    private final int candleWidth;
    private final double xAxisRangeInSeconds;
    private final DoubleProperty numVisibleCandles;
    private final double secondsPerPixel;
    private final double pixelsPerSecond;
    private final InstantAxisFormatter xAxisFormatter;
    private final Map<Integer, Pair<Extrema<Integer>, Extrema<Integer>>> extremaForCandleRangeMap;
    private int minXValue;

    public ZoomLevel(final int zoomLevelId, final int candleWidth, final int secondsPerCandle,
                     final @NotNull DoubleProperty plotAreaWidthProperty, final InstantAxisFormatter xAxisFormatter,
                     final int minXValue) {
        this.zoomLevelId = zoomLevelId;
        this.candleWidth = candleWidth;
        numVisibleCandles = new SimpleDoubleProperty(plotAreaWidthProperty.doubleValue() / candleWidth);
        numVisibleCandles.bind(Bindings.createDoubleBinding(() -> plotAreaWidthProperty.doubleValue() / candleWidth,
                plotAreaWidthProperty));
        this.secondsPerPixel = (float)secondsPerCandle / candleWidth;
        pixelsPerSecond = 1d / secondsPerPixel;
        this.xAxisFormatter = xAxisFormatter;
        this.minXValue = minXValue;
        this.xAxisRangeInSeconds = numVisibleCandles.doubleValue() * secondsPerCandle;
        extremaForCandleRangeMap = new ConcurrentHashMap<>();
    }

    public static int getNextZoomLevelId(ZoomLevel zoomLevel, ZoomDirection zoomDirection) {
        if (zoomDirection == ZoomDirection.IN) {
            return zoomLevel.zoomLevelId - 1;
        } else {
            return zoomLevel.zoomLevelId + 1;
        }
    }

    public int getCandleWidth() {
        return candleWidth;
    }

    public double getxAxisRangeInSeconds() {
        return xAxisRangeInSeconds;
    }

    public double getNumVisibleCandles() {
        return numVisibleCandles.get();
    }

    public double getSecondsPerPixel() {
        return secondsPerPixel;
    }

    public double getPixelsPerSecond() {
        return pixelsPerSecond;
    }

    public Map<Integer, Pair<Extrema<Integer>, Extrema<Integer>>> getExtremaForCandleRangeMap() {
        return extremaForCandleRangeMap;
    }

    public InstantAxisFormatter getXAxisFormatter() {
        return xAxisFormatter;
    }

    public int getMinXValue() {
        return minXValue;
    }

    public void setMinXValue(int minXValue) {
        this.minXValue = minXValue;
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
                        "%f, candleWidth = %d, minXValue = %d", zoomLevelId, numVisibleCandles, secondsPerPixel,
                pixelsPerSecond, candleWidth, minXValue);
    }
}
