/*
 * Copyright [2024] [investpro .LLC]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.investpro.ui.tools;

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
import javafx.util.StringConverter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable numeric axis for financial charts.
 * <p>
 * Goals:
 * - keep tick positions stable in value-space
 * - generate readable "nice" tick spacing
 * - avoid unstable auto-ranging around flat prices
 * - support optional zero anchoring
 * - work well for price, volume, and time-like numeric axes
 */
public class StableTicksAxis extends ValueAxis<Number> {

    /**
     * Preferred tick spacing multipliers at each power-of-ten scale.
     */
    private static final double[] NICE_DIVIDERS = {
            1.0,
            2.0,
            2.5,
            5.0
    };

    private static final int DEFAULT_MINOR_TICK_COUNT = 3;
    private static final double MIN_RANGE_DELTA = 1e-12;
    private static final double DEFAULT_AUTO_RANGE_PADDING = 0.10;
    private static final double DEFAULT_ANIMATION_MS = 250.0;
    private static final int MIN_PIXELS_PER_MAJOR_TICK = 56;

    private static final int[] POWERS_OF_10 = {
            1,
            10,
            100,
            1_000,
            10_000,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000,
            1_000_000_000
    };

    private static final int[] HALF_POWERS_OF_10 = {
            3,
            31,
            316,
            3_162,
            31_622,
            316_227,
            3_162_277,
            31_622_776,
            316_227_766,
            Integer.MAX_VALUE
    };

    private static final byte[] MAX_LOG10_FOR_LEADING_ZEROS = {
            9, 9, 9,
            8, 8, 8,
            7, 7, 7,
            6, 6, 6, 6,
            5, 5, 5,
            4, 4, 4,
            3, 3, 3, 3,
            2, 2, 2,
            1, 1, 1,
            0, 0, 0, 0
    };

    private final Timeline animationTimeline = new Timeline();

    /**
     * If true, auto-range includes zero when all values are on one side of zero.
     * For price axes, usually false.
     * For volume axes, usually true.
     */
    private final BooleanProperty forceZeroInRange = new SimpleBooleanProperty(this, "forceZeroInRange", true);

    /**
     * Padding ratio used when auto-ranging.
     */
    private final DoubleProperty autoRangePadding =
            new SimpleDoubleProperty(this, "autoRangePadding", DEFAULT_AUTO_RANGE_PADDING);

    /**
     * Major tick minimum spacing in pixels.
     */
    private final DoubleProperty minMajorTickSpacing =
            new SimpleDoubleProperty(this, "minMajorTickSpacing", MIN_PIXELS_PER_MAJOR_TICK);

    /**
     * Animation duration for range changes.
     */
    private final DoubleProperty animationDurationMillis =
            new SimpleDoubleProperty(this, "animationDurationMillis", DEFAULT_ANIMATION_MS);

    private List<Number> minorTicks = Collections.emptyList();
    private double cachedLabelSize = -1.0;
    private double cachedLabelRotation = Double.NaN;

    /** Display precision (decimal places) driven by instrument metadata (e.g. OANDA displayPrecision). -1 = auto. */
    private int displayPrecision = -1;

    private final WritableValue<Double> scaleValue = new WritableValue<>() {
        @Override
        public @NotNull Double getValue() {
            return getScale();
        }

        @Override
        public void setValue(Double value) {
            if (value != null && Double.isFinite(value)) {
                setScale(value);
            }
        }
    };

    public StableTicksAxis() {
        super();
        installCacheInvalidation();
    }

    public StableTicksAxis(double lowerBound, double upperBound) {
        super(lowerBound, upperBound);
        installCacheInvalidation();
    }

    private void installCacheInvalidation() {
        tickLabelFontProperty().addListener((observable, oldValue, newValue) -> invalidateLabelSizeCache());
        tickLabelRotationProperty().addListener((observable, oldValue, newValue) -> invalidateLabelSizeCache());
        sideProperty().addListener((observable, oldValue, newValue) -> invalidateLabelSizeCache());
        widthProperty().addListener((observable, oldValue, newValue) -> invalidateRange());
        heightProperty().addListener((observable, oldValue, newValue) -> invalidateRange());
    }

