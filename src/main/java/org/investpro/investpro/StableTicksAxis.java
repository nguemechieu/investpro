

package org.investpro.investpro;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.WritableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Dimension2D;
import javafx.scene.chart.ValueAxis;
import javafx.util.Duration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code StableTicksAxis} places tick marks at consistent (axis value rather than graphical) locations. This
 * makes the axis major tick marks (the labeled tick marks) have nice, rounded numbers.
 *
 * @author Jason Winnebeck
 */
public class StableTicksAxis extends ValueAxis<Number> {
    /**
     * Possible tick spacing at the 10^1 level. These numbers must be {@literal >= 1 and < 10}.
     */
    private static final double[] dividers = new double[]{1.0, 2.5, 5.0};
    /**
     * How many negatives powers of ten we have in the powersOfTen array.
     */
    private static final int powersOfTenOffset = 7;
    private static final double[] powersOfTen = new double[]{
            0.0000001, 0.000001, 0.00001, 0.0001, 0.001, 0.01, 0.1, 1.0, 10.0, 100.0, 1_000.0, 10_000.0, 100_000.0,
            1_000_000.0, 10_000_000.0, 100_000_000.0
    };
    private static final int numMinorTicks = 3;
    private static final int[] powersOf10 = {
            1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000
    };
    private static final int[] halfPowersOf10 = {
            3, 31, 316, 3162, 31622, 316227, 3162277, 31622776, 316227766, Integer.MAX_VALUE
    };
    private static final byte[] maxLog10ForLeadingZeros = {
            9, 9, 9, 8, 8, 8, 7, 7, 7, 6, 6, 6, 6, 5, 5, 5, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 1, 1, 1, 0, 0, 0, 0
    };
    private final Timeline animationTimeline = new Timeline();
    /**
     * If true, when auto-ranging, force 0 to be the min or max end of the range.
     */
    private final BooleanProperty forceZeroInRange = new SimpleBooleanProperty(true);
    private final WritableValue<Double> scaleValue = new WritableValue<>() {
        @Override
        public Double getValue() {
            return getScale();
        }

        @Override
        public void setValue(Double value) {
            setScale(value);
        }
    };
    /**
     * Amount of padding to add on the end of the axis when auto ranging.
     */
    private final DoubleProperty autoRangePadding = new SimpleDoubleProperty(0.1);
    private List<Number> minorTicks = new ArrayList<>();
    private double labelSize = -1;

    public StableTicksAxis() {
    }

    public StableTicksAxis(double lowerBound, double upperBound) {
        super(lowerBound, upperBound);
    }

    private static double calculateTickSpacing(double delta, int maxTicks) {

        delta = Math.abs(delta);


        int factor;
        if ((int) delta != 0) {
            factor = log10((int) delta, RoundingMode.DOWN);
        } else {
            factor = (int) Math.ceil(Math.log10(delta));
        }
        int divider = 0;

        double numTicks = delta / (dividers[Math.abs(divider)] * powersOfTen[factor + powersOfTenOffset]);
        // We don't have enough ticks, so increase ticks until we're over the limit, then back off once.
        if (numTicks < maxTicks) {
            while (numTicks < maxTicks) {
                // Move up
                --divider;
                if (divider < 0) {
                    --factor;
                    divider = dividers.length - 1;
                }

                numTicks = delta / (dividers[divider] * powersOfTen[Math.abs(factor + powersOfTenOffset)]);
            }

            // Now back off once unless we hit exactly
            // noinspection FloatingPointEquality
            if (numTicks != maxTicks) {
                ++divider;
                if (divider >= dividers.length) {
                    ++factor;
                    divider = 0;
                }
            }
        } else {
            // We have too many ticks or exactly max, so decrease until we're just under (or at) the limit.
            while (numTicks > maxTicks) {
                ++divider;
                if (divider >= dividers.length) {
                    ++factor;
                    divider = 0;
                }

                numTicks = delta / (dividers[divider] * powersOfTen[factor + powersOfTenOffset]);
            }
        }

        return dividers[divider] * powersOfTen[factor + powersOfTenOffset];
    }

