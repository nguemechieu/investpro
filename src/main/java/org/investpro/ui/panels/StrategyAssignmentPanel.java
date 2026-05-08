package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.repository.StrategyAssignmentRepository;
import org.investpro.timeframe.Timeframe;

/**
 * Strategy Assignment Panel - Assign trading strategies to symbols and manage
 * parameters
 */
@Slf4j
@Getter
@Setter
public class StrategyAssignmentPanel extends VBox {

        private ComboBox<TradePair> symbolCombo;
        private ComboBox<String> strategyCombo;
        private ComboBox<Timeframe> timeframeCombo;
        private CheckBox enableStrategyCheckbox;
        private CheckBox useRiskManagementCheckbox;

        private Spinner<Double> confidenceThresholdSpinner;
        private Spinner<Integer> minSignalStrengthSpinner;
        private Spinner<Double> positionSizePercentSpinner;
        private Spinner<Integer> maxTradesPerDaySpinner;

        private ComboBox<String> entrySignalCombo;
        private ComboBox<String> exitSignalCombo;
        private ComboBox<String> stopLossTypeCombo;

        private Spinner<Double> stopLossPercentSpinner;
        private Spinner<Double> takeProfitPercentSpinner;

        private TextArea strategyNotesArea;
        private SystemCore systemCore;

        public StrategyAssignmentPanel(SystemCore systemCore) {
                this.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 16;");
                this.setSpacing(12);
                this.systemCore=systemCore;

                // Title
                Label titleLabel = new Label("Strategy Assignment");
                titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

                // Symbol & Strategy Selection Section
                VBox selectionSection = createSelectionSection();

                // Strategy Configuration Section
                VBox configSection = createConfigurationSection();

                // Entry/Exit Rules Section
                VBox rulesSection = createEntryExitRulesSection();

                // Risk Parameters Section
                VBox riskSection = createRiskParametersSection();

                // Strategy Notes Section
                VBox notesSection = createStrategyNotesSection();

                // Buttons
                HBox buttonBox = createButtonBox();

                // Add all sections to main panel
                ScrollPane scrollPane = new ScrollPane();
                VBox contentBox = new VBox(12);
                contentBox.setPadding(new Insets(12));
                contentBox.getChildren().addAll(
                                selectionSection,
                                new Separator(),
                                configSection,
                                new Separator(),
                                rulesSection,
                                new Separator(),
                                riskSection,
                                new Separator(),
                                notesSection);
                scrollPane.setContent(contentBox);
                scrollPane.setStyle("-fx-control-inner-background: #1a1a2e;");

                this.getChildren().addAll(titleLabel, scrollPane, buttonBox);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
        }

        private VBox createSelectionSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label sectionLabel = new Label("Symbol & Strategy Selection");
                sectionLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                GridPane grid = new GridPane();
                grid.setHgap(12);
                grid.setVgap(12);

                // Symbol Selection
                Label symbolLabel = new Label("Trading Symbol:");
                symbolLabel.setStyle("-fx-text-fill: #a0aec0;");
                symbolCombo = new ComboBox<>();
                symbolCombo.getItems().addAll(systemCore.getExchange().getTradePairSymbol());
                symbolCombo.setPrefWidth(250);
                symbolCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(symbolLabel, 0, 0);
                grid.add(symbolCombo, 1, 0);

                // Strategy Selection
                Label strategyLabel = new Label("Strategy Type:");
                strategyLabel.setStyle("-fx-text-fill: #a0aec0;");
                strategyCombo = new ComboBox<>();
                strategyCombo.getItems().addAll(
                                "Moving Average", "RSI Oscillator", "MACD", "Bollinger Bands",
                                "Stochastic", "Ichimoku", "EMA Crossover", "Support/Resistance");
                strategyCombo.setPrefWidth(250);
                strategyCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(strategyLabel, 0, 1);
                grid.add(strategyCombo, 1, 1);

                // Timeframe Selection
                Label timeframeLabel = new Label("Timeframe:");
                timeframeLabel.setStyle("-fx-text-fill: #a0aec0;");
                timeframeCombo = new ComboBox<>();
                timeframeCombo.getItems().addAll(systemCore.getExchange().getSupportedTimeframes());
                timeframeCombo.setPrefWidth(250);
                timeframeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(timeframeLabel, 0, 2);
                grid.add(timeframeCombo, 1, 2);

