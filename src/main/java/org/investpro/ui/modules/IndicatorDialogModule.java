package org.investpro.ui.modules;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.investpro.indicators.Indicator;
import org.investpro.spi.PluginIndicatorFactory;
import org.investpro.spi.PluginRegistry;
import org.investpro.ui.charts.CandleStickChart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class IndicatorDialogModule {

    public void openInsertIndicatorDialog(
            CandleStickChart chart,
            Window owner,
            String dialogTitle,
            String dialogHeader,
            String dialogContent,
            Consumer<String> activityLogger,
            BiConsumer<String, String> warningHandler) {
        if (chart == null) {
            return;
        }

        List<String> choices = new ArrayList<>();
        choices.add("Clear Indicators");
        choices.addAll(PluginIndicatorFactory.supportedChoices());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(1), choices);
        dialog.setTitle(dialogTitle);
        dialog.setHeaderText(dialogHeader);
        dialog.setContentText(dialogContent);
        dialog.initOwner(owner);

        dialog.showAndWait().ifPresent(choice -> {
            if ("Clear Indicators".equals(choice)) {
                chart.clearIndicators();
                if (activityLogger != null) {
                    activityLogger.accept("Cleared indicators from active chart.");
                }
                return;
            }

            Map<String, String> config = promptIndicatorConfiguration(choice, owner);
            if (config == null) {
                return;
            }

            PluginIndicatorFactory.saveConfig(choice, config);
            Indicator indicator = createChartIndicator(choice, config);
            if (indicator == null) {
                if (warningHandler != null) {
                    warningHandler.accept("Indicators", "Unsupported indicator: " + choice);
                }
                return;
            }

            chart.addIndicator(indicator);
            if (activityLogger != null) {
                activityLogger.accept("Inserted indicator %s.".formatted(indicator.getName()));
            }
        });
    }

    private Map<String, String> promptIndicatorConfiguration(String choice, Window owner) {
        List<PluginIndicatorFactory.IndicatorParameter> parameters = PluginIndicatorFactory.parametersFor(choice);
        Map<String, String> defaults = PluginIndicatorFactory.loadConfig(choice);

        if (parameters.isEmpty()) {
            return new LinkedHashMap<>(defaults);
        }

        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(choice + " settings");
        dialog.setHeaderText("Adjust parameters before adding the indicator.");
        dialog.initOwner(owner);

        ButtonType addButton = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        Map<String, TextField> fields = new LinkedHashMap<>();
        int row = 0;
        for (PluginIndicatorFactory.IndicatorParameter parameter : parameters) {
            Label label = new Label(parameter.label());
            TextField field = new TextField(defaults.getOrDefault(parameter.key(), parameter.defaultValue()));
            field.setPromptText(parameter.description());
            field.setMaxWidth(Double.MAX_VALUE);
            grid.add(label, 0, row);
            grid.add(field, 1, row);
            GridPane.setHgrow(field, Priority.ALWAYS);
            fields.put(parameter.key(), field);
            row++;
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(420);
        scrollPane.setPrefViewportHeight(Math.min(360, 72 + parameters.size() * 44));
        dialog.getDialogPane().setContent(scrollPane);

        dialog.setResultConverter(button -> {
            if (button != addButton) {
                return null;
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, TextField> entry : fields.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getText().trim());
            }
            return values;
        });

        return dialog.showAndWait().orElse(null);
    }

    private Indicator createChartIndicator(String choice, Map<String, String> config) {
        return PluginIndicatorFactory.create(choice, PluginRegistry.loadDefault(), config).orElse(null);
    }
}