    private void invalidateLabelSizeCache() {
        cachedLabelSize = -1.0;
        cachedLabelRotation = Double.NaN;
    }

    // ---------------------------------------------------------------------
    // Public configuration
    // ---------------------------------------------------------------------

    public boolean isForceZeroInRange() {
        return forceZeroInRange.get();
    }

    public BooleanProperty forceZeroInRangeProperty() {
        return forceZeroInRange;
    }

    public void setForceZeroInRange(boolean forceZeroInRange) {
        this.forceZeroInRange.set(forceZeroInRange);
        invalidateRange();
    }

    public double getAutoRangePadding() {
        return autoRangePadding.get();
    }

    public DoubleProperty autoRangePaddingProperty() {
        return autoRangePadding;
    }

    public void setAutoRangePadding(double padding) {
        double safePadding = sanitizePadding(padding);
        autoRangePadding.set(safePadding);
        invalidateRange();
    }

    public double getMinMajorTickSpacing() {
        return minMajorTickSpacing.get();
    }

    public DoubleProperty minMajorTickSpacingProperty() {
        return minMajorTickSpacing;
    }

    public void setMinMajorTickSpacing(double spacing) {
        if (!Double.isFinite(spacing) || spacing <= 0) {
            minMajorTickSpacing.set(MIN_PIXELS_PER_MAJOR_TICK);
        } else {
            minMajorTickSpacing.set(spacing);
        }

        invalidateRange();
    }

    public double getAnimationDurationMillis() {
        return animationDurationMillis.get();
    }

    public DoubleProperty animationDurationMillisProperty() {
        return animationDurationMillis;
    }

    public void setAnimationDurationMillis(double millis) {
        if (!Double.isFinite(millis) || millis < 0) {
            animationDurationMillis.set(DEFAULT_ANIMATION_MS);
        } else {
            animationDurationMillis.set(millis);
        }
    }

    /**
     * Convenience profile for price axes.
     */
    public void configureForPriceAxis() {
        setForceZeroInRange(false);
        setAutoRangePadding(0.08);
        setMinMajorTickSpacing(58);
    }

    /**
     * Convenience profile for volume axes.
     */
    public void configureForVolumeAxis() {
        setForceZeroInRange(true);
        setAutoRangePadding(0.12);
        setMinMajorTickSpacing(46);
    }

    // ---------------------------------------------------------------------
    // Auto range / range application
    // ---------------------------------------------------------------------

    @Override
    protected Range autoRange(double minValue, double maxValue, double length, double labelSize) {
        double safeMin = sanitizeAxisValue(minValue, 0.0);
        double safeMax = sanitizeAxisValue(maxValue, safeMin + 1.0);

        if (safeMax < safeMin) {
            double temp = safeMin;
            safeMin = safeMax;
            safeMax = temp;
        }

        double delta = safeMax - safeMin;

        if (!Double.isFinite(delta) || Math.abs(delta) < MIN_RANGE_DELTA) {
            double center = Double.isFinite(safeMin) ? safeMin : 0.0;
            double expansion = Math.max(1.0, Math.abs(center) * 0.01);

            safeMin = center - expansion;
            safeMax = center + expansion;
            delta = safeMax - safeMin;
        }

        double padding = sanitizePadding(getAutoRangePadding());
        double paddedMin = safeMin - (delta * padding);
        double paddedMax = safeMax + (delta * padding);

        if (isForceZeroInRange()) {
            if (paddedMin > 0.0 && paddedMax > 0.0) {
                paddedMin = 0.0;
            } else if (paddedMin < 0.0 && paddedMax < 0.0) {
                paddedMax = 0.0;
            }
        } else {
            /*
             * Do not accidentally cross zero for price axes unless the data itself crosses zero.
             */
            if (safeMin > 0.0 && paddedMin < 0.0) {
                paddedMin = Math.max(0.0, safeMin - (delta * padding * 0.35));
            }

            if (safeMax < 0.0 && paddedMax > 0.0) {
                paddedMax = Math.min(0.0, safeMax + (delta * padding * 0.35));
            }
        }

        return createRange(paddedMin, paddedMax, length);
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        if (!(range instanceof Range rangeValue)) {
            return;
        }

        animationTimeline.stop();

        if (animate && getAnimationDurationMillis() > 0.0) {
            ObservableList<KeyFrame> keyFrames = animationTimeline.getKeyFrames();

            keyFrames.setAll(
                    new KeyFrame(
                            Duration.ZERO,
                            new KeyValue(currentLowerBound, getLowerBound()),
                            new KeyValue(scaleValue, getScale())
                    ),
                    new KeyFrame(
                            Duration.millis(getAnimationDurationMillis()),
                            new KeyValue(currentLowerBound, rangeValue.low()),
                            new KeyValue(scaleValue, rangeValue.scale())
                    )
            );

            animationTimeline.play();
        } else {
            currentLowerBound.set(rangeValue.low());
            setScale(rangeValue.scale());
        }

        setLowerBound(rangeValue.low());
        setUpperBound(rangeValue.high());
    }

