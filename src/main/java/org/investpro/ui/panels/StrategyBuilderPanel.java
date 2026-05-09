package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.enums.StrategyCategory;
import org.investpro.indicators.INDICATORS;
import org.investpro.i18n.LocalizationService;
import org.investpro.enums.timeframe.Timeframe;

import java.util.Arrays;

/**
 * Strategy Builder Panel - Allows users to build and configure custom trading
 * strategies.
 * Provides a UI to select indicators, set parameters, define entry/exit rules.
 */
@Slf4j
@Getter
@Setter
public class StrategyBuilderPanel extends VBox {

    private TextField strategyNameField;
    private ComboBox<StrategyCategory> categoryCombo;
    private ComboBox<Timeframe> timeframeCombo;
    private ComboBox<String> indicatorCombo;
    private TextArea strategyDescriptionArea;
    private TableView<StrategyParameter> parametersTable;
    private SystemCore systemCore;
    public StrategyBuilderPanel(SystemCore systemCore) {
        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("strategy-builder-panel");
        this.systemCore = systemCore;
        setupUI();
        LocalizationService.applyTranslations(this);
    }

    private void setupUI() {
        // Header
        Label titleLabel = new Label("Strategy Builder");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Basic Information Section
        VBox basicInfoBox = createBasicInfoSection();

        // Indicators & Parameters Section
        VBox indicatorsBox = createIndicatorsSection();

        // Strategy Logic Section
        VBox logicBox = createStrategyLogicSection();

        // Action Buttons
        HBox actionBox = createActionButtons();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(basicInfoBox, new Separator(), indicatorsBox, new Separator(), logicBox);
        scrollPane.setContent(content);

        getChildren().addAll(titleLabel, scrollPane, actionBox);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createBasicInfoSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Basic Information");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Strategy Name
        HBox nameBox = createLabeledInput("Strategy Name:", strategyNameField = new TextField());
        strategyNameField.setPromptText("e.g., My Mean Reversion Bot");
        strategyNameField.setPrefHeight(35);

        // Category Selection
        categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(StrategyCategory.values());
        categoryCombo.setPrefHeight(35);
        HBox categoryBox = createLabeledInput("Category:", categoryCombo);

        // Timeframe Selection
        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().addAll(Timeframe.values());
        timeframeCombo.setPrefHeight(35);
        HBox timeframeBox = createLabeledInput("Primary Timeframe:", timeframeCombo);

        section.getChildren().addAll(sectionTitle, nameBox, categoryBox, timeframeBox);
        return section;
    }

    private VBox createIndicatorsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #10b981; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Indicators & Parameters");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Indicator Selection
        indicatorCombo = new ComboBox<>();
        indicatorCombo.getItems().addAll(
                Arrays.toString(INDICATORS.values())

        );
        indicatorCombo.setPrefHeight(35);
        HBox indicatorBox = createLabeledInput("Add Indicator:", indicatorCombo);

        Button addIndicatorBtn = new Button("+ Add");
        addIndicatorBtn.setStyle(
                "-fx-padding: 8px 20px; -fx-font-size: 12px; -fx-background-color: #10b981; -fx-text-fill: white;");
        addIndicatorBtn.setOnAction(e -> addIndicatorToTable());

        HBox addBox = new HBox(10, indicatorBox, addIndicatorBtn);
        HBox.setHgrow(indicatorBox, Priority.ALWAYS);

        // Parameters Table
        parametersTable = createParametersTable();

        section.getChildren().addAll(sectionTitle, addBox, new Label("Configured Indicators:"), parametersTable);
        VBox.setVgrow(parametersTable, Priority.ALWAYS);
        return section;
    }

    private VBox createStrategyLogicSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #f59e0b; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Strategy Logic");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label descLabel = new Label("Strategy Description & Rules:");
        descLabel.setStyle("-fx-text-fill: #a0aec0;");

        strategyDescriptionArea = new TextArea();
        strategyDescriptionArea.setPromptText("Describe your strategy entry/exit rules and logic...");
        strategyDescriptionArea.setWrapText(true);
        strategyDescriptionArea.setPrefHeight(150);
        strategyDescriptionArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        section.getChildren().addAll(sectionTitle, descLabel, strategyDescriptionArea);
        VBox.setVgrow(strategyDescriptionArea, Priority.ALWAYS);
        return section;
    }

    private HBox createActionButtons() {
        Button validateBtn = new Button("✓ Validate");
        validateBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #3b82f6; -fx-text-fill: white;");
        validateBtn.setOnAction(e -> validateStrategy());

        Button saveBtn = new Button("💾 Save Strategy");
        saveBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #10b981; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> saveStrategy());

        Button testBtn = new Button("🧪 Test");
        testBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        testBtn.setOnAction(e -> testStrategy());

        Button resetBtn = new Button("↻ Reset");
        resetBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #ef4444; -fx-text-fill: white;");
        resetBtn.setOnAction(e -> resetForm());

        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.setPadding(new Insets(12));
        actionBox.getChildren().addAll(validateBtn, saveBtn, testBtn, resetBtn);
        return actionBox;
    }

    private TableView<StrategyParameter> createParametersTable() {
        TableView<StrategyParameter> table = new TableView<>();
        table.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        TableColumn<StrategyParameter, String> nameCol = new TableColumn<>("Indicator");
        nameCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().name()));
        nameCol.setPrefWidth(150);

        TableColumn<StrategyParameter, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().parameter()));
        paramCol.setPrefWidth(150);

        TableColumn<StrategyParameter, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(
                param -> new javafx.beans.property.SimpleStringProperty(param.getValue().value()));
        valueCol.setPrefWidth(100);

        TableColumn<StrategyParameter, String> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, paramCol, valueCol, actionCol);
        return table;
    }

    private HBox createLabeledInput(String label, Control input) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #a0aec0; -fx-min-width: 150px;");
        HBox box = new HBox(10, lbl, input);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        return box;
    }

    private void addIndicatorToTable() {
        if (indicatorCombo.getValue() != null) {
            StrategyParameter param = new StrategyParameter(indicatorCombo.getValue(), "Period", "14");
            parametersTable.getItems().add(param);
            log.info("Added indicator: {}", indicatorCombo.getValue());
        }
    }

    private void validateStrategy() {
        if (strategyNameField.getText().isEmpty()) {
            showAlert("Validation Error", "Please enter a strategy name");
            return;
        }
        if (categoryCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a category");
            return;
        }
        if (parametersTable.getItems().isEmpty()) {
            showAlert("Validation Error", "Please add at least one indicator");
            return;
        }
        showAlert("Success", "Strategy validation passed! ✓");
        log.info("Strategy validated: {}", strategyNameField.getText());
    }

    private void saveStrategy() {
        validateStrategy();
        log.info("Strategy saved: {}", strategyNameField.getText());
        showAlert("Success", "Strategy saved successfully!");
    }

    private void testStrategy() {
        validateStrategy();
        log.info("Testing strategy: {}", strategyNameField.getText());
        showAlert("Info", "Opening backtest for: " + strategyNameField.getText());
    }

    private void resetForm() {
        strategyNameField.clear();
        categoryCombo.setValue(null);
        timeframeCombo.setValue(null);
        indicatorCombo.setValue(null);
        strategyDescriptionArea.clear();
        parametersTable.getItems().clear();
        log.info("Form reset");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
         * Inner class for strategy parameters
         */
        public record StrategyParameter(String name, String parameter, String value) {

    }
}
