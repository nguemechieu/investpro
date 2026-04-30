package org.investpro.utils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.investpro.ui.tools.InstantAxisFormatter;

/**
 * @author NOEL NGUEMECHIEU
 */
public class ZoomLevel {
    private final int zoomLevelId;
    @Getter
    private final int candleWidth;
    private final double xAxisRangeInSeconds;
    private final DoubleProperty numVisibleCandles;
    private final double secondsPerPixel;
    @Getter
    private final double pixelsPerSecond;
    @Getter
    private final InstantAxisFormatter xAxisFormatter;
    @Getter
    @Setter
    private int minXValue;

    @Getter
    private final Map<Integer, Pair<Extrema<Double>, Extrema<Double>>> extremaForCandleRangeMap;

    public ZoomLevel(final int zoomLevelId, final int candleWidth, final int secondsPerCandle,
                     final DoubleProperty plotAreaWidthProperty, final InstantAxisFormatter xAxisFormatter,
                     final int minXValue) {
        this.zoomLevelId = zoomLevelId;
        this.candleWidth = candleWidth;
        numVisibleCandles = new SimpleDoubleProperty(plotAreaWidthProperty.doubleValue() / candleWidth);
        numVisibleCandles.bind(Bindings.createDoubleBinding(() -> plotAreaWidthProperty.doubleValue() / candleWidth,
                plotAreaWidthProperty));
        this.secondsPerPixel = (double) secondsPerCandle / candleWidth;
        pixelsPerSecond = 1d / secondsPerPixel;
        this.xAxisFormatter = xAxisFormatter;
        this.minXValue = minXValue;
        this.xAxisRangeInSeconds = numVisibleCandles.doubleValue() * secondsPerCandle;
        extremaForCandleRangeMap = new ConcurrentHashMap<>();
    }

    public double getxAxisRangeInSeconds() {
        return xAxisRangeInSeconds;
    }

    public double getNumVisibleCandles() {
        return numVisibleCandles.get();
    }

    public static int getNextZoomLevelId(ZoomLevel zoomLevel, ZoomDirection zoomDirection) {
        if (zoomDirection == ZoomDirection.IN) {
            return zoomLevel.zoomLevelId - 1;
        } else {
            return zoomLevel.zoomLevelId + 1;
        }
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
