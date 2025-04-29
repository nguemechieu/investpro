package org.investpro.chart;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

@Getter
@Setter
public class ChartToolbar extends HBox {

    private final Button resetZoomButton;
    private final Button refreshButton;
    private final ComboBox<String> intervalSelector;

    public ChartToolbar(Runnable onResetZoom, Runnable onRefresh, Consumer<String> onIntervalChange) {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        resetZoomButton = new Button("Reset Zoom");
        resetZoomButton.setOnAction(e -> onResetZoom.run());

        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> onRefresh.run());

        intervalSelector = new ComboBox<>();
        intervalSelector.getItems().addAll("1m", "5m", "15m", "1h", "1d");
        intervalSelector.setValue("1m");
        intervalSelector.setOnAction(e -> onIntervalChange.accept(intervalSelector.getValue()));

        getChildren().addAll(resetZoomButton, refreshButton, intervalSelector);
    }

    public String getSelectedInterval() {
        return intervalSelector.getValue();
    }
}
