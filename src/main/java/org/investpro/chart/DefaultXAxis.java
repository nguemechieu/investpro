package org.investpro.chart;

import javafx.scene.chart.Axis;

import java.util.ArrayList;
import java.util.List;

public class DefaultXAxis extends Axis<String> {

    private List<String> categories = new ArrayList<>();

    @Override
    protected Object autoRange(double length) {
        return categories;
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        categories = (List<String>) range;
    }

    @Override
    protected Object getRange() {
        return categories;
    }

    @Override
    public double getZeroPosition() {
        return 0;
    }

    @Override
    public double getDisplayPosition(String value) {
        int index = categories.indexOf(value);
        if (index == -1) return 0;
        double spacing = 10; // pixels per candle (adjust if needed)
        return index * spacing;
    }

    @Override
    public String getValueForDisplay(double displayPosition) {
        int index = (int) (displayPosition / 10); // spacing must match
        if (index >= 0 && index < categories.size()) {
            return categories.get(index);
        }
        return "";
    }

    @Override
    public boolean isValueOnAxis(String value) {
        return categories.contains(value);
    }

    @Override
    public double toNumericValue(String value) {
        return categories.indexOf(value);
    }

    @Override
    public String toRealValue(double value) {
        int index = (int) value;
        if (index >= 0 && index < categories.size()) {
            return categories.get(index);
        }
        return "";
    }

    @Override
    protected List<String> calculateTickValues(double length, Object range) {
        return categories;
    }

    @Override
    protected String getTickMarkLabel(String value) {
        return value; // show timestamp or formatted label directly
    }
}
