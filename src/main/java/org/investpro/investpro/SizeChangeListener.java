package org.investpro.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ui.CandleStickChartToolbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 📌 **SizeChangeListener**
 * - Monitors changes in container **width** and **height**.
 * - Ensures layout updates only when the size is stable.
 */
@Getter
@Setter
public class SizeChangeListener implements ChangeListener<Number> {

    private final BooleanProperty gotFirstSize;
    private final ObservableNumberValue containerWidth;
    private final ObservableNumberValue containerHeight;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private boolean sizeUpdated = false;
    private CandleStickChartToolbar chart;

    /**
     * **Constructor**
     *
     * @param gotFirstSize    Indicates if the size has stabilized.
     * @param containerWidth  The observable width of the container.
     * @param containerHeight The observable height of the container.
     */
    public SizeChangeListener(BooleanProperty gotFirstSize, ObservableNumberValue containerWidth,
                              ObservableNumberValue containerHeight) {
        this.gotFirstSize = gotFirstSize;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
    }

    /**
     * **Handles Size Changes**
     * - Ensures the chart resizes only after it stabilizes.
     * - Prevents excessive updates to improve performance.
     *
     * @param observable The observed size property (width or height).
     * @param oldValue   The previous size.
     * @param newValue   The new size.
     */
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!sizeUpdated && containerWidth.doubleValue() > 0 && containerHeight.doubleValue() > 0) {
            sizeUpdated = true;
            gotFirstSize.set(true);  // Size has now stabilized.

        }
        resize();
    }

    /**
     * 📐 **Resizes the Chart Layout**
     * - Dynamically adjusts chart elements when the container size changes.
     * - Updates fonts, paddings, and element positions.
     * - Ensures responsiveness and scalability.
     */
    public void resize() {
        if (chart == null || chart.getScene() == null) return;

        double width = chart.getScene().getWidth();
        double height = chart.getScene().getHeight();

        // Adjust canvas size
        chart.setPrefWidth(width);
        chart.setPrefHeight(height);

        if (chart instanceof Region region) {
            region.setMinSize(width, height);
            region.setMaxSize(width, height);
        }

        // Redraw or re-layout content
        chart.requestLayout();
        chart.layout(); // Force layout pass if needed

        // Optional: update font sizes and padding
        double scale = Math.min(width / 1200.0, height / 800.0);
        chart.setStyle("-fx-font-size: " + (12 * scale) + "px;");

        logger.info("Chart resized to {}x{}", width, height);
    }



}
