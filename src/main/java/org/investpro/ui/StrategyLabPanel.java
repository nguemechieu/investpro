package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.i18n.LocalizationService;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.lab.*;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * JavaFX panel for Strategy Lab.
 *
 * Displays:
 * - Strategy testing controls
 * - Active assignment information
 * - Strategy rankings
 * - Voting/consensus results
 * - Consensus summary
 * - Test status and logs
 *
 * All backtests run asynchronously without blocking UI.
 */
@Getter
@Setter
@Slf4j
public class StrategyLabPanel extends BorderPane {

    private final StrategyLabService labService;

    // State
    private String selectedSymbol = "BTC/USD";
    private Timeframe selectedTimeframe = Timeframe.H1;
    private boolean isRunning = false;

    // UI Components - Controls
    private ComboBox<String> symbolCombo;
    private ComboBox<Timeframe> timeframeCombo;
    private TextField strategyFilterField;
    private Button testSelectedButton;
    private Button testAllStrategiesButton;
    private Button testAllTimeframesButton;
    private Button rankButton;
    private Button assignBestButton;
    private Button manualAssignButton;
    private Button unassignButton;
    private Button disableAssignmentButton;

    // UI Components - Display
    private Label activeAssignmentLabel;
    private Label activeStrategyLabel;
    private Label activeScoreLabel;
    private Label activeModeLabel;
    private Label activeAssignedAtLabel;

    // Tables
    private TableView<StrategyPerformanceReport> rankingTable;
    private TableView<StrategyVote> votingTable;

    // Consensus
    private Label consensusLabel;
    private Label consensusConfidenceLabel;
    private Label selectedStrategyLabel;
    private Label buyVotesLabel;
    private Label sellVotesLabel;
    private Label holdVotesLabel;
    private Label consensusReasonLabel;

    // Status
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea logsArea;

    public StrategyLabPanel(@NotNull StrategyLabService labService) {
        this.labService = labService;
        initializeUI();
        LocalizationService.applyTranslations(this);
        refreshUI();
    }

    /**
     * Initialize all UI components.
     */
    private void initializeUI() {


        // Top: Controls
        setTop(createControlsSection());

        // Center: Tabbed content
        setCenter(createContentTabs());

        // Bottom: Status/Logs
        setBottom(createStatusSection());
    }