                // Enable Strategy Checkbox
                enableStrategyCheckbox = new CheckBox("Enable Strategy on This Symbol");
                enableStrategyCheckbox.setStyle("-fx-text-fill: #a0aec0;");
                enableStrategyCheckbox.setSelected(true);
                grid.add(enableStrategyCheckbox, 0, 3, 2, 1);

                section.getChildren().addAll(sectionLabel, grid);
                return section;
        }

        private VBox createConfigurationSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label sectionLabel = new Label("Strategy Configuration");
                sectionLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                GridPane grid = new GridPane();
                grid.setHgap(12);
                grid.setVgap(12);

                // Confidence Threshold
                Label confidenceLabel = new Label("Confidence Threshold (%):");
                confidenceLabel.setStyle("-fx-text-fill: #a0aec0;");
                confidenceThresholdSpinner = new Spinner<>(0.0, 100.0, 65.0, 5.0);
                confidenceThresholdSpinner.setPrefWidth(150);
                confidenceThresholdSpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(confidenceLabel, 0, 0);
                grid.add(confidenceThresholdSpinner, 1, 0);

                // Minimum Signal Strength
                Label signalLabel = new Label("Min Signal Strength (0-10):");
                signalLabel.setStyle("-fx-text-fill: #a0aec0;");
                minSignalStrengthSpinner = new Spinner<>(0, 10, 6, 1);
                minSignalStrengthSpinner.setPrefWidth(150);
                minSignalStrengthSpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(signalLabel, 0, 1);
                grid.add(minSignalStrengthSpinner, 1, 1);

                // Position Size
                Label positionLabel = new Label("Position Size (%):");
                positionLabel.setStyle("-fx-text-fill: #a0aec0;");
                positionSizePercentSpinner = new Spinner<>(0.5, 100.0, 5.0, 0.5);
                positionSizePercentSpinner.setPrefWidth(150);
                positionSizePercentSpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(positionLabel, 0, 2);
                grid.add(positionSizePercentSpinner, 1, 2);

                // Max Trades Per Day
                Label tradesLabel = new Label("Max Trades Per Day:");
                tradesLabel.setStyle("-fx-text-fill: #a0aec0;");
                maxTradesPerDaySpinner = new Spinner<>(1, 100, 10, 1);
                maxTradesPerDaySpinner.setPrefWidth(150);
                maxTradesPerDaySpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(tradesLabel, 0, 3);
                grid.add(maxTradesPerDaySpinner, 1, 3);

                // Risk Management Checkbox
                useRiskManagementCheckbox = new CheckBox("Use Risk Management Rules");
                useRiskManagementCheckbox.setStyle("-fx-text-fill: #a0aec0;");
                useRiskManagementCheckbox.setSelected(true);
                grid.add(useRiskManagementCheckbox, 0, 4, 2, 1);

                section.getChildren().addAll(sectionLabel, grid);
                return section;
        }

        private VBox createEntryExitRulesSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label sectionLabel = new Label("Entry / Exit Rules");
                sectionLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                GridPane grid = new GridPane();
                grid.setHgap(12);
                grid.setVgap(12);

                // Entry Signal
                Label entryLabel = new Label("Entry Signal:");
                entryLabel.setStyle("-fx-text-fill: #a0aec0;");
                entrySignalCombo = new ComboBox<>();
                entrySignalCombo.getItems().addAll(
                                "Golden Cross", "Price Above MA", "RSI Oversold", "MACD Bullish",
                                "Breakout Above Resistance", "Custom Signal 1", "Custom Signal 2");
                entrySignalCombo.setPrefWidth(250);
                entrySignalCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(entryLabel, 0, 0);
                grid.add(entrySignalCombo, 1, 0);

                // Exit Signal
                Label exitLabel = new Label("Exit Signal:");
                exitLabel.setStyle("-fx-text-fill: #a0aec0;");
                exitSignalCombo = new ComboBox<>();
                exitSignalCombo.getItems().addAll(
                                "Death Cross", "Price Below MA", "RSI Overbought", "MACD Bearish",
                                "Breakout Below Support", "Target Profit Reached", "Stop Loss Hit");
                exitSignalCombo.setPrefWidth(250);
                exitSignalCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(exitLabel, 0, 1);
                grid.add(exitSignalCombo, 1, 1);

                // Stop Loss Type
                Label stoplossLabel = new Label("Stop Loss Type:");
                stoplossLabel.setStyle("-fx-text-fill: #a0aec0;");
                stopLossTypeCombo = new ComboBox<>();
                stopLossTypeCombo.getItems().addAll(
                                "Percentage", "Fixed Price", "ATR Multiple", "Support Level");
                stopLossTypeCombo.setPrefWidth(250);
                stopLossTypeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(stoplossLabel, 0, 2);
                grid.add(stopLossTypeCombo, 1, 2);

                section.getChildren().addAll(sectionLabel, grid);
                return section;
        }

        private VBox createRiskParametersSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label sectionLabel = new Label("Risk Parameters");
                sectionLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                GridPane grid = new GridPane();
                grid.setHgap(12);
                grid.setVgap(12);

                // Stop Loss Percentage
                Label stoplossPercLabel = new Label("Stop Loss (%):");
                stoplossPercLabel.setStyle("-fx-text-fill: #a0aec0;");
                stopLossPercentSpinner = new Spinner<>(0.1, 50.0, 2.0, 0.1);
                stopLossPercentSpinner.setPrefWidth(150);
                stopLossPercentSpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(stoplossPercLabel, 0, 0);
                grid.add(stopLossPercentSpinner, 1, 0);

                // Take Profit Percentage
                Label tpLabel = new Label("Take Profit (%):");
                tpLabel.setStyle("-fx-text-fill: #a0aec0;");
                takeProfitPercentSpinner = new Spinner<>(0.1, 100.0, 5.0, 0.1);
                takeProfitPercentSpinner.setPrefWidth(150);
                takeProfitPercentSpinner.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(tpLabel, 0, 1);
                grid.add(takeProfitPercentSpinner, 1, 1);

                section.getChildren().addAll(sectionLabel, grid);
                return section;
        }

        private VBox createStrategyNotesSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label notesLabel = new Label("Strategy Notes & Parameters:");
                notesLabel.setStyle("-fx-text-fill: #a0aec0;");

                strategyNotesArea = new TextArea();
                strategyNotesArea.setWrapText(true);
                strategyNotesArea.setPrefHeight(100);
                strategyNotesArea
                                .setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-font-family: monospace;");
                strategyNotesArea.setPromptText(
                                "Add custom strategy notes, parameter details, or special conditions...");

                VBox.setVgrow(strategyNotesArea, Priority.ALWAYS);
                section.getChildren().addAll(notesLabel, strategyNotesArea);
                return section;
        }

        private HBox createButtonBox() {
                HBox buttonBox = new HBox(12);
                buttonBox.setStyle("-fx-padding: 12; -fx-alignment: CENTER_RIGHT;");

                Button assignButton = new Button("Assign Strategy");
                assignButton.setStyle(
                                "-fx-padding: 8 16; -fx-font-size: 11; -fx-background-color: #10b981; -fx-text-fill: #ffffff;");
                assignButton.setOnAction(e -> assignStrategy());

                Button testButton = new Button("Test Strategy");
                testButton.setStyle(
                                "-fx-padding: 8 16; -fx-font-size: 11; -fx-background-color: #3b82f6; -fx-text-fill: #ffffff;");
                testButton.setOnAction(e -> testStrategy());

                Button resetButton = new Button("Reset");
                resetButton.setStyle(
                                "-fx-padding: 8 16; -fx-font-size: 11; -fx-background-color: #374151; -fx-text-fill: #a0aec0;");
                resetButton.setOnAction(e -> resetAssignment());

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                buttonBox.getChildren().addAll(spacer, testButton, assignButton, resetButton);
                return buttonBox;
        }

        private void assignStrategy() {
                String symbol = symbolCombo.getValue().toString('/');
                String strategy = strategyCombo.getValue();
                String timeframeCode = timeframeCombo.getValue().getCode();
                Double confidence = confidenceThresholdSpinner.getValue();
                Integer signalStrength = minSignalStrengthSpinner.getValue();
                Double positionSize = positionSizePercentSpinner.getValue();
                Integer maxTrades = maxTradesPerDaySpinner.getValue();
                String entrySignal = entrySignalCombo.getValue();
                String exitSignal = exitSignalCombo.getValue();
                Double stopLoss = stopLossPercentSpinner.getValue();
                Double takeProfit = takeProfitPercentSpinner.getValue();
                boolean enabled = enableStrategyCheckbox.isSelected();
                Boolean useRisk = useRiskManagementCheckbox.isSelected();

                // Validate inputs
                if (symbol == null || symbol.isBlank()) {
                        showError("Validation Error", "Please select a trading symbol");
                        return;
                }
                if (strategy == null || strategy.isBlank()) {
                        showError("Validation Error", "Please select a strategy");
                        return;
                }
                if (timeframeCode == null || timeframeCode.isBlank()) {
                        showError("Validation Error", "Please select a timeframe");
                        return;
                }

                try {
                        // Convert timeframe code to Timeframe enum
                        Timeframe timeframe = Timeframe.fromCode(timeframeCode);

                        // Create strategy assignment
                        String reason = String.format(
                                        "Manual assignment: %s on %s (%s) with %s entry and %s exit. Stop: %.1f%%, TP: %.1f%%, Position: %.1f%%, Max Trades: %d",
                                        strategy, symbol, timeframeCode, entrySignal, exitSignal, stopLoss, takeProfit,
                                        positionSize, maxTrades);

                        StrategyAssignment assignment = StrategyAssignment.builder()
                                        .symbol(symbol)
                                        .timeframe(timeframe)
                                        .strategyId(strategy) // Use strategy name as ID (can be mapped later if needed)
                                        .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                                        .assignedBy(StrategyAssignment.AssignedBy.USER)
                                        .scoreAtAssignment(confidence / 100.0) // Convert percentage to decimal
                                        .active(enabled)
                                        .reason(reason)
                                        .locked(false)
                                        .build();

                        // Save to repository
                        StrategyAssignmentRepository repository = StrategyAssignmentRepository.getInstance();
                        repository.save(assignment);

                        log.info(
                                        "Strategy Assigned Successfully: id={}, symbol={}, strategy={}, timeframe={}, confidence={}, signal={}, position={}, maxTrades={}, entry={}, exit={}, stopLoss={}, takeProfit={}, enabled={}, useRisk={}",
                                        assignment.getAssignmentId(), symbol, strategy, timeframeCode, confidence,
                                        signalStrength, positionSize, maxTrades, entrySignal,
                                        exitSignal, stopLoss, takeProfit, enabled, useRisk);

                        showInfo("Success", String.format(
                                        "Strategy '%s' assigned to %s (%s) successfully!\nAssignment ID: %s", strategy,
                                        symbol, timeframeCode, assignment.getAssignmentId()));
                } catch (IllegalArgumentException e) {
                        log.error("Failed to assign strategy: {}", e.getMessage());
                        showError("Error", "Failed to assign strategy: " + e.getMessage());
                }
        }

        private void testStrategy() {
                String symbol = symbolCombo.getValue().toString('/');
                String strategy = strategyCombo.getValue();
                log.info("Testing strategy: {} for symbol: {}", strategy, symbol);
                showInfo("Test", "Testing " + strategy + " strategy on " + symbol + "...");
        }

        private void resetAssignment() {
                symbolCombo.setValue(null);
                strategyCombo.setValue(null);
                timeframeCombo.setValue(null);
                confidenceThresholdSpinner.getValueFactory().setValue(65.0);
                minSignalStrengthSpinner.getValueFactory().setValue(6);
                positionSizePercentSpinner.getValueFactory().setValue(5.0);
                maxTradesPerDaySpinner.getValueFactory().setValue(10);
                stopLossPercentSpinner.getValueFactory().setValue(2.0);
                takeProfitPercentSpinner.getValueFactory().setValue(5.0);
                entrySignalCombo.setValue(null);
                exitSignalCombo.setValue(null);
                stopLossTypeCombo.setValue(null);
                enableStrategyCheckbox.setSelected(true);
                useRiskManagementCheckbox.setSelected(true);
                strategyNotesArea.clear();
                log.info("Strategy assignment panel reset to defaults");
        }

        private void showInfo(String title, String message) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
        }

        private void showError(String title, String message) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
        }
}
