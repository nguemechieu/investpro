package org.investpro.ui;

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.models.MarketWatchRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * MarketWatch Panel displays symbol-level agent state and trading readiness.
 * 
 * Shows:
 * - Market data (bid, ask, spread)
 * - Agent state (training, paper trading, live ready, live trading, blocked,
 * etc.)
 * - Active strategy and timeframe
 * - Strategy score
 * - Live readiness status
 * - Issues/reasons if blocked
 */
@Getter
@Setter
@Slf4j
public class MarketWatchPanel extends BorderPane {

    private final SystemCore systemCore;
    private final SymbolAgentManager symbolAgentManager;
    private final TableView<MarketWatchRow> table = new TableView<>();
    private final Map<TradePair, MarketWatchRow> rowCache = new LinkedHashMap<>();

    private Timeline refreshTimer;
    private static final long REFRESH_INTERVAL_MS = 3000; // Refresh every 3 seconds
    private int lastRowCount = -1; // Track last row count to avoid unnecessary updates

    public MarketWatchPanel(@NotNull SystemCore systemCore) {
        this.systemCore = systemCore;
        this.symbolAgentManager = systemCore.getSmartBot().getSymbolAgentManager();

        initializeUI();
        startAutoRefresh();
    }

    private void initializeUI() {
        // Setup table columns
        setupTableColumns();

        // Setup table styling with size constraints
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setStyle("-fx-font-size: 11px;");
        table.setPrefHeight(400);
        table.setMinHeight(200);
        table.setFixedCellSize(25); // Set fixed cell height to prevent rendering issues

        // Create controls
        HBox controls = createControlsBar();

        // Layout
        VBox content = new VBox(8, controls, table);
        content.setPadding(new Insets(8));
        content.setMinHeight(300);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        this.setCenter(content);
        this.setPadding(new Insets(4));
    }

