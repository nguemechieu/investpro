package org.investpro.investpro.chart;

import javafx.scene.chart.XYChart;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.jetbrains.annotations.NotNull;

public class CandleChartNavigator {

    private final XYChart<String, Number> chart;
    private double mouseAnchorX;
    private double translateAnchorX;

    public CandleChartNavigator(XYChart<String, Number> chart, Pane container) {
        this.chart = chart;
        enableScrollZoom(container);
        enableMouseDrag(container);
    }

    private void enableScrollZoom(@NotNull Pane container) {
        container.setOnScroll((ScrollEvent event) -> {
            if (event.getDeltaY() == 0) return;

            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            chart.setScaleX(chart.getScaleX() * zoomFactor);
            event.consume();
        });
    }

    private void enableMouseDrag(Pane container) {
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
