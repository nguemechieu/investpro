package org.investpro.ui.charts;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


import lombok.Getter;
import lombok.Setter;
import org.investpro.utils.ToggleSwitch;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates all the possible options for a CandleStickChart.
 * Includes display toggles (grid lines, volume) and color theme selection.
 */
@Getter
@Setter
public class CandleStickChartOptions {

    private final VBox optionsPane;
    
    // Color theme property

    private final ObjectProperty<ChartColorPresets.ColorScheme> colorTheme = 
            new SimpleObjectProperty<>(ChartColorPresets.getPreset(ChartColorPresets.DEFAULT));

    public CandleStickChartOptions() {
        optionsPane = new VBox(10);
        optionsPane.setPadding(new Insets(15));
        optionsPane.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f1f5f9;");
        
        // Display options section
        Label displayLabel = new Label("Display Options");
        displayLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        
        GridPane displayGrid = new GridPane();
        displayGrid.setVgap(10);
        displayGrid.setHgap(20);
        int rowIndex = 0;
        
        for (BooleanProperty optionProperty : List.of(
                verticalGridLinesVisible, horizontalGridLinesVisible, showVolume, alignOpenClose)) {
            ChartOption newOption = new ChartOption(optionProperty);
            displayGrid.add(newOption.optionLabel, 0, rowIndex);
            displayGrid.add(newOption.optionSwitch, 1, rowIndex);
            optionProperty.bind(newOption.optionSwitch.selectedProperty());
            rowIndex++;
        }
        
        // Color theme section
        Label colorLabel = new Label("Color Theme");
        colorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-padding: 10 0 0 0;");
        
        ComboBox<String> themeSelector = new ComboBox<>(
                FXCollections.observableArrayList(ChartColorPresets.getPresetNames())
        );
        themeSelector.setValue(ChartColorPresets.DEFAULT);
        themeSelector.setStyle("-fx-padding: 5; -fx-min-width: 200;");
        themeSelector.setOnAction(event ->
            colorTheme.set(ChartColorPresets.getPreset(themeSelector.getValue()))
        );
        
        Label themeLabel = new Label("Preset:");
        themeLabel.setStyle("-fx-text-fill: #cbd5e1;");
        
        GridPane colorGrid = new GridPane();
        colorGrid.setVgap(8);
        colorGrid.setHgap(15);
        colorGrid.add(themeLabel, 0, 0);
        colorGrid.add(themeSelector, 1, 0);
        GridPane.setHgrow(themeSelector, Priority.ALWAYS);
        
        // Add sections to options pane
        optionsPane.getChildren().addAll(
                displayLabel,
                displayGrid,
                colorLabel,
                colorGrid
        );
    }

    /**
     * {@literal true} if vertical grid lines should be drawn at major tick marks along the x-axis
     */
    private final ReadOnlyBooleanWrapper verticalGridLinesVisible = new ReadOnlyBooleanWrapper(true) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Contract(pure = true)
        @Override
        public @NotNull String getName() {
            return "Vertical Grid Lines";
        }
    };

    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty verticalGridLinesVisibleProperty() {
        return verticalGridLinesVisible.getReadOnlyProperty();
    }

    /**
     * {@literal true} if horizontal grid lines should be drawn at major tick marks along the y-axis
     */
    private final ReadOnlyBooleanWrapper horizontalGridLinesVisible = new ReadOnlyBooleanWrapper(true) {
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

    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisible.get();
    }

    public final ReadOnlyBooleanProperty horizontalGridLinesVisibleProperty() {
        return horizontalGridLinesVisible.getReadOnlyProperty();
    }

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

    public final boolean isShowVolume() {
        return showVolume.get();
    }

    public final ReadOnlyBooleanProperty showVolumeProperty() {
        return showVolume.getReadOnlyProperty();
    }

    /**
     * {@literal true} if the close price of candle at index N should be aligned (the same as) with the open price
     * of the candle at index N+1.
     */
    private final ReadOnlyBooleanWrapper alignOpenClose = new ReadOnlyBooleanWrapper(false) {
        @Override
        public Object getBean() {
            return CandleStickChartOptions.this;
        }

        @Contract(pure = true)
        @Override
        public @NotNull String getName() {
            return "Align Open/Close";
        }
    };

    public final boolean isAlignOpenClose() {
        return alignOpenClose.get();
    }

    public final ReadOnlyBooleanProperty alignOpenCloseProperty() {
        return alignOpenClose.getReadOnlyProperty();
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