    @Override
    protected Range getRange() {
        return createRange(getLowerBound(), getUpperBound(), getLength());
    }

    private @NotNull Range createRange(double minValue, double maxValue, double length) {
        double low = sanitizeAxisValue(minValue, 0.0);
        double high = sanitizeAxisValue(maxValue, low + 1.0);

        if (high < low) {
            double temp = low;
            low = high;
            high = temp;
        }

        double delta = high - low;

        if (!Double.isFinite(delta) || delta < MIN_RANGE_DELTA) {
            double center = low;
            double expansion = Math.max(1.0, Math.abs(center) * 0.01);

            low = center - expansion;
            high = center + expansion;
            delta = high - low;
        }

        double safeLength = Math.max(1.0, sanitizeAxisValue(length, getLength()));
        int maxTicks = calculateMaxTicks(safeLength);
        double tickSpacing = calculateTickSpacing(delta, maxTicks);
        double scale = calculateNewScale(safeLength, low, high);

        return new Range(low, high, tickSpacing, scale);
    }

    private int calculateMaxTicks(double length) {
        double spacing = getMinMajorTickSpacing();

        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            spacing = MIN_PIXELS_PER_MAJOR_TICK;
        }

        return Math.max(2, (int) Math.floor(length / spacing));
    }

    // ---------------------------------------------------------------------
    // Tick calculation
    // ---------------------------------------------------------------------

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return minorTicks == null ? Collections.emptyList() : minorTicks;
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        if (!(range instanceof Range rangeValue)) {
            minorTicks = Collections.emptyList();
            return Collections.emptyList();
        }

        double tickSpacing = rangeValue.tickSpacing();

        if (!Double.isFinite(tickSpacing) || tickSpacing <= 0.0) {
            minorTicks = Collections.emptyList();
            return Collections.emptyList();
        }

        double low = rangeValue.low();
        double high = rangeValue.high();

        if (!Double.isFinite(low) || !Double.isFinite(high) || high <= low) {
            minorTicks = Collections.emptyList();
            return Collections.emptyList();
        }

        double firstTick = Math.floor(low / tickSpacing) * tickSpacing;
        double lastTick = Math.ceil(high / tickSpacing) * tickSpacing;

        int estimatedCount = (int) Math.ceil((lastTick - firstTick) / tickSpacing) + 1;
        int maxSafetyCount = 10_000;

        if (estimatedCount <= 0 || estimatedCount > maxSafetyCount) {
            minorTicks = Collections.emptyList();
            return Collections.emptyList();
        }

        List<Number> major = new ArrayList<>(estimatedCount);
        List<Number> minor = new ArrayList<>(estimatedCount * DEFAULT_MINOR_TICK_COUNT);

        double minorTickSpacing = tickSpacing / (DEFAULT_MINOR_TICK_COUNT + 1.0);

        for (int i = 0; i < estimatedCount; i++) {
            double majorTick = cleanFloatingPoint(firstTick + (tickSpacing * i));

            if (majorTick >= low - tickSpacing && majorTick <= high + tickSpacing) {
                major.add(majorTick);
            }

            for (int j = 1; j <= DEFAULT_MINOR_TICK_COUNT; j++) {
                double minorTick = cleanFloatingPoint(majorTick + (minorTickSpacing * j));

                if (minorTick > low && minorTick < high) {
                    minor.add(minorTick);
                }
            }
        }

        minorTicks = minor;
        return major;
    }

    @Override
    protected String getTickMarkLabel(Number number) {
        if (number == null) {
            return "";
        }

        if (getTickLabelFormatter() != null) {
            return getTickLabelFormatter().toString(number);
        }

        // Fallback: format with displayPrecision if set, otherwise auto-detect
        return formatWithPrecision(number.doubleValue(), displayPrecision, "");
    }

    protected double getLength() {
        if (getSide() == null) {
            return Math.max(1.0, getWidth());
        }

        return getSide().isHorizontal()
                ? Math.max(1.0, getWidth())
                : Math.max(1.0, getHeight());
    }

    private double getLabelSize() {
        double rotation = getTickLabelRotation();

        if (cachedLabelSize < 0.0 || Double.compare(rotation, cachedLabelRotation) != 0) {
            Dimension2D dim = measureTickMarkLabelSize("-888.88E-88", rotation);

            cachedLabelSize = getSide() != null && getSide().isHorizontal()
                    ? dim.getWidth()
                    : dim.getHeight();

            cachedLabelRotation = rotation;
        }

        return Math.max(1.0, cachedLabelSize);
    }

    // ---------------------------------------------------------------------
    // Tick spacing math
    // ---------------------------------------------------------------------

    private static double calculateTickSpacing(double delta, int maxTicks) {
        if (!Double.isFinite(delta) || delta <= 0.0) {
            return 1.0;
        }

        int safeMaxTicks = Math.max(1, maxTicks);

        double rawSpacing = delta / safeMaxTicks;

        if (!Double.isFinite(rawSpacing) || rawSpacing <= 0.0) {
            return 1.0;
        }

        double exponent = Math.floor(Math.log10(rawSpacing));
        double base = Math.pow(10.0, exponent);

        for (double divider : NICE_DIVIDERS) {
            double candidate = divider * base;

            if (candidate >= rawSpacing) {
                return candidate;
            }
        }

        return 10.0 * base;
    }

    /**
     * Returns the base-10 logarithm of x, rounded according to mode.
     * Kept for compatibility with older code that may call this utility.
     */
    @SuppressWarnings("fallthrough")
    public static int log10(int x, RoundingMode mode) {
        if (x <= 0) {
            throw new IllegalArgumentException("x must be positive but was: " + x);
        }

        int y = MAX_LOG10_FOR_LEADING_ZEROS[Integer.numberOfLeadingZeros(x)];
        int logFloor = y - lessThanBranchFree(x, POWERS_OF_10[y]);
        int floorPow = POWERS_OF_10[logFloor];

        switch (mode) {
            case UNNECESSARY:
                if (x != floorPow) {
                    throw new ArithmeticException("mode was UNNECESSARY, but rounding was necessary");
                }
            case FLOOR:
            case DOWN:
                return logFloor;
            case CEILING:
            case UP:
                return logFloor + lessThanBranchFree(floorPow, x);
            case HALF_DOWN:
            case HALF_UP:
            case HALF_EVEN:
                return logFloor + lessThanBranchFree(HALF_POWERS_OF_10[logFloor], x);
            default:
                throw new AssertionError();
        }
    }

    static int lessThanBranchFree(int x, int y) {
        return (x - y) >>> (Integer.SIZE - 1);
    }

    private static double cleanFloatingPoint(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        double abs = Math.abs(value);

        if (abs < 1e-12) {
            return 0.0;
        }

        return value;
    }

    private static double sanitizeAxisValue(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double sanitizePadding(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return DEFAULT_AUTO_RANGE_PADDING;
        }

        return Math.min(value, 2.0);
    }

    /**
     * Set the display precision (decimal places) for tick labels.
     * Should match the instrument's {@code displayPrecision} from OANDA or equivalent.
     * Pass -1 to use automatic precision detection.
     */
    public void setPrecision(int precision) {
        this.displayPrecision = precision < 0 ? -1 : Math.min(precision, 12);
        // Reapply label formatter if one was set (preserves symbol)
        if (getTickLabelFormatter() instanceof PrecisionStringConverter psc) {
            setTickLabelFormatter(psc.symbol, this.displayPrecision);
        }
    }

    public int getDisplayPrecision() {
        return displayPrecision;
    }

    /**
     * Installs a tick label formatter using the given currency/pair symbol and current display precision.
     */
    public void setTickLabelFormatter(String symbol) {
        setTickLabelFormatter(symbol, displayPrecision);
    }

    /**
     * Installs a tick label formatter using the given currency/pair symbol and explicit precision.
     * Precision should come from instrument metadata (e.g. OANDA {@code displayPrecision}).
     */
    public void setTickLabelFormatter(String symbol, int precision) {
        this.displayPrecision = precision < 0 ? -1 : Math.min(precision, 12);
        String safeSymbol = symbol == null ? "" : symbol.trim();
        super.setTickLabelFormatter(new PrecisionStringConverter(safeSymbol, this.displayPrecision));
    }

    /**
     * Formats a raw double tick value to the given precision with an optional symbol prefix.
     * If {@code precision} is -1, the precision is inferred from the magnitude of the value.
     */
    static String formatWithPrecision(double value, int precision, String symbol) {
        if (!Double.isFinite(value)) {
            return "";
        }

        int p = precision >= 0 ? precision : inferPrecision(value);
        String formatted = String.format("%,." + p + "f", value);
        return symbol == null || symbol.isEmpty() ? formatted : symbol + formatted;
    }

    /**
     * Infer a sensible display precision from the magnitude of the value when no explicit precision is set.
     * This is a fallback — prefer setting precision from instrument metadata.
     */
    private static int inferPrecision(double value) {
        double abs = Math.abs(value);
        if (abs == 0.0)        return 2;
        if (abs >= 10_000.0)   return 0;
        if (abs >= 1_000.0)    return 1;
        if (abs >= 100.0)      return 2;
        if (abs >= 10.0)       return 3;
        if (abs >= 1.0)        return 4;
        if (abs >= 0.01)       return 5;
        if (abs >= 0.001)      return 6;
        return 8;
    }

    /** Named converter so {@link #setPrecision} can detect and re-apply it. */
    private static final class PrecisionStringConverter extends StringConverter<Number> {
        final String symbol;
        final int precision;

        PrecisionStringConverter(String symbol, int precision) {
            this.symbol = symbol;
            this.precision = precision;
        }

        @Override
        public String toString(Number number) {
            if (number == null) {
                return "";
            }
            return formatWithPrecision(number.doubleValue(), precision, symbol);
        }

        @Override
        public Number fromString(String text) {
            if (text == null || text.isBlank()) {
                return 0.0;
            }

            String cleaned = text
                    .replace(symbol, "")
                    .replace(",", "")
                    .trim();

            try {
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException exception) {
                return 0.0;
            }
        }
    }
    private record Range(double low, double high, double tickSpacing, double scale) {




        public double getDelta() {
            return high - low;
        }

        @Contract(pure = true)
        @Override
        public @NotNull String toString() {
            return "Range{low=%s, high=%s, tickSpacing=%s, scale=%s}"
                    .formatted(low, high, tickSpacing, scale);
        }
    }
}
