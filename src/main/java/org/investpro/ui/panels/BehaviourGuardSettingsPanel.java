package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.risk.BehaviourGuardConfig;
import org.investpro.risk.BehaviourGuardService;
import org.investpro.risk.BehaviourGuardService.BehaviourGuardValidation;

/**
 * Behaviour Guard Settings Panel - Risk monitoring and trade guard settings
 */
@Slf4j
public class BehaviourGuardSettingsPanel extends VBox {

    private CheckBox enableBehaviourGuardCheckbox;
    private CheckBox enableDrawdownProtectionCheckbox;
    private CheckBox enableWinStreakLimitCheckbox;
    private CheckBox enableLossStreakLimitCheckbox;
    private CheckBox enableTradingHoursCheckbox;
    private CheckBox enableVolatilityFilterCheckbox;
    private CheckBox enableEquityGuardCheckbox;

    private Spinner<Double> maxDrawdownSpinner;
    private Spinner<Integer> maxConsecutiveWinsSpinner;
    private Spinner<Integer> maxConsecutiveLossesSpinner;
    private Spinner<Double> maxVolatilityThresholdSpinner;
    private Spinner<Double> minEquityThresholdSpinner;

    private ComboBox<String> tradingStartTimeCombo;
    private ComboBox<String> tradingEndTimeCombo;
    private ComboBox<String> volatilitySourceCombo;

    private TextArea behaviorNotesArea;

    public BehaviourGuardSettingsPanel() {
        this.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 16;");
        this.setSpacing(12);

        // Title
        Label titleLabel = new Label("Behaviour Guard Settings");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Guard Status Section
        VBox guardStatusSection = createGuardStatusSection();

        // Drawdown & Equity Protection
        VBox equitySection = createEquityProtectionSection();

        // Win/Loss Streak Limits
        VBox streakSection = createStreakLimitSection();

        // Trading Hours
        VBox tradingHoursSection = createTradingHoursSection();

        // Volatility Filter
        VBox volatilitySection = createVolatilityFilterSection();

        // Notes & Configuration
        VBox notesSection = createNotesSection();

        // Buttons
        HBox buttonBox = createButtonBox();

        this.getChildren().addAll(
                titleLabel,
                new Separator(),
                guardStatusSection,
                equitySection,
                streakSection,
                tradingHoursSection,
                volatilitySection,
                notesSection,
                new Separator(),
                buttonBox);

        VBox.setVgrow(notesSection, Priority.ALWAYS);
    }

    private VBox createGuardStatusSection() {
        Label sectionTitle = new Label("Guard Status");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #ef4444;");

        VBox box = new VBox(8);
        box.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 12;");

        enableBehaviourGuardCheckbox = new CheckBox("Enable Behaviour Guard (Master Switch)");
        enableBehaviourGuardCheckbox.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        enableBehaviourGuardCheckbox.setSelected(true);

        enableDrawdownProtectionCheckbox = new CheckBox("Enable Drawdown Protection");
        enableDrawdownProtectionCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        enableEquityGuardCheckbox = new CheckBox("Enable Equity Guard");
        enableEquityGuardCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        box.getChildren().addAll(
                enableBehaviourGuardCheckbox,
                enableDrawdownProtectionCheckbox,
                enableEquityGuardCheckbox);

        return new VBox(8, sectionTitle, box);
    }

