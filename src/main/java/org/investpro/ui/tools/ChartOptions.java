package org.investpro.ui.tools;

import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.controlsfx.control.ToggleSwitch;

/**
 * Encapsulates all of the possible options for a CandleStickChart.
 *
 * @author Michael Ennen
 */
public class ChartOptions {
    @Getter
    private final VBox optionsPane;
    private final ChartOption verticalGridOption;
    private final ChartOption horizontalGridOption;
    private final ChartOption showVolumeOption;
    private final ChartOption alignOpenCloseOption;
    private final ChartOption showNewsEventsOption;

    public ChartOptions() {
        optionsPane = new VBox();
        optionsPane.getStyleClass().add("chart-options-pane");

        GridPane optionsGrid = new GridPane();
        optionsGrid.getStyleClass().add("chart-options-grid");
        int numOptions = 0;
        optionsGrid.setVgap(10);
        optionsGrid.setHgap(20);
        verticalGridOption = addOptionRow(optionsGrid, numOptions++, verticalGridLinesVisible);
        horizontalGridOption = addOptionRow(optionsGrid, numOptions++, horizontalGridLinesVisible);
        showVolumeOption = addOptionRow(optionsGrid, numOptions++, showVolume);
        alignOpenCloseOption = addOptionRow(optionsGrid, numOptions++, alignOpenClose);
        showNewsEventsOption = addOptionRow(optionsGrid, numOptions++, showNewsEvents);
        optionsPane.getChildren().setAll(optionsGrid);
        optionsPane.setPadding(new Insets(14, 16, 16, 16));
    }

    private ChartOption addOptionRow(GridPane grid, int index, BooleanProperty optionProperty) {
        ChartOption option = new ChartOption(optionProperty);
        grid.add(option.optionLabel, 0, index);
        grid.add(option.optionSwitch, 1, index);
        optionProperty.bind(option.optionSwitch.selectedProperty());
        return option;
    }

    /**
     * {@literal true} if vertical grid lines should be drawn at major tick marks
     * along the x-axis
     */
    private final ReadOnlyBooleanWrapper verticalGridLinesVisible = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return ChartOptions.this;
        }

        @Override
        public String getName() {
            return "Vertical Grid Lines";
        }
    };

    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty verticalGridLinesVisibleProperty() {
        return verticalGridLinesVisible.getReadOnlyProperty();
    }

    public void setVerticalGridLinesVisible(boolean visible) {
        if (verticalGridOption != null) {
            verticalGridOption.optionSwitch.setSelected(visible);
        }
    }

    /**
     * {@literal true} if horizontal grid lines should be drawn at major tick marks
     * along the y-axis
     */
    private final ReadOnlyBooleanWrapper horizontalGridLinesVisible = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return ChartOptions.this;
        }

        @Override
        public String getName() {
            return "Horizontal Grid Lines";
        }
    };

    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty horizontalGridLinesVisibleProperty() {
        return horizontalGridLinesVisible.getReadOnlyProperty();
    }

    public void setHorizontalGridLinesVisible(boolean visible) {
        if (horizontalGridOption != null) {
            horizontalGridOption.optionSwitch.setSelected(visible);
        }
    }

    /**
     * {@literal true} if volume bars should be drawn along the bottom of the chart
     */
    private final ReadOnlyBooleanWrapper showVolume = new ReadOnlyBooleanWrapper(true) {
        @Override
        public Object getBean() {
            return ChartOptions.this;
        }

        @Override
        public String getName() {
            return "Volume Bars";
        }
    };

    public final boolean isShowVolume() {
        return showVolume.get();
    }

    public final ReadOnlyBooleanProperty showVolumeProperty() {
        return showVolume.getReadOnlyProperty();
    }

    public void setShowVolume(boolean visible) {
        if (showVolumeOption != null) {
            showVolumeOption.optionSwitch.setSelected(visible);
        }
    }

    /**
     * {@literal true} if the close price of candle at index N should be aligned
     * (the same as) with the open price
     * of the candle at index N+1.
     */
    private final ReadOnlyBooleanWrapper alignOpenClose = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return ChartOptions.this;
        }

        @Override
        public String getName() {
            return "Align Open/Close";
        }
    };

    public final boolean isAlignOpenClose() {
        return alignOpenClose.get();
    }

    public final ReadOnlyBooleanProperty alignOpenCloseProperty() {
        return alignOpenClose.getReadOnlyProperty();
    }

    public void setAlignOpenClose(boolean enabled) {
        if (alignOpenCloseOption != null) {
            alignOpenCloseOption.optionSwitch.setSelected(enabled);
        }
    }

    /**
     * {@literal true} if economic/news events should be drawn on the chart.
     */
    private final ReadOnlyBooleanWrapper showNewsEvents = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return ChartOptions.this;
        }

        @Override
        public String getName() {
            return "News Events";
        }
    };

    public final boolean isShowNewsEvents() {
        return showNewsEvents.get();
    }

    public final ReadOnlyBooleanProperty showNewsEventsProperty() {
        return showNewsEvents.getReadOnlyProperty();
    }

    public void setShowNewsEvents(boolean visible) {
        if (showNewsEventsOption != null) {
            showNewsEventsOption.optionSwitch.setSelected(visible);
        }
    }

    private static class ChartOption {
        private final ToggleSwitch optionSwitch;
        private final Label optionLabel;

        ChartOption(BooleanProperty optionProperty) {
            Objects.requireNonNull(optionProperty);
            optionSwitch = new ToggleSwitch(optionProperty.getName());
            optionSwitch.getStyleClass().add("chart-options-switch");
            optionLabel = new Label(optionProperty.getName() + ':');
            optionLabel.getStyleClass().add("chart-options-label");
        }
    }
}
