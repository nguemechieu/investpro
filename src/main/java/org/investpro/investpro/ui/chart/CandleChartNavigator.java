package org.investpro.investpro.ui.chart;

import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import org.jetbrains.annotations.NotNull;

public class CandleChartNavigator {

    private final CandleStickChart chart;
    private double mouseAnchorX;
    private double translateAnchorX;

    public CandleChartNavigator(CandleStickChart chart, ChartLayout container) {
        this.chart = chart;
        enableScrollZoom(container);
        enableMouseDrag(container);
    }

    private void enableScrollZoom(@NotNull ChartLayout container) {
        container.setOnScroll((ScrollEvent event) -> {
            if (event.getDeltaY() == 0) return;

            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            chart.setScaleX(chart.getScaleX() * zoomFactor);
            event.consume();
        });
    }

    private void enableMouseDrag(ChartLayout container) {
        container.setOnMousePressed((MouseEvent event) -> {
            mouseAnchorX = event.getSceneX();
            translateAnchorX = chart.getTranslateX();
        });

        container.setOnMouseDragged((MouseEvent event) -> {
            double dragDeltaX = event.getSceneX() - mouseAnchorX;
            chart.setTranslateX(translateAnchorX + dragDeltaX);
        });
    }
}
