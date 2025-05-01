package org.investpro.investpro;

import java.util.List;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.investpro.investpro.ToggleSwitch;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates all the possible options for a CandleStickChart.
 */
public class CandleStickChartOptions {
    @Getter
    private final VBox optionsPane;
    /**
     * {@literal true} if vertical grid lines should be drawn at major tick marks along the x-axis
     */
    private final ReadOnlyBooleanWrapper verticalGridLinesVisible = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Override
        public String getName() {
            return "Vertical Grid Lines";
        }
    };
    /**
     * {@literal true} if horizontal grid lines should be drawn at major tick marks along the y-axis
     */
    private final ReadOnlyBooleanWrapper horizontalGridLinesVisible = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Contract(pure = true)
        @Override
        public @NotNull String getName() {
            return "Horizontal Grid Lines";
        }
    };
    /**
     * {@literal true} if volume bars should be drawn along the bottom of the chart
     */
    private final ReadOnlyBooleanWrapper showVolume = new ReadOnlyBooleanWrapper(true) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Contract(pure = true)
        @Override
        public @NotNull String getName() {
            return "Volume Bars";
        }
    };
    /**
     * {@literal true} if the close price of candle at index N should be aligned (the same as) with the open price
     * of the candle at index N+1.
     */
    private final ReadOnlyBooleanWrapper alignOpenClose = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Override
        public String getName() {
            return "Align Open/Close";
        }
    };

    public CandleStickChartOptions() {
        optionsPane = new VBox();
        GridPane optionsGrid = new GridPane();
        int numOptions = 0;
        optionsGrid.setVgap(10);
        optionsGrid.setHgap(20);
        for (BooleanProperty optionProperty : List.of(
                verticalGridLinesVisible, horizontalGridLinesVisible, showVolume, alignOpenClose)) {
            ChartOption newOption = new ChartOption(optionProperty);
            int optionIndex = numOptions++;
            optionsGrid.add(newOption.optionLabel, 0, optionIndex);
            optionsGrid.add(newOption.optionSwitch, 1, optionIndex);
            optionProperty.bind(newOption.optionSwitch.selectedProperty());
        }
        optionsPane.getChildren().setAll(optionsGrid);
        optionsPane.setPadding(new Insets(20, 5, 20, 5));
    }

    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty verticalGridLinesVisibleProperty() {
        return verticalGridLinesVisible.getReadOnlyProperty();
    }

    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty horizontalGridLinesVisibleProperty() {
        return horizontalGridLinesVisible.getReadOnlyProperty();
    }

    public final boolean isShowVolume() {
        return showVolume.get();
    }

    public final ReadOnlyBooleanProperty showVolumeProperty() {
        return showVolume.getReadOnlyProperty();
    }

    public final boolean isAlignOpenClose() {
        return alignOpenClose.get();
    }

    public final ReadOnlyBooleanProperty alignOpenCloseProperty() {
        return alignOpenClose.getReadOnlyProperty();
    }

    public boolean isGridVisible() {
        return verticalGridLinesVisible.get() || horizontalGridLinesVisible.get();
    }

    public void setGridVisible(boolean b) {
//        verticalGridLinesVisible.set(b);
//        horizontalGridLinesVisible.set(b);
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
