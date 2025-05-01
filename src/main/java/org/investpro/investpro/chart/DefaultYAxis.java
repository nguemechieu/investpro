package org.investpro.investpro.chart;

import javafx.scene.chart.Axis;

import java.util.ArrayList;
import java.util.List;

public class DefaultYAxis extends Axis<Number> {

    private double lowerBound = 0;
    private double upperBound = 100;

    @Override
    protected Object autoRange(double length) {
        return new double[]{lowerBound, upperBound};
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        double[] r = (double[]) range;
        lowerBound = r[0];
        upperBound = r[1];
    }

    @Override
    protected Object getRange() {
        return new double[]{lowerBound, upperBound};
    }

    @Override
    public double getZeroPosition() {
        return getDisplayPosition(0);
    }

    @Override
    public double getDisplayPosition(Number value) {
        double range = upperBound - lowerBound;
        if (range == 0) {
            return 0;
        }
        double scale = getHeight() / range;
        return getHeight() - (value.doubleValue() - lowerBound) * scale;
    }

    @Override
    public Number getValueForDisplay(double displayPosition) {
        double range = upperBound - lowerBound;
        if (range == 0) {
            return 0;
        }
        double scale = getHeight() / range;
        return lowerBound + (getHeight() - displayPosition) / scale;
    }

    @Override
    public boolean isValueOnAxis(Number value) {
        return value.doubleValue() >= lowerBound && value.doubleValue() <= upperBound;
    }

    @Override
    public double toNumericValue(Number value) {
        return value.doubleValue();
    }

    @Override
    public Number toRealValue(double value) {
        return value;
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        List<Number> ticks = new ArrayList<>();
        double[] r = (double[]) range;
        double lower = r[0];
        double upper = r[1];
        double tickUnit = (upper - lower) / 10.0;

        for (double v = lower; v <= upper; v += tickUnit) {
            ticks.add(v);
        }
        return ticks;
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        return String.format("%.2f", value.doubleValue());
    }
}