    /**
     * Returns the base-10 logarithm of {@code x}, rounded according to the specified rounding mode.
     * <p>
     * From Guava's IntMath.java.
     *
     * @throws IllegalArgumentException if {@code x <= 0}
     * @throws ArithmeticException      if {@code mode} is {@link RoundingMode#UNNECESSARY} and {@code x}
     *                                  is not a power of ten
     */
    @SuppressWarnings("fallthrough")
    public static int log10(int x, RoundingMode mode) {
        if (x <= 0) {
            throw new IllegalArgumentException("x must be positive but was: " + x);
        }
        int y = maxLog10ForLeadingZeros[Integer.numberOfLeadingZeros(x)];
        int logFloor = y - lessThanBranchFree(x, powersOf10[y]);

        // Ensure logFloor is within valid range
        if (logFloor < 0 || logFloor >= powersOf10.length) {
            throw new ArithmeticException("Calculated logFloor out of bounds: " + logFloor);
        }

        int floorPow = powersOf10[logFloor];
        switch (mode) {
            case UNNECESSARY:
                if (x != floorPow) {
                    throw new ArithmeticException("mode was UNNECESSARY, but rounding was necessary");
                }
                // fall through
            case FLOOR:
            case DOWN:
                return logFloor;
            case CEILING:
            case UP:
                return logFloor + lessThanBranchFree(x, floorPow);
            case HALF_DOWN:
            case HALF_UP:
            case HALF_EVEN:
                return logFloor + lessThanBranchFree(x, halfPowersOf10[logFloor]);
            default:
                throw new AssertionError("Unexpected RoundingMode: " + mode);
        }
    }

    static int lessThanBranchFree(int x, int y) {
        // The double negation is optimized away by normal Java, but is necessary for GWT
        // to make sure bit twiddling works as expected.
        return (x - y) >>> (Integer.SIZE - 1);
    }

    /**
     * Amount of padding to add on the end of the axis when auto ranging.
     */
    public double getAutoRangePadding() {
        return autoRangePadding.get();
    }

    /**
     * Amount of padding to add on the end of the axis when auto ranging.
     */
    public void setAutoRangePadding(double autoRangePadding) {
        this.autoRangePadding.set(autoRangePadding);
    }

    /**
     * Amount of padding to add on the end of the axis when auto ranging.
     */
    public DoubleProperty autoRangePaddingProperty() {
        return autoRangePadding;
    }

    /**
     * If true, when auto-ranging, force 0 to be the min or max end of the range.
     */
    public boolean isForceZeroInRange() {
        return forceZeroInRange.get();
    }

    /**
     * If true, when auto-ranging, force 0 to be the min or max end of the range.
     */
    public void setForceZeroInRange(boolean forceZeroInRange) {
        this.forceZeroInRange.set(forceZeroInRange);
    }

    /**
     * If true, when auto-ranging, force 0 to be the min or max end of the range.
     */
    public BooleanProperty forceZeroInRangeProperty() {
        return forceZeroInRange;
    }

    @Override
    protected Range autoRange(double minValue, double maxValue, double length, double labelSize) {
        // NOTE(dweil): if the range is very small, display it like a flat line, the scaling doesn't work very well at
        // these values. 1e-300 was chosen arbitrarily.
        if (Math.abs(minValue - maxValue) < 1e-300) {
            // Normally this is the case for all points with the same value
            minValue = minValue - 1;
            maxValue = maxValue + 1;
        } else {
            // Add padding
            double delta = maxValue - minValue;
            double paddedMin = minValue - delta * autoRangePadding.get();
            // If we've crossed the 0 line, clamp to 0.
            // noinspection FloatingPointEquality
            if (Math.signum(paddedMin) != Math.signum(minValue)) {
                paddedMin = 0.0;
            }

            double paddedMax = maxValue + delta * autoRangePadding.get();
            // If we've crossed the 0 line, clamp to 0.
            // noinspection FloatingPointEquality
            if (Math.signum(paddedMax) != Math.signum(maxValue)) {
                paddedMax = 0.0;
            }

            minValue = paddedMin;
            maxValue = paddedMax;
        }

        // Handle forcing zero into the range
        if (forceZeroInRange.get()) {
            if (minValue < 0 && maxValue < 0) {
                maxValue = 0;
                minValue -= -minValue * autoRangePadding.get();
            } else if (minValue > 0 && maxValue > 0) {
                minValue = 0;
                maxValue += maxValue * autoRangePadding.get();
            }
        }

        return getRange(minValue, maxValue);
    }

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return minorTicks;
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        if (!(range instanceof Range rangeVal)) {
            return; // Avoid NPE if range is null or incorrect type
        }