    private VBox createEquityProtectionSection() {
        Label sectionTitle = new Label("Equity Protection");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Max Drawdown
        maxDrawdownSpinner = new Spinner<>(0.0, 100.0, 5.0, 0.5);
        maxDrawdownSpinner.setEditable(true);
        maxDrawdownSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label drawdownLabel = new Label("Max Drawdown (%):");
        drawdownLabel.setStyle("-fx-text-fill: #a0aec0;");

        // Min Equity Threshold
        minEquityThresholdSpinner = new Spinner<>(0.0, 1000000.0, 1000.0, 100.0);
        minEquityThresholdSpinner.setEditable(true);
        minEquityThresholdSpinner.getEditor()
                .setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label equityLabel = new Label("Min Equity Threshold ($):");
        equityLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, drawdownLabel, maxDrawdownSpinner);
        grid.addRow(1, equityLabel, minEquityThresholdSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createStreakLimitSection() {
        Label sectionTitle = new Label("Win/Loss Streak Limits");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        enableWinStreakLimitCheckbox = new CheckBox("Limit Consecutive Winning Trades");
        enableWinStreakLimitCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        // Max Consecutive Wins
        maxConsecutiveWinsSpinner = new Spinner<>(1, 100, 10, 1);
        maxConsecutiveWinsSpinner.setEditable(true);
        maxConsecutiveWinsSpinner.getEditor()
                .setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label winsLabel = new Label("Max Consecutive Wins:");
        winsLabel.setStyle("-fx-text-fill: #a0aec0;");

        enableLossStreakLimitCheckbox = new CheckBox("Limit Consecutive Losing Trades");
        enableLossStreakLimitCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        // Max Consecutive Losses
        maxConsecutiveLossesSpinner = new Spinner<>(1, 100, 5, 1);
        maxConsecutiveLossesSpinner.setEditable(true);
        maxConsecutiveLossesSpinner.getEditor()
                .setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label lossesLabel = new Label("Max Consecutive Losses:");
        lossesLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, enableWinStreakLimitCheckbox);
        grid.addRow(1, winsLabel, maxConsecutiveWinsSpinner);
        grid.addRow(2, enableLossStreakLimitCheckbox);
        grid.addRow(3, lossesLabel, maxConsecutiveLossesSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createTradingHoursSection() {
        Label sectionTitle = new Label("Trading Hours Guard");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        enableTradingHoursCheckbox = new CheckBox("Enable Trading Hours Restriction");
        enableTradingHoursCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        tradingStartTimeCombo = new ComboBox<>();
        tradingStartTimeCombo.getItems().addAll(generateTimeOptions());
        tradingStartTimeCombo.setValue("00:00");
        tradingStartTimeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label startLabel = new Label("Trading Start Time (UTC):");
        startLabel.setStyle("-fx-text-fill: #a0aec0;");

        tradingEndTimeCombo = new ComboBox<>();
        tradingEndTimeCombo.getItems().addAll(generateTimeOptions());
        tradingEndTimeCombo.setValue("23:59");
        tradingEndTimeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label endLabel = new Label("Trading End Time (UTC):");
        endLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, enableTradingHoursCheckbox);
        grid.addRow(1, startLabel, tradingStartTimeCombo);
        grid.addRow(2, endLabel, tradingEndTimeCombo);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createVolatilityFilterSection() {
        Label sectionTitle = new Label("Volatility Filter");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #8b5cf6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        enableVolatilityFilterCheckbox = new CheckBox("Enable Volatility Filter");
        enableVolatilityFilterCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        maxVolatilityThresholdSpinner = new Spinner<>(0.0, 100.0, 50.0, 5.0);
        maxVolatilityThresholdSpinner.setEditable(true);
        maxVolatilityThresholdSpinner.getEditor()
                .setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label volatilityLabel = new Label("Max Volatility Threshold (%):");
        volatilityLabel.setStyle("-fx-text-fill: #a0aec0;");

        volatilitySourceCombo = new ComboBox<>();
        volatilitySourceCombo.getItems().addAll("VIX", "ATR", "Historical Volatility", "Implied Volatility");
        volatilitySourceCombo.setValue("ATR");
        volatilitySourceCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label sourceLabel = new Label("Volatility Source:");
        sourceLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, enableVolatilityFilterCheckbox);
        grid.addRow(1, volatilityLabel, maxVolatilityThresholdSpinner);
        grid.addRow(2, sourceLabel, volatilitySourceCombo);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createNotesSection() {
        Label sectionTitle = new Label("Guard Notes & Configuration");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #ec4899;");

        behaviorNotesArea = new TextArea();
        behaviorNotesArea.setPromptText("Document your behaviour guard settings, exceptions, and trading rules...");
        behaviorNotesArea.setWrapText(true);
        behaviorNotesArea.setStyle(
                "-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-padding: 8;");

        VBox box = new VBox(8, sectionTitle, behaviorNotesArea);
        VBox.setVgrow(behaviorNotesArea, Priority.ALWAYS);
        return box;
    }

    private HBox createButtonBox() {
        Button saveButton = new Button("Save Guard Settings");
        saveButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        saveButton.setOnAction(e -> saveGuardSettings());

        Button testButton = new Button("Test Guard");
        testButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        testButton.setOnAction(e -> testGuard());

        Button resetButton = new Button("Reset to Defaults");
        resetButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        resetButton.setOnAction(e -> resetGuardSettings());

        Button closeButton = new Button("Close");
        closeButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #374151; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, spacer, saveButton, testButton, resetButton, closeButton);
    }

    private void saveGuardSettings() {
        try {
            BehaviourGuardConfig config = BehaviourGuardConfig.builder()
                    .guardEnabled(enableBehaviourGuardCheckbox.isSelected())
                    .drawdownProtectionEnabled(enableDrawdownProtectionCheckbox.isSelected())
                    .maxDrawdownPercent(maxDrawdownSpinner.getValue())
                    .equityGuardEnabled(enableEquityGuardCheckbox.isSelected())
                    .minEquityThreshold(minEquityThresholdSpinner.getValue())
                    .winStreakLimitEnabled(enableWinStreakLimitCheckbox.isSelected())
                    .maxConsecutiveWins(maxConsecutiveWinsSpinner.getValue())
                    .lossStreakLimitEnabled(enableLossStreakLimitCheckbox.isSelected())
                    .maxConsecutiveLosses(maxConsecutiveLossesSpinner.getValue())
                    .tradingHoursEnabled(enableTradingHoursCheckbox.isSelected())
                    .tradingStartTime(tradingStartTimeCombo.getValue())
                    .tradingEndTime(tradingEndTimeCombo.getValue())
                    .volatilityFilterEnabled(enableVolatilityFilterCheckbox.isSelected())
                    .maxVolatilityPercent(maxVolatilityThresholdSpinner.getValue())
                    .volatilitySource(volatilitySourceCombo.getValue())
                    .notes(behaviorNotesArea.getText())
                    .build();

            // Validate configuration
            BehaviourGuardService service = BehaviourGuardService.getInstance();
            BehaviourGuardValidation validation = service.validate(config);

            if (!validation.isValid()) {
                String errors = String.join("\n", validation.getErrors());
                showAlert(Alert.AlertType.ERROR, "Validation Failed",
                        "Guard settings contain errors:\n" + errors);
                return;
            }

            // Save the configuration
            service.saveConfig(config);
            log.info("Behaviour Guard Settings Saved: {}", config);
            showAlert(Alert.AlertType.INFORMATION, "Success",
                    "Behaviour Guard settings saved successfully");
        } catch (Exception e) {
            log.error("Error saving behaviour guard settings", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to save settings: " + e.getMessage());
        }
    }

    private void testGuard() {
        try {
            BehaviourGuardConfig config = BehaviourGuardConfig.builder()
                    .guardEnabled(enableBehaviourGuardCheckbox.isSelected())
                    .drawdownProtectionEnabled(enableDrawdownProtectionCheckbox.isSelected())
                    .maxDrawdownPercent(maxDrawdownSpinner.getValue())
                    .equityGuardEnabled(enableEquityGuardCheckbox.isSelected())
                    .minEquityThreshold(minEquityThresholdSpinner.getValue())
                    .winStreakLimitEnabled(enableWinStreakLimitCheckbox.isSelected())
                    .maxConsecutiveWins(maxConsecutiveWinsSpinner.getValue())
                    .lossStreakLimitEnabled(enableLossStreakLimitCheckbox.isSelected())
                    .maxConsecutiveLosses(maxConsecutiveLossesSpinner.getValue())
                    .tradingHoursEnabled(enableTradingHoursCheckbox.isSelected())
                    .tradingStartTime(tradingStartTimeCombo.getValue())
                    .tradingEndTime(tradingEndTimeCombo.getValue())
                    .volatilityFilterEnabled(enableVolatilityFilterCheckbox.isSelected())
                    .maxVolatilityPercent(maxVolatilityThresholdSpinner.getValue())
                    .volatilitySource(volatilitySourceCombo.getValue())
                    .notes(behaviorNotesArea.getText())
                    .build();

            BehaviourGuardService service = BehaviourGuardService.getInstance();
            BehaviourGuardValidation validation = service.validate(config);

            StringBuilder result = new StringBuilder();
            result.append("Behaviour Guard Configuration Test Results:\n\n");

            if (validation.isValid()) {
                result.append("✓ Configuration is VALID\n\n");
                result.append("Guard Settings:\n");
                result.append(
                        String.format("  - Master Guard: %s\n", config.getGuardEnabled() ? "ENABLED" : "DISABLED"));
                result.append(String.format("  - Drawdown Protection: %s (%.1f%%)\n",
                        config.getDrawdownProtectionEnabled() ? "ENABLED" : "DISABLED",
                        config.getMaxDrawdownPercent()));
                result.append(String.format("  - Equity Guard: %s ($%.2f)\n",
                        config.getEquityGuardEnabled() ? "ENABLED" : "DISABLED",
                        config.getMinEquityThreshold()));
                result.append(String.format("  - Win Streak Limit: %s (%d wins)\n",
                        config.getWinStreakLimitEnabled() ? "ENABLED" : "DISABLED",
                        config.getMaxConsecutiveWins()));
                result.append(String.format("  - Loss Streak Limit: %s (%d losses)\n",
                        config.getLossStreakLimitEnabled() ? "ENABLED" : "DISABLED",
                        config.getMaxConsecutiveLosses()));
                result.append(String.format("  - Trading Hours: %s (%s - %s UTC)\n",
                        config.getTradingHoursEnabled() ? "ENABLED" : "DISABLED",
                        config.getTradingStartTime(),
                        config.getTradingEndTime()));
                result.append(String.format("  - Volatility Filter: %s (%.1f%% via %s)\n",
                        config.getVolatilityFilterEnabled() ? "ENABLED" : "DISABLED",
                        config.getMaxVolatilityPercent(),
                        config.getVolatilitySource()));

                if (config.getTradingHoursEnabled()) {
                    result.append(String.format("  - Currently in trading hours: %s\n",
                            config.isInTradingHours() ? "YES" : "NO"));
                }
            } else {
                result.append("✗ Configuration has ERRORS:\n");
                for (String error : validation.getErrors()) {
                    result.append(String.format("  • %s\n", error));
                }
            }

            if (!validation.getWarnings().isEmpty()) {
                result.append("\nWarnings:\n");
                for (String warning : validation.getWarnings()) {
                    result.append(String.format("  ⚠ %s\n", warning));
                }
            }

            log.info("Guard test results:\n{}", result);
            showAlert(Alert.AlertType.INFORMATION, "Guard Test Results", result.toString());
        } catch (Exception e) {
            log.error("Error testing behaviour guard", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to test guard settings: " + e.getMessage());
        }
    }

    private void resetGuardSettings() {
        enableBehaviourGuardCheckbox.setSelected(true);
        enableDrawdownProtectionCheckbox.setSelected(true);
        enableEquityGuardCheckbox.setSelected(true);
        enableWinStreakLimitCheckbox.setSelected(true);
        enableLossStreakLimitCheckbox.setSelected(true);
        enableTradingHoursCheckbox.setSelected(false);
        enableVolatilityFilterCheckbox.setSelected(true);

        maxDrawdownSpinner.getValueFactory().setValue(5.0);
        maxConsecutiveWinsSpinner.getValueFactory().setValue(10);
        maxConsecutiveLossesSpinner.getValueFactory().setValue(5);
        maxVolatilityThresholdSpinner.getValueFactory().setValue(50.0);
        minEquityThresholdSpinner.getValueFactory().setValue(1000.0);

        tradingStartTimeCombo.setValue("00:00");
        tradingEndTimeCombo.setValue("23:59");
        volatilitySourceCombo.setValue("ATR");
        behaviorNotesArea.clear();

        log.info("Behaviour Guard settings reset to defaults");
    }

    public void loadGuardSettings() {
        try {
            BehaviourGuardService service = BehaviourGuardService.getInstance();
            BehaviourGuardConfig config = service.loadConfig();

            // Populate UI from configuration
            enableBehaviourGuardCheckbox.setSelected(config.getGuardEnabled());
            enableDrawdownProtectionCheckbox.setSelected(config.getDrawdownProtectionEnabled());
            maxDrawdownSpinner.getValueFactory().setValue(config.getMaxDrawdownPercent());
            enableEquityGuardCheckbox.setSelected(config.getEquityGuardEnabled());
            minEquityThresholdSpinner.getValueFactory().setValue(config.getMinEquityThreshold());
            enableWinStreakLimitCheckbox.setSelected(config.getWinStreakLimitEnabled());
            maxConsecutiveWinsSpinner.getValueFactory().setValue(config.getMaxConsecutiveWins());
            enableLossStreakLimitCheckbox.setSelected(config.getLossStreakLimitEnabled());
            maxConsecutiveLossesSpinner.getValueFactory().setValue(config.getMaxConsecutiveLosses());
            enableTradingHoursCheckbox.setSelected(config.getTradingHoursEnabled());
            tradingStartTimeCombo.setValue(config.getTradingStartTime());
            tradingEndTimeCombo.setValue(config.getTradingEndTime());
            enableVolatilityFilterCheckbox.setSelected(config.getVolatilityFilterEnabled());
            maxVolatilityThresholdSpinner.getValueFactory().setValue(config.getMaxVolatilityPercent());
            volatilitySourceCombo.setValue(config.getVolatilitySource());
            behaviorNotesArea.setText(config.getNotes() != null ? config.getNotes() : "");

            log.info("Loaded behaviour guard settings: {}", config);
        } catch (Exception e) {
            log.warn("Failed to load behaviour guard settings, using defaults", e);
            resetGuardSettings();
        }
    }

    private java.util.List<String> generateTimeOptions() {
        java.util.List<String> times = new java.util.ArrayList<>();
        for (int h = 0; h < 24; h++) {
            for (int m = 0; m < 60; m += 15) {
                times.add(String.format("%02d:%02d", h, m));
            }
        }
        return times;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.getDialogPane().setStyle(
                "-fx-background-color: #1a1a2e; -fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        alert.showAndWait();
    }
}
