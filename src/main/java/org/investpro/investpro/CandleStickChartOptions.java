package org.investpro.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates user-configurable chart display and indicator options.
 */
public class CandleStickChartOptions {
    @Getter
    private final VBox optionsPane;

    private final BooleanProperty verticalGridLinesVisible =
            new SimpleBooleanProperty(this, "Vertical Grid Lines", true);
    private final BooleanProperty horizontalGridLinesVisible =
            new SimpleBooleanProperty(this, "Horizontal Grid Lines", true);
    private final BooleanProperty showVolume =
            new SimpleBooleanProperty(this, "Volume Bars", true);
    private final BooleanProperty alignOpenClose =
            new SimpleBooleanProperty(this, "Align Open/Close", false);
    private final BooleanProperty showSma20 =
            new SimpleBooleanProperty(this, "SMA 20", false);
    private final BooleanProperty showEma50 =
            new SimpleBooleanProperty(this, "EMA 50", false);
    private final BooleanProperty showBollingerBands =
            new SimpleBooleanProperty(this, "Bollinger Bands", false);

    public CandleStickChartOptions() {
        optionsPane = buildOptionsPane();
    }

    public VBox createMirroredOptionsPane() {
        return buildOptionsPane();
    }

    private VBox buildOptionsPane() {
        VBox pane = new VBox(18);
        pane.setPadding(new Insets(20, 8, 20, 8));

        GridPane chartGrid = createOptionGrid(List.of(
                verticalGridLinesVisible,
                horizontalGridLinesVisible,
                showVolume,
                alignOpenClose
        ));

        GridPane indicatorGrid = createOptionGrid(List.of(
                showSma20,
                showEma50,
                showBollingerBands
        ));

        Label chartLabel = createSectionLabel("Chart");
        Label indicatorLabel = createSectionLabel("Indicators");
        pane.getChildren().setAll(chartLabel, chartGrid, indicatorLabel, indicatorGrid);
        return pane;
    }

    private GridPane createOptionGrid(List<BooleanProperty> optionProperties) {
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(20);
        int optionIndex = 0;
        for (BooleanProperty optionProperty : optionProperties) {
            ChartOption newOption = new ChartOption(optionProperty);
            grid.add(newOption.optionLabel, 0, optionIndex);
            grid.add(newOption.optionSwitch, 1, optionIndex);
            newOption.optionSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> optionProperty.set(newVal));
            optionProperty.addListener((obs, oldVal, newVal) -> newOption.optionSwitch.setOn(newVal));
            optionIndex++;
        }
        return grid;
    }

    private Label createSectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty verticalGridLinesVisibleProperty() {
        return verticalGridLinesVisible;
    }

    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty horizontalGridLinesVisibleProperty() {
        return horizontalGridLinesVisible;
    }

    public final boolean isShowVolume() {
        return showVolume.get();
    }

    public final ReadOnlyBooleanProperty showVolumeProperty() {
        return showVolume;
    }

    public final boolean isAlignOpenClose() {
        return alignOpenClose.get();
    }

    public final ReadOnlyBooleanProperty alignOpenCloseProperty() {
        return alignOpenClose;
    }

    public final boolean isShowSma20() {
        return showSma20.get();
    }

    public final ReadOnlyBooleanProperty showSma20Property() {
        return showSma20;
    }

    public final boolean isShowEma50() {
        return showEma50.get();
    }

    public final ReadOnlyBooleanProperty showEma50Property() {
        return showEma50;
    }

    public final boolean isShowBollingerBands() {
        return showBollingerBands.get();
    }

    public final ReadOnlyBooleanProperty showBollingerBandsProperty() {
        return showBollingerBands;
    }

    public void setVerticalGridLinesVisible(boolean visible) {
        verticalGridLinesVisible.set(visible);
    }

    public void setHorizontalGridLinesVisible(boolean visible) {
        horizontalGridLinesVisible.set(visible);
    }

    public void setShowVolume(boolean visible) {
        showVolume.set(visible);
    }

    public void setAlignOpenClose(boolean visible) {
        alignOpenClose.set(visible);
    }

    public void setShowSma20(boolean visible) {
        showSma20.set(visible);
    }

    public void setShowEma50(boolean visible) {
        showEma50.set(visible);
    }

    public void setShowBollingerBands(boolean visible) {
        showBollingerBands.set(visible);
    }

    public boolean isGridVisible() {
        return verticalGridLinesVisible.get() || horizontalGridLinesVisible.get();
    }

    private static class ChartOption {
        private final ToggleSwitch optionSwitch;
        private final Label optionLabel;

        ChartOption(BooleanProperty optionProperty) {
            Objects.requireNonNull(optionProperty);
            optionSwitch = new ToggleSwitch(optionProperty.get());
            optionLabel = new Label(optionProperty.getName() + ':');
        }
    }
}