        if (animate) {
            animationTimeline.stop();
            ObservableList<KeyFrame> keyFrames = animationTimeline.getKeyFrames();
            keyFrames.setAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(currentLowerBound, getLowerBound()),
                            new KeyValue(scaleValue, getScale())),
                    new KeyFrame(Duration.millis(750),
                            new KeyValue(currentLowerBound, rangeVal.low),
                            new KeyValue(scaleValue, rangeVal.scale)));

            animationTimeline.play();
        } else {
            currentLowerBound.set(rangeVal.low);
            setScale(rangeVal.scale);
        }

        setLowerBound(rangeVal.low);
        setUpperBound(rangeVal.high);
    }


    @Override
    protected Range getRange() {
        return getRange(getValue(), getMaxValue());
    }

    private @NotNull Range getRange(double minValue, double maxValue) {
        double length = getLength();
        double delta = maxValue - minValue;
        double scale = calculateNewScale(length, minValue, maxValue);
        int maxTicks = Math.max(1, (int) (length / getLabelSize()));
        return new Range(minValue, maxValue, calculateTickSpacing(Math.abs(delta), maxTicks), scale);
    }


    @Override
    protected String getTickMarkLabel(Number number) {
        if (getTickLabelFormatter() == null) {
            return number.toString(); // Fallback in case formatter is null
        }
        return getTickLabelFormatter().toString(number);
    }

    protected double getLength() {
        if (getSide() == null) {
            // default to horizontal
            return getWidth();
        }

        if (getSide().isHorizontal()) {
            return getWidth();
        } else {
            return getHeight();
        }
    }

    private double getLabelSize() {
        if (labelSize == -1) {
            Dimension2D dim = measureTickMarkLabelSize("-888.88E-88", getTickLabelRotation());
            if (getSide().isHorizontal()) {
                labelSize = dim.getWidth();
            } else {

                labelSize = dim.getHeight();
            }
        }

        return labelSize;
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        if (!(range instanceof Range rangeVal)) {
            return List.of();
        }

        if (rangeVal.tickSpacing == 0) {
            return List.of();
        }

        double firstTick = Math.floor(rangeVal.low / rangeVal.tickSpacing) * rangeVal.tickSpacing;
        int numTicks = (int) (rangeVal.getDelta() / rangeVal.tickSpacing) + 1;

        List<Number> majorTicks = new ArrayList<>(numTicks + 1);
        minorTicks = new ArrayList<>((numTicks + 2) * numMinorTicks);

        double minorTickSpacing = rangeVal.tickSpacing / (numMinorTicks + 1);

        for (int i = 0; i <= numTicks; ++i) {
            double majorTick = firstTick + rangeVal.tickSpacing * i;
            majorTicks.add(majorTick);

            for (int j = 1; j <= numMinorTicks; ++j) {
                minorTicks.add(majorTick + minorTickSpacing * j);
            }
        }
        return majorTicks;
    }

    public double getValue() {
        return currentLowerBound.get();
    }

    public void setValue(double newX) {

        setRange(newX, true);
    }

    public double getMaxValue() {
        return getUpperBound();
    }

    private record Range(double low, double high, double tickSpacing, double scale) {

        public double getDelta() {
            return (high - low);
        }

        @Contract(pure = true)
        @Override
        public @NotNull String toString() {
            return "Range{" +
                    "low=" + low +
                    ", high=" + high +
                    ", tickSpacing=" + tickSpacing +
                    ", scale=" + scale +
                    '}';
        }
    }

}