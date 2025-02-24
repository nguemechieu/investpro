package org.investpro;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Getter
@Setter
public class ZoomLevel {
    private final int zoomLevelId;

    private final int candleWidth;

    private final double xAxisRangeInSeconds;
    private final IntegerProperty numVisibleCandles;
    private final double secondsPerPixel;
    private final double pixelsPerSecond;
    private final InstantAxisFormatter xAxisFormatter;
    private final Map<Integer, Pair<Extrema, Extrema>> extremaForCandleRangeMap;
    private double minXValue;
    private double minYValue;

    ZoomLevel(final int zoomLevelId, final int candleWidth, final int secondsPerCandle,
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
    }

    public int getNumVisibleCandles() {
        return numVisibleCandles.get();
    }

    public InstantAxisFormatter getXAxisFormatter() {
        return xAxisFormatter;
    }


    static int getNextZoomLevelId(ZoomLevel zoomLevel, ZoomDirection zoomDirection) {
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
                        "%f, candleWidth = %d, minXValue = %f", zoomLevelId, numVisibleCandles, secondsPerPixel,
                pixelsPerSecond, candleWidth, minXValue);
    }
    private double maxYValue;

    public double getLowerBound() {
        return minXValue;
    }

    public double getUpperBound() {
        return minXValue + xAxisRangeInSeconds;
    }

    public double getLength() {
        return xAxisRangeInSeconds;
    }

    public double getMaxXValue() {
        return minXValue + getLength();
    }

    public void setMaxXValue(double newMaxXValue) {
        minXValue = newMaxXValue - getLength();
    }

    public double getMinYValue() {
        return extremaForCandleRangeMap.values().stream()
                .mapToDouble(Extrema::getMin)
                .min()
                .orElse(Double.NaN);
    }

    public double getMaxYValue() {
        return extremaForCandleRangeMap.values().stream()
                .mapToDouble(Extrema::getMax)
                .max()
                .orElse(Double.NaN);
    }


    int duration = 1000;

    public void decreaseDuration() {
        duration /= 2;
    }

    public double calculateZoomFactor(double deltaY) {
        double range = getMaxYValue() - getMinYValue();
        double newMinYValue = getMaxYValue() - deltaY / pixelsPerSecond * range;
        double newMaxYValue = getMinYValue() + deltaY / pixelsPerSecond * range;
        double newRange = newMaxYValue - newMinYValue;
        double newDuration = newRange / pixelsPerSecond * duration;
        return newDuration / duration;
    }
}