    private void setupTableColumns() {
        // Symbol column
        TableColumn<MarketWatchRow, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getSymbol().toString()));
        symbolCol.setPrefWidth(100);

        // Bid column
        TableColumn<MarketWatchRow, Double> bidCol = new TableColumn<>("Bid");
        bidCol.setCellValueFactory(cellData -> (ObservableValue<Double>) (Object) cellData.getValue().bidProperty());
        bidCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.5f", item));
            }
        });
        bidCol.setPrefWidth(80);

        // Ask column
        TableColumn<MarketWatchRow, Double> askCol = new TableColumn<>("Ask");
        askCol.setCellValueFactory(cellData -> (ObservableValue<Double>) (Object) cellData.getValue().askProperty());
        askCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.5f", item));
            }
        });
        askCol.setPrefWidth(80);

        // Spread % column
        TableColumn<MarketWatchRow, Double> spreadCol = new TableColumn<>("Spread %");
        spreadCol.setCellValueFactory(
                cellData -> (ObservableValue<Double>) (Object) cellData.getValue().spreadPercentProperty());
        spreadCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.4f", item));
            }
        });
        spreadCol.setPrefWidth(70);

        // Session column
        TableColumn<MarketWatchRow, String> sessionCol = new TableColumn<>("Session");
        sessionCol.setCellValueFactory(cellData -> cellData.getValue().sessionProperty());
        sessionCol.setPrefWidth(60);

        // Agent State column
        TableColumn<MarketWatchRow, String> agentStateCol = new TableColumn<>("State");
        agentStateCol.setCellValueFactory(cellData -> cellData.getValue().agentStateProperty());
        agentStateCol.setPrefWidth(90);

        // Trading Mode column (with color coding)
        TableColumn<MarketWatchRow, String> modeCol = new TableColumn<>("Mode");
        modeCol.setCellValueFactory(cellData -> cellData.getValue().tradingModeProperty());
        modeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    applyModeStyle(item);
                }
            }

            private void applyModeStyle(String mode) {
                String style = switch (mode.toLowerCase()) {
                    case "training / evaluating" ->
                        "-fx-text-fill: #94a3b8; -fx-font-weight: normal;";
                    case "paper trading" ->
                        "-fx-text-fill: #3b82f6; -fx-font-weight: normal;";
                    case "live ready" ->
                        "-fx-text-fill: #22c55e; -fx-font-weight: bold;";
                    case "live trading" ->
                        "-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-background-color: rgba(16,185,129,0.1);";
                    case "blocked" ->
                        "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
                    case "paused" ->
                        "-fx-text-fill: #f59e0b; -fx-font-weight: normal;";
                    case "failed" ->
                        "-fx-text-fill: #dc2626; -fx-font-weight: bold;";
                    default -> "-fx-text-fill: #6b7280;";
                };
                setStyle(style);
            }
        });
        modeCol.setPrefWidth(110);

        // Active Strategy column
        TableColumn<MarketWatchRow, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(cellData -> cellData.getValue().activeStrategyProperty());
        strategyCol.setPrefWidth(110);

        // Timeframe column
        TableColumn<MarketWatchRow, String> tfCol = new TableColumn<>("TF");
        tfCol.setCellValueFactory(cellData -> cellData.getValue().activeTimeframeProperty());
        tfCol.setPrefWidth(50);

        // Strategy Score column
        TableColumn<MarketWatchRow, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(
                cellData -> (ObservableValue<Double>) (Object) cellData.getValue().strategyScoreProperty());
        scoreCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item == 0.0) {
                    setText("");
                } else {
                    setText(String.format("%.2f", item));
                    // Color code the score
                    String style = item > 0.75 ? "-fx-text-fill: #22c55e;"
                            : item > 0.50 ? "-fx-text-fill: #3b82f6;"
                                    : item > 0.25 ? "-fx-text-fill: #f59e0b;" : "-fx-text-fill: #ef4444;";
                    setStyle(style);
                }
            }
        });
        scoreCol.setPrefWidth(60);

        // Live Ready column
        TableColumn<MarketWatchRow, Boolean> liveReadyCol = new TableColumn<>("Live Ready");
        liveReadyCol.setCellValueFactory(cellData -> cellData.getValue().liveReadyProperty());
        liveReadyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item ? "✓ Yes" : "✗ No");
                    String style = item ? "-fx-text-fill: #22c55e; -fx-font-weight: bold;" : "-fx-text-fill: #ef4444;";
                    setStyle(style);
                }
            }
        });
        liveReadyCol.setPrefWidth(75);

        // Issue column
        TableColumn<MarketWatchRow, String> issueCol = new TableColumn<>("Issue");
        issueCol.setCellValueFactory(cellData -> cellData.getValue().issueProperty());
        issueCol.setPrefWidth(200);
        issueCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #ef4444; -fx-font-size: 10px;");
                    setWrapText(true);
                }
            }
        });

        // Add columns to table
        table.getColumns().addAll(
                symbolCol, bidCol, askCol, spreadCol, sessionCol, modeCol,
                strategyCol, tfCol, scoreCol, liveReadyCol, issueCol);
    }

    private HBox createControlsBar() {
        Label titleLabel = new Label("Market Watch - Symbol Agent Status");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        Button refreshButton = new Button("⟳ Refresh");
        refreshButton.setOnAction(e -> refreshMarketWatchData());

        Button pauseButton = new Button("⏸ Pause");
        pauseButton.setOnAction(e -> {
            if (refreshTimer != null && refreshTimer.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                refreshTimer.stop();
                pauseButton.setText("▶ Resume");
            } else {
                startAutoRefresh();
                pauseButton.setText("⏸ Pause");
            }
        });

        HBox.setHgrow(titleLabel, javafx.scene.layout.Priority.ALWAYS);
        HBox controls = new HBox(8, titleLabel, refreshButton, pauseButton);
        controls.setPadding(new Insets(4));
        controls.setStyle("-fx-background-color: #1e293b; -fx-border-color: #475569; -fx-border-width: 0 0 1 0;");
        return controls;
    }

    private void startAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }

        refreshTimer = new Timeline(
                new javafx.animation.KeyFrame(
                        Duration.millis(REFRESH_INTERVAL_MS),
                        event -> refreshMarketWatchData()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    /**
     * Refresh market watch data from SymbolAgentManager.
     * 
     * This pulls the latest symbol states and updates the table.
     * Only updates if the data has actually changed.
     */
    public synchronized void refreshMarketWatchData() {
        if (symbolAgentManager == null) {
            log.warn("SymbolAgentManager is not available");
            return;
        }

        Platform.runLater(() -> {
            try {
                List<SymbolAgentState> allStates = symbolAgentManager.getAllStates();

                // Only update if row count changed
                if (allStates.size() == lastRowCount && lastRowCount > 0) {
                    // Data count hasn't changed, just refresh the values
                    table.refresh();
                    return;
                }

                // Remove rows for symbols no longer in the manager
                rowCache.keySet().retainAll(
                        allStates.stream()
                                .map(SymbolAgentState::getSymbol)
                                .toList());

                // Update or create rows
                for (SymbolAgentState state : allStates) {
                    if (state != null && state.getSymbol() != null) {
                        TradePair symbol = state.getSymbol();
                        MarketWatchRow row = rowCache.computeIfAbsent(
                                symbol,
                                key -> MarketWatchRow.builder()
                                        .symbol(symbol)
                                        .build());

                        // Update the row with symbol state
                        row.updateSymbolState(state);
                    }
                }

                // Only update table items if count changed
                int newRowCount = rowCache.size();
                if (newRowCount != lastRowCount) {
                    table.getItems().setAll(new java.util.ArrayList<>(rowCache.values()));
                    lastRowCount = newRowCount;
                    log.debug("Refreshed MarketWatch with {} symbols", newRowCount);
                } else {
                    // Just refresh the displayed data
                    table.refresh();
                }
            } catch (Exception e) {
                log.error("Error refreshing market watch data", e);
            }
        });
    }

    /**
     * Update a specific symbol's row.
     */
    public void updateSymbol(@NotNull TradePair symbol, @Nullable SymbolAgentState state) {
        Platform.runLater(() -> {
            if (state == null) {
                rowCache.remove(symbol);
            } else {
                MarketWatchRow row = rowCache.computeIfAbsent(
                        symbol,
                        key -> MarketWatchRow.builder()
                                .symbol(symbol)
                                .build());
                row.updateSymbolState(state);
            }
            table.refresh();
        });
    }

    /**
     * Shutdown the panel and cleanup resources.
     */
    public void shutdown() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