    /**
     * Create controls section (top).
     */
    private VBox createControlsSection() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));


        // Row 1: Symbol, Timeframe, Filter
        HBox row1 = new HBox(8);
        row1.setStyle("-fx-alignment: center-left;");

        Label symbolLabel = new Label("Symbol:");
        symbolCombo = new ComboBox<>();
        symbolCombo.getItems().addAll("BTC/USD", "ETH/USD", "EUR/USD");
        symbolCombo.setValue("BTC/USD");
        symbolCombo.setOnAction(e -> {
            selectedSymbol = symbolCombo.getValue();
            refreshUI();
        });

        Label timeframeLabel = new Label("Timeframe:");
        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().addAll(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1);
        timeframeCombo.setValue(Timeframe.H1);
        timeframeCombo.setOnAction(e -> {
            selectedTimeframe = timeframeCombo.getValue();
            refreshUI();
        });

        Label filterLabel = new Label("Strategy Filter:");
        strategyFilterField = new TextField();
        strategyFilterField.setPromptText("Search strategies...");
        strategyFilterField.setPrefWidth(200);

        row1.getChildren().addAll(
                symbolLabel, symbolCombo,
                timeframeLabel, timeframeCombo,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                filterLabel, strategyFilterField);

        // Row 2: Test/Rank Buttons
        HBox row2 = new HBox(8);
        row2.setStyle("-fx-alignment: center-left;");

        testSelectedButton = new Button("Test Selected");
        testSelectedButton.setStyle("-fx-padding: 8px 16px;");
        testSelectedButton.setOnAction(e -> testSelectedStrategy());

        testAllStrategiesButton = new Button("Test All Strategies");
        testAllStrategiesButton.setStyle("-fx-padding: 8px 16px;");
        testAllStrategiesButton.setOnAction(e -> testAllStrategies());

        testAllTimeframesButton = new Button("Test All Timeframes");
        testAllTimeframesButton.setStyle("-fx-padding: 8px 16px;");
        testAllTimeframesButton.setOnAction(e -> testAllTimeframes());

        rankButton = new Button("Rank");
        rankButton.setStyle("-fx-padding: 8px 16px;");
        rankButton.setOnAction(e -> refreshUI());

        row2.getChildren().addAll(
                testSelectedButton,
                testAllStrategiesButton,
                testAllTimeframesButton,
                rankButton);

        // Row 3: Assignment Buttons
        HBox row3 = new HBox(8);
        row3.setStyle("-fx-alignment: center-left;");

        assignBestButton = new Button("Assign Best");
        assignBestButton.setStyle("-fx-padding: 8px 16px; -fx-text-fill: green;");
        assignBestButton.setOnAction(e -> assignBest());

        manualAssignButton = new Button("Manual Assign");
        manualAssignButton.setStyle("-fx-padding: 8px 16px;");
        manualAssignButton.setOnAction(e -> openManualAssignDialog());

        unassignButton = new Button("Unassign");
        unassignButton.setStyle("-fx-padding: 8px 16px; -fx-text-fill: orange;");
        unassignButton.setOnAction(e -> unassign());

        disableAssignmentButton = new Button("Disable Assignment");
        disableAssignmentButton.setStyle("-fx-padding: 8px 16px; -fx-text-fill: red;");
        disableAssignmentButton.setOnAction(e -> openDisableDialog());

        row3.getChildren().addAll(
                assignBestButton,
                manualAssignButton,
                unassignButton,
                disableAssignmentButton);

        box.getChildren().addAll(row1, row2, row3);
        return box;
    }

    /**
     * Create main content tabs.
     */
    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabPane.getTabs().addAll(
                createAssignmentTab(),
                createRankingTab(),
                createVotingTab(),
                createConsensusTab());

        return tabPane;
    }

    /**
     * Create "Active Assignment" tab.
     */
    private Tab createAssignmentTab() {
        Tab tab = new Tab("Active Assignment");

        VBox box = new VBox(8);
        box.setPadding(new Insets(15));

        // Card: Assignment Info
        VBox card = createCard(
                "Current Strategy Assignment",
                createAssignmentInfoContent());

        box.getChildren().add(card);
        tab.setContent(new ScrollPane(box));

        return tab;
    }

    /**
     * Create assignment info content.
     */
    private VBox createAssignmentInfoContent() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #f0f0f0; -fx-border-radius: 4;");

        activeAssignmentLabel = new Label("No assignment");
        activeAssignmentLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        activeStrategyLabel = new Label("Strategy: -");
        activeScoreLabel = new Label("Score: -");
        activeModeLabel = new Label("Mode: -");
        activeAssignedAtLabel = new Label("Assigned: -");

        box.getChildren().addAll(
                activeAssignmentLabel,
                new Separator(),
                activeStrategyLabel,
                activeScoreLabel,
                activeModeLabel,
                activeAssignedAtLabel);

        return box;
    }

    /**
     * Create "Rankings" tab.
     */
    private Tab createRankingTab() {
        Tab tab = new Tab("Strategy Rankings");

        rankingTable = new TableView<>();
        rankingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Columns
        TableColumn<StrategyPerformanceReport, Integer> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(cellData -> {
            int idx = rankingTable.getItems().indexOf(cellData.getValue()) + 1;
            return new javafx.beans.property.SimpleObjectProperty<>(idx);
        });

        TableColumn<StrategyPerformanceReport, String> nameCol = new TableColumn<>("Strategy");
        nameCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getStrategyName()));

        TableColumn<StrategyPerformanceReport, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getScore()));
        scoreCol.setCellFactory(col -> new TableCell<StrategyPerformanceReport, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.1f", item));
            }
        });

        TableColumn<StrategyPerformanceReport, Double> winRateCol = new TableColumn<>("Win Rate");
        winRateCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getWinRate()));
        winRateCol.setCellFactory(col -> new TableCell<StrategyPerformanceReport, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.1f%%", item * 100));
            }
        });

        TableColumn<StrategyPerformanceReport, Double> returnCol = new TableColumn<>("Return %");
        returnCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getTotalReturn()));
        returnCol.setCellFactory(col -> new TableCell<StrategyPerformanceReport, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.2f%%", item));
            }
        });

        TableColumn<StrategyPerformanceReport, Double> ddCol = new TableColumn<>("Max DD");
        ddCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getMaxDrawdown()));
        ddCol.setCellFactory(col -> new TableCell<StrategyPerformanceReport, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.1f%%", item * 100));
            }
        });

        TableColumn<StrategyPerformanceReport, Double> pfCol = new TableColumn<>("Profit Factor");
        pfCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getProfitFactor()));
        pfCol.setCellFactory(col -> new TableCell<StrategyPerformanceReport, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.2f", item));
            }
        });

        TableColumn<StrategyPerformanceReport, Integer> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getTotalTrades()));

        @SuppressWarnings("unchecked")
        TableColumn<StrategyPerformanceReport, Double>[] allCols = new TableColumn[] {
                rankCol, nameCol, scoreCol, winRateCol, returnCol, ddCol, pfCol, tradesCol
        };

        rankingTable.getColumns().addAll(allCols);

        tab.setContent(rankingTable);
        return tab;
    }

    /**
     * Create "Voting" tab.
     */
    private Tab createVotingTab() {
        Tab tab = new Tab("Voting Results");

        votingTable = new TableView<>();
        votingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<StrategyVote, String> stratCol = new TableColumn<>("Strategy");
        stratCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getStrategyName()));

        TableColumn<StrategyVote, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getSide().toString()));

        TableColumn<StrategyVote, Double> confCol = new TableColumn<>("Confidence");
        confCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getConfidence()));
        confCol.setCellFactory(col -> new TableCell<StrategyVote, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.2f", item));
            }
        });

        TableColumn<StrategyVote, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getScore()));
        scoreCol.setCellFactory(col -> new TableCell<StrategyVote, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.1f", item));
            }
        });

        TableColumn<StrategyVote, Double> weightCol = new TableColumn<>("Weight");
        weightCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(
                cellData.getValue().getWeight()));
        weightCol.setCellFactory(col -> new TableCell<StrategyVote, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText(null);
                else
                    setText(String.format("%.2f", item));
            }
        });

        TableColumn<StrategyVote, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getReason()));

        @SuppressWarnings("unchecked")
        TableColumn<StrategyVote, ?>[] allCols = new TableColumn[] {
                stratCol, sideCol, confCol, scoreCol, weightCol, reasonCol
        };

        votingTable.getColumns().addAll(allCols);

        tab.setContent(votingTable);
        return tab;
    }

    /**
     * Create "Consensus" tab.
     */
    private Tab createConsensusTab() {
        Tab tab = new Tab("Consensus Summary");

        VBox box = new VBox(10);
        box.setPadding(new Insets(15));

        VBox consensusCard = createCard(
                "Consensus Result",
                createConsensusContent());

        box.getChildren().add(consensusCard);
        tab.setContent(new ScrollPane(box));

        return tab;
    }

    /**
     * Create consensus content.
     */
    private VBox createConsensusContent() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #f0f0f0; -fx-border-radius: 4;");

        consensusLabel = new Label("Consensus: HOLD");
        consensusLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        consensusConfidenceLabel = new Label("Confidence: -");
        selectedStrategyLabel = new Label("Selected Strategy: -");

        HBox votesBox = new HBox(20);
        buyVotesLabel = new Label("BUY: 0 votes");
        sellVotesLabel = new Label("SELL: 0 votes");
        holdVotesLabel = new Label("HOLD: 0 votes");
        votesBox.getChildren().addAll(buyVotesLabel, sellVotesLabel, holdVotesLabel);

        consensusReasonLabel = new Label("Reason: No consensus data");
        consensusReasonLabel.setWrapText(true);

        box.getChildren().addAll(
                consensusLabel,
                new Separator(),
                consensusConfidenceLabel,
                selectedStrategyLabel,
                new Separator(),
                votesBox,
                new Separator(),
                consensusReasonLabel);

        return box;
    }

    /**
     * Create status/logs section (bottom).
     */
    private VBox createStatusSection() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        box.setPrefHeight(150);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 12;");

        logsArea = new TextArea();
        logsArea.setEditable(false);
        logsArea.setPrefHeight(100);
        logsArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10;");

        box.getChildren().addAll(
                new HBox(8, new Label("Progress:"), progressBar),
                statusLabel,
                new Label("Logs:"),
                logsArea);

        return box;
    }

    /**
     * Helper to create a card.
     */
    private VBox createCard(String title, VBox content) {
        VBox card = new VBox();
        card.setStyle("-fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-color: #fafafa;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 10;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel
                .setStyle("-fx-padding: 10; -fx-font-size: 13; -fx-font-weight: bold; -fx-background-color: #f0f0f0;");

        card.getChildren().addAll(titleLabel, content);
        return card;
    }

    /**
     * Refresh UI with current data.
     */
    private void refreshUI() {
        StrategyLabSnapshot snapshot = labService.getSnapshot(selectedSymbol, selectedTimeframe);

        // Update rankings table
        rankingTable.getItems().clear();
        rankingTable.getItems().addAll(snapshot.getRankings());

        // Update active assignment
        if (snapshot.hasAssignment()) {
            StrategyAssignment assignment = snapshot.getActiveAssignment();
            activeAssignmentLabel.setText(
                    assignment.getStrategyId() + " (" + assignment.getMode().getDisplayName() + ")");
            activeStrategyLabel.setText("Strategy: " + assignment.getStrategyId());
            activeScoreLabel.setText(String.format("Score: %.1f", assignment.getScoreAtAssignment()));
            activeModeLabel.setText("Mode: " + assignment.getMode().getDisplayName());
            activeAssignedAtLabel.setText("Assigned: " + assignment.getAssignedAt());
        } else {
            activeAssignmentLabel.setText("No assignment");
            activeStrategyLabel.setText("Strategy: -");
            activeScoreLabel.setText("Score: -");
            activeModeLabel.setText("Mode: -");
            activeAssignedAtLabel.setText("Assigned: -");
        }

        // Update consensus
        if (snapshot.hasConsensus()) {
            StrategyConsensusResult consensus = snapshot.getConsensus();
            consensusLabel.setText("Consensus: " + consensus.getConsensusSide().toString());
            consensusConfidenceLabel.setText(
                    String.format("Confidence: %.1f%%", consensus.getConsensusConfidence() * 100));
            selectedStrategyLabel.setText("Selected: " + consensus.getSelectedStrategyName());
            buyVotesLabel
                    .setText(String.format("BUY: %d votes (%.1f)", consensus.getBuyVotes(), consensus.getBuyScore()));
            sellVotesLabel.setText(
                    String.format("SELL: %d votes (%.1f)", consensus.getSellVotes(), consensus.getSellScore()));
            holdVotesLabel.setText(
                    String.format("HOLD: %d votes (%.1f)", consensus.getHoldVotes(), consensus.getHoldScore()));
            consensusReasonLabel.setText("Reason: " + consensus.getReason());

            votingTable.getItems().clear();
            votingTable.getItems().addAll(consensus.getVotes());
        } else {
            consensusLabel.setText("Consensus: No data");
            consensusConfidenceLabel.setText("Confidence: -");
            selectedStrategyLabel.setText("Selected: -");
            buyVotesLabel.setText("BUY: 0 votes");
            sellVotesLabel.setText("SELL: 0 votes");
            holdVotesLabel.setText("HOLD: 0 votes");
            consensusReasonLabel.setText("Reason: No consensus generated");
            votingTable.getItems().clear();
        }

        statusLabel.setText("Ready");
    }

    /**
     * Test selected strategy (not implemented, placeholder).
     */
    private void testSelectedStrategy() {
        appendLog("Testing selected strategy on " + selectedSymbol + "/" + selectedTimeframe.getCode() + "...");
        disableControls(true);

        labService.testStrategies(selectedSymbol, selectedTimeframe, List.of("Trend Following"))
                .thenRun(() -> Platform.runLater(() -> {
                    appendLog("Test completed");
                    refreshUI();
                    disableControls(false);
                }))
                .exceptionally(ex -> {
                    appendLog("Test failed: " + ex.getMessage());
                    disableControls(false);
                    return null;
                });
    }

    /**
     * Test all strategies.
     */
    private void testAllStrategies() {
        appendLog("Testing all strategies on " + selectedSymbol + "/" + selectedTimeframe.getCode() + "...");
        disableControls(true);

        labService.testStrategies(selectedSymbol, selectedTimeframe,
                new java.util.ArrayList<>(org.investpro.strategy.StrategyCatalog.availableStrategyNames()))
                .thenRun(() -> Platform.runLater(() -> {
                    appendLog("All tests completed");
                    refreshUI();
                    disableControls(false);
                }))
                .exceptionally(ex -> {
                    appendLog("Tests failed: " + ex.getMessage());
                    disableControls(false);
                    return null;
                });
    }

    /**
     * Test all timeframes.
     */
    private void testAllTimeframes() {
        appendLog("Testing all strategies on all timeframes for " + selectedSymbol + "...");
        disableControls(true);

        labService.testAllStrategies(selectedSymbol, List.of(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1))
                .thenRun(() -> Platform.runLater(() -> {
                    appendLog("All tests completed");
                    refreshUI();
                    disableControls(false);
                }))
                .exceptionally(ex -> {
                    appendLog("Tests failed: " + ex.getMessage());
                    disableControls(false);
                    return null;
                });
    }

    /**
     * Assign best strategy.
     */
    private void assignBest() {
        appendLog("Assigning best strategy for " + selectedSymbol + "/" + selectedTimeframe.getCode() + "...");

        StrategyAssignment assignment = labService.assignBest(selectedSymbol, selectedTimeframe);
        if (assignment != null) {
            appendLog("✓ Assigned: " + assignment.getStrategyId());
        } else {
            appendLog("✗ Failed to find suitable strategy");
        }

        refreshUI();
    }

    /**
     * Manually assign strategy.
     */
    private void openManualAssignDialog() {
        // Create dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Manual Strategy Assignment");
        dialog.setHeaderText("Select a strategy to assign");

        ComboBox<String> strategyCombo = new ComboBox<>();
        strategyCombo.getItems().addAll(
                new java.util.ArrayList<>(org.investpro.strategy.StrategyCatalog.availableStrategyNames()));

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.getChildren().addAll(
                new Label("Strategy:"),
                strategyCombo);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return strategyCombo.getValue();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(strategyName -> {
            StrategyAssignment assignment = labService.manuallyAssign(selectedSymbol, selectedTimeframe, strategyName);
            appendLog("✓ Manually assigned: " + assignment.getStrategyId());
            refreshUI();
        });
    }

    /**
     * Unassign strategy.
     */
    private void unassign() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unassign Strategy");
        alert.setHeaderText("Unassign strategy for " + selectedSymbol + "/" + selectedTimeframe.getCode() + "?");
        alert.setContentText("This symbol/timeframe will have no automatic strategy trading.");

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                labService.unassign(selectedSymbol, selectedTimeframe);
                appendLog("✓ Unassigned: " + selectedSymbol + "/" + selectedTimeframe.getCode());
                refreshUI();
            }
        });
    }

    /**
     * Disable assignment dialog.
     */
    private void openDisableDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Disable Assignment");
        dialog.setHeaderText("Disable strategy assignment");

        TextArea reasonArea = new TextArea();
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(3);
        reasonArea.setPromptText("Reason for disabling...");

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.getChildren().addAll(
                new Label("Reason:"),
                reasonArea);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return reasonArea.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(reason -> {
            labService.getAssignmentService().getActiveAssignment(selectedSymbol, selectedTimeframe)
                    .ifPresent(assignment -> {
                        labService.getAssignmentService().disableAssignment(assignment.getAssignmentId(), reason);
                        appendLog("✓ Disabled: " + assignment.getStrategyId());
                        refreshUI();
                    });
        });
    }

    /**
     * Append log message.
     */
    private void appendLog(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss"));
            logsArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    /**
     * Disable/enable controls.
     */
    private void disableControls(boolean disabled) {
        Platform.runLater(() -> {
            testSelectedButton.setDisable(disabled);
            testAllStrategiesButton.setDisable(disabled);
            testAllTimeframesButton.setDisable(disabled);
            rankButton.setDisable(disabled);
            assignBestButton.setDisable(disabled);
            manualAssignButton.setDisable(disabled);
            unassignButton.setDisable(disabled);
            disableAssignmentButton.setDisable(disabled);

            if (disabled) {
                statusLabel.setText("Testing in progress...");
                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            } else {
                statusLabel.setText("Ready");
                progressBar.setProgress(1.0);
            }
        });
    }
}
