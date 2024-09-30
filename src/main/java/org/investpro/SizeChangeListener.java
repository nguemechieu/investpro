package org.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;

/**
 * A listener that handles changes in the width and height of the container that holds the chart.
 * Once the chart's size stabilizes (after fluctuating during initialization), the chart layout is updated accordingly.
 */
public class SizeChangeListener implements ChangeListener<Number> {

    private final BooleanProperty gotFirstSize;
    private final ObservableNumberValue containerWidth;
    private final ObservableNumberValue containerHeight;
    private boolean sizeUpdated = false;

    /**
     * Constructs a SizeChangeListener that listens for changes in the container's width and height.
     *
     * @param gotFirstSize  a boolean property indicating if the size has stabilized.
     * @param containerWidth  the width of the container.
     * @param containerHeight the height of the container.
     */
    public SizeChangeListener(BooleanProperty gotFirstSize, ObservableNumberValue containerWidth, ObservableNumberValue containerHeight) {
        this.gotFirstSize = gotFirstSize;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
    }

    /**
     * Invoked when the observed size property changes. Once the size stabilizes (doesn't fluctuate),
     * it updates the chart layout and disables further size updates.
     *
     * @param observable the observed size property (width or height).
     * @param oldValue the previous size value.
     * @param newValue the new size value.
     */
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        // Prevent unnecessary updates if the size has already been set once.
        if (!sizeUpdated && containerWidth.doubleValue() > 0 && containerHeight.doubleValue() > 0) {
            sizeUpdated = true;
            gotFirstSize.set(true);  // Signals that the size is now stable.
        }
    }
}
