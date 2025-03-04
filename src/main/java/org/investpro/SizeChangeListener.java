package org.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 📌 **SizeChangeListener**
 * - Monitors changes in container **width** and **height**.
 * - Ensures layout updates only when the size is stable.
 */
public class SizeChangeListener implements ChangeListener<Number> {

    private final BooleanProperty gotFirstSize;
    private final ObservableNumberValue containerWidth;
    private final ObservableNumberValue containerHeight;
    private boolean sizeUpdated = false;
    private final CandleStickChartToolbar chart;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * **Constructor**
     * @param gotFirstSize  Indicates if the size has stabilized.
     * @param containerWidth  The observable width of the container.
     * @param containerHeight The observable height of the container.
     * @param chart The chart instance to be resized.
     */
    public SizeChangeListener(BooleanProperty gotFirstSize, ObservableNumberValue containerWidth,
                              ObservableNumberValue containerHeight, CandleStickChartToolbar chart) {
        this.gotFirstSize = gotFirstSize;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        this.chart = chart;
    }

    /**
     * **Handles Size Changes**
     * - Ensures the chart resizes only after it stabilizes.
     * - Prevents excessive updates to improve performance.
     *
     * @param observable The observed size property (width or height).
     * @param oldValue The previous size.
     * @param newValue The new size.
     */
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!sizeUpdated && containerWidth.doubleValue() > 0 && containerHeight.doubleValue() > 0) {
            sizeUpdated = true;
            gotFirstSize.set(true);  // Size has now stabilized.

        }
    }

    public void resize() {

    }


    /**
     * **Resizes the Chart Layout**
     * - Dynamically adjusts chart elements when the container size changes.
     * - Updates fonts, paddings, and element positions.
     * - Ensures responsiveness and scalability.
     */






}
