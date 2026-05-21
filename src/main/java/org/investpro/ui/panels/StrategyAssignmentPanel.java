package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.lab.StrategyLabService;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Strategy Assignment Panel - Assign trading strategies to symbols and manage
 * parameters
 */
@Slf4j
@Getter
@Setter
public class StrategyAssignmentPanel extends VBox{

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
        private Label currentAssignmentLabel;
        private Label statusLabel;
        private SystemCore systemCore;
        private  StrategyAssignment strategyAssignment;
        private final StrategyAssignmentRepository repository = StrategyAssignmentRepository.getInstance();
        private final StrategyLabService labService = StrategyLabService.getInstance();

        public StrategyAssignmentPanel(@NotNull SystemCore systemCore) throws SQLException, ClassNotFoundException {
                this.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 16;");

                this.systemCore=systemCore;
                this.strategyAssignment=systemCore.getStrategyAssignment();

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

                // Current canonical assignment section
                VBox currentAssignmentSection = createCurrentAssignmentSection();

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
                                notesSection,
                                new Separator(),
                                currentAssignmentSection);
                scrollPane.setContent(contentBox);
                scrollPane.setStyle("-fx-control-inner-background: #1a1a2e;");

                this.getChildren().addAll(titleLabel, scrollPane, buttonBox);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                initializeDefaults();
                refreshCurrentAssignmentLabel();
                LocalizationService.applyTranslations(this);
        }

        private void initializeDefaults() {
                confidenceThresholdSpinner.getValueFactory().setValue(65.0);
                minSignalStrengthSpinner.getValueFactory().setValue(6);
                positionSizePercentSpinner.getValueFactory().setValue(5.0);
                maxTradesPerDaySpinner.getValueFactory().setValue(10);
                stopLossPercentSpinner.getValueFactory().setValue(2.0);
                takeProfitPercentSpinner.getValueFactory().setValue(5.0);

                if (!entrySignalCombo.getItems().isEmpty()) {
                        entrySignalCombo.setValue(entrySignalCombo.getItems().get(0));
                }
                if (!exitSignalCombo.getItems().isEmpty()) {
                        exitSignalCombo.setValue(exitSignalCombo.getItems().get(0));
                }
                if (!stopLossTypeCombo.getItems().isEmpty()) {
                        stopLossTypeCombo.setValue(stopLossTypeCombo.getItems().get(0));
                }
        }

        private @NotNull VBox createSelectionSection() throws SQLException, ClassNotFoundException {
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
                if (systemCore.getExchange() != null) {
                        symbolCombo.getItems().addAll(systemCore.getExchange().getTradePairSymbol());
                }
                symbolCombo.setPrefWidth(250);
                symbolCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(symbolLabel, 0, 0);
                grid.add(symbolCombo, 1, 0);

                // Strategy Selection
                Label strategyLabel = new Label("Strategy Type:");
                strategyLabel.setStyle("-fx-text-fill: #a0aec0;");
                strategyCombo = new ComboBox<>();
                strategyCombo.getItems().addAll(StrategyCatalog.availableStrategyNames());
                strategyCombo.setPrefWidth(250);
                strategyCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
                grid.add(strategyLabel, 0, 1);
                grid.add(strategyCombo, 1, 1);

                // Timeframe Selection
                Label timeframeLabel = new Label("Timeframe:");
                timeframeLabel.setStyle("-fx-text-fill: #a0aec0;");
                timeframeCombo = new ComboBox<>();
                if (systemCore.getExchange() != null) {
                        timeframeCombo.getItems().addAll(systemCore.getExchange().getSupportedTimeframes());
                }
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

        private VBox createCurrentAssignmentSection() {
                VBox section = new VBox(8);
                section.setStyle(
                                "-fx-border-color: #374151; -fx-border-radius: 4; -fx-padding: 12; -fx-background-color: #16213e;");

                Label title = new Label("Current System Assignment");
                title.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                currentAssignmentLabel = new Label("Select a symbol and timeframe to inspect the active assignment.");
                currentAssignmentLabel.setWrapText(true);
                currentAssignmentLabel.setStyle("-fx-text-fill: #a0aec0;");

                statusLabel = new Label("Ready");
                statusLabel.setWrapText(true);
                statusLabel.setStyle("-fx-text-fill: #94a3b8;");

                symbolCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshCurrentAssignmentLabel());
                timeframeCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshCurrentAssignmentLabel());

                section.getChildren().addAll(title, currentAssignmentLabel, statusLabel);
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

                Button loadButton = new Button("Load Existing");
                loadButton.setStyle(
                                "-fx-padding: 8 16; -fx-font-size: 11; -fx-background-color: #6366f1; -fx-text-fill: #ffffff;");
                loadButton.setOnAction(e -> loadExistingAssignment());

                Button resetButton = new Button("Reset");
                resetButton.setStyle(
                                "-fx-padding: 8 16; -fx-font-size: 11; -fx-background-color: #374151; -fx-text-fill: #a0aec0;");
                resetButton.setOnAction(e -> resetAssignment());

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                buttonBox.getChildren().addAll(spacer, loadButton, testButton, assignButton, resetButton);
                return buttonBox;
        }

        private void assignStrategy() {
                TradePair selectedPair = symbolCombo.getValue();
                String strategy = strategyCombo.getValue();
                Timeframe selectedTimeframe = timeframeCombo.getValue();
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
                String notes = strategyNotesArea.getText() == null ? "" : strategyNotesArea.getText().trim();

                // Validate inputs
                if (selectedPair == null) {
                        showError("Validation Error", "Please select a trading symbol");
                        return;
                }
                if (strategy == null || strategy.isBlank()) {
                        showError("Validation Error", "Please select a strategy");
                        return;
                }
                if (selectedTimeframe == null) {
                        showError("Validation Error", "Please select a timeframe");
                        return;
                }
                if (entrySignal == null || entrySignal.isBlank()) {
                        showError("Validation Error", "Please select an entry signal");
                        return;
                }
                if (exitSignal == null || exitSignal.isBlank()) {
                        showError("Validation Error", "Please select an exit signal");
                        return;
                }

                String symbol = selectedPair.toString('/');
                String timeframeCode = selectedTimeframe.getCode();

                try {
                        // Create strategy assignment
                        String reason = String.format(
                                        "Manual assignment: %s on %s (%s) with %s entry and %s exit. Stop: %.1f%%, TP: %.1f%%, Position: %.1f%%, Max Trades: %d. Notes: %s",
                                        strategy, symbol, timeframeCode, entrySignal, exitSignal, stopLoss, takeProfit,
                                        positionSize, maxTrades, notes.isBlank() ? "n/a" : notes);

                        StrategyAssignment assignmentToPersist = StrategyAssignment.builder()
                                        .symbol(symbol)
                                        .timeframe(selectedTimeframe)
                                        .strategyId(strategy) // Use strategy name as ID (can be mapped later if needed)
                                        .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                                        .assignedBy(StrategyAssignment.AssignedBy.USER)
                                        .scoreAtAssignment(confidence)
                                        .active(enabled)
                                        .reason(reason)
                                        .locked(true)
                                        .build();

                        // Save to repository
                        repository.save(assignmentToPersist);
                        this.strategyAssignment = assignmentToPersist;
                        refreshCurrentAssignmentLabel();

                        log.info(
                                        "Strategy Assigned Successfully: id={}, symbol={}, strategy={}, timeframe={}, confidence={}, signal={}, position={}, maxTrades={}, entry={}, exit={}, stopLoss={}, takeProfit={}, enabled={}, useRisk={}",
                                        assignmentToPersist.getAssignmentId(), symbol, strategy, timeframeCode, confidence,
                                        signalStrength, positionSize, maxTrades, entrySignal,
                                        exitSignal, stopLoss, takeProfit, enabled, useRisk);

                        showInfo("Success", String.format(
                                        "Strategy '%s' assigned to %s (%s) successfully!\nAssignment ID: %s", strategy,
                                        symbol, timeframeCode, assignmentToPersist.getAssignmentId()));
                } catch (IllegalArgumentException e) {
                        log.error("Failed to assign strategy: {}", e.getMessage());
                        showError("Error", "Failed to assign strategy: " + e.getMessage());
                }
        }

        private void testStrategy() {
                TradePair selectedPair = symbolCombo.getValue();
                String strategy = strategyCombo.getValue();
                Timeframe selectedTimeframe = timeframeCombo.getValue();

                if (selectedPair == null || strategy == null || strategy.isBlank() || selectedTimeframe == null) {
                        showError("Validation Error",
                                        "Please select symbol, strategy, and timeframe before testing.");
                        return;
                }

                String symbol = selectedPair.toString('/');
                statusLabel.setText("Running real-candle backtest and assignment check...");

                fetchHistoricalCandles(selectedPair, selectedTimeframe)
                                .thenCompose(candles -> labService.evaluateAndAssignBest(
                                                symbol,
                                                selectedTimeframe,
                                                candles,
                                                List.of(strategy)))
                                .thenAccept(assignment -> Platform.runLater(() -> {
                                        if (assignment == null) {
                                                statusLabel.setText("No assignment created. Strategy failed minimum score, data, or auto-assign checks.");
                                                refreshCurrentAssignmentLabel();
                                                return;
                                        }

                                        this.strategyAssignment = assignment;
                                        strategyCombo.setValue(assignment.getStrategyId());
                                        confidenceThresholdSpinner.getValueFactory()
                                                        .setValue(displayScore(assignment.getScoreAtAssignment()));
                                        statusLabel.setText("Assigned by real-candle evaluation: "
                                                        + assignment.getDisplayName());
                                        refreshCurrentAssignmentLabel();
                                        showInfo("Strategy Evaluation",
                                                        "Assigned " + assignment.getStrategyId() + " to " + symbol
                                                                        + " (" + selectedTimeframe.getCode() + ").");
                                }))
                                .exceptionally(exception -> {
                                        Platform.runLater(() -> {
                                                String reason = rootMessage(exception);
                                                statusLabel.setText("Evaluation failed: " + reason);
                                                showError("Strategy Evaluation", reason);
                                        });
                                        return null;
                                });
        }

        private void loadExistingAssignment() {
                TradePair selectedPair = symbolCombo.getValue();
                Timeframe selectedTimeframe = timeframeCombo.getValue();

                if (selectedPair == null || selectedTimeframe == null) {
                        showError("Validation Error", "Select symbol and timeframe to load an assignment.");
                        return;
                }

                String symbol = selectedPair.toString('/');
                StrategyAssignment existing = repository.getAssignment(symbol, selectedTimeframe);

                if (existing == null) {
                        showInfo("No Assignment",
                                        "No active assignment found for " + symbol + " (" + selectedTimeframe.getCode()
                                                        + ").");
                        return;
                }

                this.strategyAssignment = existing;
                strategyCombo.setValue(existing.getStrategyId());
                enableStrategyCheckbox.setSelected(existing.isActive());
                confidenceThresholdSpinner.getValueFactory().setValue(displayScore(existing.getScoreAtAssignment()));
                strategyNotesArea.setText(existing.getReason() == null ? "" : existing.getReason());
                refreshCurrentAssignmentLabel();

                showInfo("Loaded", "Loaded assignment: " + existing.getDisplayName());
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
                if (!entrySignalCombo.getItems().isEmpty()) {
                        entrySignalCombo.setValue(entrySignalCombo.getItems().get(0));
                } else {
                        entrySignalCombo.setValue(null);
                }
                if (!exitSignalCombo.getItems().isEmpty()) {
                        exitSignalCombo.setValue(exitSignalCombo.getItems().get(0));
                } else {
                        exitSignalCombo.setValue(null);
                }
                if (!stopLossTypeCombo.getItems().isEmpty()) {
                        stopLossTypeCombo.setValue(stopLossTypeCombo.getItems().get(0));
                } else {
                        stopLossTypeCombo.setValue(null);
                }
                enableStrategyCheckbox.setSelected(true);
                useRiskManagementCheckbox.setSelected(true);
                strategyNotesArea.clear();
                strategyAssignment = null;
                refreshCurrentAssignmentLabel();
                log.info("Strategy assignment panel reset to defaults");
        }

        private CompletableFuture<List<CandleData>> fetchHistoricalCandles(TradePair pair, Timeframe timeframe) {
                if (systemCore.getExchange() == null) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Connect an exchange first."));
                }

                try {
                        CandleDataSupplier supplier = systemCore.getExchange()
                                        .getCandleDataSupplier(timeframe.getSeconds(), pair);
                        if (supplier == null) {
                                return CompletableFuture.failedFuture(new IllegalStateException(
                                                "No candle supplier for " + pair.toString('/') + " "
                                                                + timeframe.getCode()));
                        }

                        Future<List<CandleData>> future = supplier.get();
                        return CompletableFuture.supplyAsync(() -> {
                                try {
                                        List<CandleData> candles = future.get(20, TimeUnit.SECONDS);
                                        return candles == null ? List.of() : candles;
                                } catch (Exception exception) {
                                        throw new IllegalStateException("Historical candle fetch failed", exception);
                                }
                        });
                } catch (Exception exception) {
                        return CompletableFuture.failedFuture(exception);
                }
        }

        private void refreshCurrentAssignmentLabel() {
                if (currentAssignmentLabel == null) {
                        return;
                }

                TradePair pair = symbolCombo == null ? null : symbolCombo.getValue();
                Timeframe timeframe = timeframeCombo == null ? null : timeframeCombo.getValue();
                if (pair == null || timeframe == null) {
                        currentAssignmentLabel.setText("Select a symbol and timeframe to inspect the active assignment.");
                        return;
                }

                StrategyAssignment active = repository.getActive(pair.toString('/'), timeframe);
                if (active == null) {
                        currentAssignmentLabel.setText("No active canonical assignment for "
                                        + pair.toString('/') + " | " + timeframe.getCode());
                        return;
                }

                currentAssignmentLabel.setText(String.format(
                                "Active: %s | %s | score %.1f | mode %s | locked %s",
                                active.getStrategyId(),
                                active.getTimeframe().getCode(),
                                active.getScoreAtAssignment(),
                                active.getMode(),
                                active.isLocked()));
        }

        private double displayScore(double score) {
                return score > 0.0 && score <= 1.0 ? score * 100.0 : score;
        }

        private String rootMessage(Throwable throwable) {
                Throwable cursor = throwable;
                while (cursor != null && cursor.getCause() != null) {
                        cursor = cursor.getCause();
                }
                return cursor == null || cursor.getMessage() == null || cursor.getMessage().isBlank()
                                ? "Unknown error"
                                : cursor.getMessage();
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
