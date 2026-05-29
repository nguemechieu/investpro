package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.Callback;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;
import org.investpro.strategy.management.StrategyAssignmentManager;
import org.investpro.strategy.portfolio.PortfolioIntelligenceEngine;
import org.investpro.strategy.portfolio.PortfolioIntelligenceReport;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-featured strategy assignment dashboard with a 7-tab TableView layout.
 * Displays strategies grouped by lifecycle status and provides quick-action
 * buttons for lifecycle management.
 *
 * <p>Auto-refreshes data every 10 seconds from {@link StrategyAssignmentManager}.</p>
 */
public class StrategyAssignmentDashboard extends BorderPane {

    private static final String DARK_BG = "-fx-background-color: #0d1117;";
    private static final String HEADER_STYLE = "-fx-background-color: #161b22; -fx-padding: 12px;";
    private static final String TEXT_WHITE = "-fx-text-fill: #e6edf3;";
    private static final String TEXT_MUTED = "-fx-text-fill: #8b949e;";
    private static final String BTN_PROMOTE = "-fx-background-color: #238636; -fx-text-fill: white;";
    private static final String BTN_DEMOTE  = "-fx-background-color: #da3633; -fx-text-fill: white;";
    private static final String BTN_PAUSE   = "-fx-background-color: #9e6a03; -fx-text-fill: white;";
    private static final String BTN_REFRESH = "-fx-background-color: #1f6feb; -fx-text-fill: white;";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StrategyAssignmentManager manager = StrategyAssignmentManager.getInstance();
    private final PortfolioIntelligenceEngine portfolioEngine = PortfolioIntelligenceEngine.getInstance();

    private final Label lastRefreshLabel = new Label("Last refresh: —");
    private final Label portfolioSummaryLabel = new Label("");
    private final TabPane tabPane = new TabPane();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    // Observable lists per status tab
    private final ObservableList<StrategyLifecycleRecord> allRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> liveRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> paperRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> pendingRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> degradedRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> pausedRecords = FXCollections.observableArrayList();
    private final ObservableList<StrategyLifecycleRecord> archivedRecords = FXCollections.observableArrayList();

    /**
     * Constructs the dashboard and starts the 10-second auto-refresh timer.
     */
    public StrategyAssignmentDashboard() {
        setStyle(DARK_BG);
        buildLayout();
        subscribeToEvents();
        startAutoRefresh();
        refreshData();
    }

    // =========================================================================
    // Layout
    // =========================================================================

    private void buildLayout() {
        // ---- Header ----
        Label title = new Label("Strategy Assignment Dashboard");
        title.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 18px; -fx-font-weight: bold;");
        lastRefreshLabel.setStyle(TEXT_MUTED + " -fx-font-size: 11px;");
        portfolioSummaryLabel.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 12px;");

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.setStyle(BTN_REFRESH);
        refreshBtn.setOnAction(e -> refreshData());

        HBox header = new HBox(10, title);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().addAll(portfolioSummaryLabel, lastRefreshLabel, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(HEADER_STYLE);
        header.setPadding(new Insets(12, 16, 12, 16));

        setTop(header);

        // ---- Tabs ----
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle(DARK_BG);

        tabPane.getTabs().addAll(
                buildTab("All", allRecords, true),
                buildTab("Live", liveRecords, true),
                buildTab("Paper", paperRecords, true),
                buildTab("Pending", pendingRecords, false),
                buildTab("Degraded", degradedRecords, true),
                buildTab("Paused", pausedRecords, true),
                buildTab("Archived", archivedRecords, false)
        );

        setCenter(tabPane);
    }

    private Tab buildTab(String title, ObservableList<StrategyLifecycleRecord> items, boolean showActions) {
        TableView<StrategyLifecycleRecord> table = buildTable(items, showActions);
        Tab tab = new Tab(title, table);
        tab.setStyle(TEXT_WHITE);
        return tab;
    }

    @SuppressWarnings("unchecked")
    private TableView<StrategyLifecycleRecord> buildTable(ObservableList<StrategyLifecycleRecord> items,
                                                          boolean showActions) {
        TableView<StrategyLifecycleRecord> table = new TableView<>(items);
        table.setStyle(DARK_BG + " -fx-text-fill: #e6edf3;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StrategyLifecycleRecord, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));

        TableColumn<StrategyLifecycleRecord, String> tfCol = new TableColumn<>("TF");
        tfCol.setCellValueFactory(new PropertyValueFactory<>("timeframe"));
        tfCol.setMaxWidth(60);

        TableColumn<StrategyLifecycleRecord, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(new PropertyValueFactory<>("strategyName"));

        TableColumn<StrategyLifecycleRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("lifecycleStatus"));
        statusCol.setCellFactory(coloredStatusCell());

        TableColumn<StrategyLifecycleRecord, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("assignmentScore"));
        scoreCol.setMaxWidth(70);

        TableColumn<StrategyLifecycleRecord, Double> confidenceCol = new TableColumn<>("AI Conf");
        confidenceCol.setCellValueFactory(new PropertyValueFactory<>("aiConfidence"));
        confidenceCol.setMaxWidth(80);

        table.getColumns().addAll(symbolCol, tfCol, strategyCol, statusCol, scoreCol, confidenceCol);

        if (showActions) {
            TableColumn<StrategyLifecycleRecord, Void> actionsCol = new TableColumn<>("Actions");
            actionsCol.setCellFactory(buildActionsCellFactory());
            actionsCol.setMinWidth(220);
            table.getColumns().add(actionsCol);
        }

        return table;
    }

    private Callback<TableColumn<StrategyLifecycleRecord, Void>,
            TableCell<StrategyLifecycleRecord, Void>> buildActionsCellFactory() {
        return col -> new TableCell<>() {
            private final Button promoteBtn = new Button("Promote");
            private final Button demoteBtn = new Button("Demote");
            private final Button pauseBtn = new Button("Pause");

            {
                promoteBtn.setStyle(BTN_PROMOTE);
                demoteBtn.setStyle(BTN_DEMOTE);
                pauseBtn.setStyle(BTN_PAUSE);

                promoteBtn.setOnAction(e -> {
                    StrategyLifecycleRecord rec = getTableView().getItems().get(getIndex());
                    manager.promoteToLive(rec.getAssignmentId(), "Manual promotion via dashboard");
                    refreshData();
                });
                demoteBtn.setOnAction(e -> {
                    StrategyLifecycleRecord rec = getTableView().getItems().get(getIndex());
                    manager.demote(rec.getAssignmentId(), "Manual demotion via dashboard");
                    refreshData();
                });
                pauseBtn.setOnAction(e -> {
                    StrategyLifecycleRecord rec = getTableView().getItems().get(getIndex());
                    manager.pause(rec.getAssignmentId(), "Manual pause via dashboard");
                    refreshData();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(4, promoteBtn, demoteBtn, pauseBtn);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        };
    }

    private Callback<TableColumn<StrategyLifecycleRecord, String>,
            TableCell<StrategyLifecycleRecord, String>> coloredStatusCell() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = switch (item) {
                        case "LIVE_ACTIVE" -> "#3fb950";
                        case "PAPER_TRADING", "ASSIGNED" -> "#58a6ff";
                        case "DEGRADED", "RISK_BREACH" -> "#f85149";
                        case "PAUSED" -> "#d29922";
                        case "ARCHIVED", "REPLACED" -> "#8b949e";
                        default -> "#e6edf3";
                    };
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        };
    }

    // =========================================================================
    // Data refresh
    // =========================================================================

    private void refreshData() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            List<StrategyLifecycleRecord> all = manager.getAllRecords();

            Platform.runLater(() -> {
                allRecords.setAll(all);
                liveRecords.setAll(filter(all, StrategyLifecycleStatus.LIVE_ACTIVE));
                paperRecords.setAll(filter(all, StrategyLifecycleStatus.PAPER_TRADING));
                pendingRecords.setAll(filter(all, StrategyLifecycleStatus.ASSIGNED));
                degradedRecords.setAll(filter(all, StrategyLifecycleStatus.DEGRADED));
                pausedRecords.setAll(filter(all, StrategyLifecycleStatus.PAUSED));
                archivedRecords.setAll(filter(all, StrategyLifecycleStatus.ARCHIVED));

                lastRefreshLabel.setText("Last refresh: " + FORMATTER.format(java.time.Instant.now()));

                portfolioEngine.getLastReport().ifPresent(this::updatePortfolioSummary);
            });
        } finally {
            refreshing.set(false);
        }
    }

    private List<StrategyLifecycleRecord> filter(List<StrategyLifecycleRecord> all,
                                                  StrategyLifecycleStatus status) {
        return all.stream()
                .filter(r -> r.getLifecycleStatus() == status)
                .toList();
    }

    private void updatePortfolioSummary(PortfolioIntelligenceReport report) {
        portfolioSummaryLabel.setText(String.format(
                "Active: %d  Live: %d  Degraded: %d  Symbols: %d  Warnings: %d",
                report.getActiveStrategies(), report.getLiveStrategies(),
                report.getDegradedStrategies(), report.getSymbolCount(),
                report.getWarnings().size()));
    }

    // =========================================================================
    // Event subscription
    // =========================================================================

    private void subscribeToEvents() {
        EventBusManager bus = EventBusManager.getInstance();
        bus.subscribe(AgentEvent.STRATEGY_ASSIGNED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_PROMOTED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_DEMOTED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_PAUSED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_RESUMED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_ARCHIVED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.STRATEGY_HEALTH_CHANGED, e -> Platform.runLater(this::refreshData));
        bus.subscribe(AgentEvent.PORTFOLIO_ANALYZED, e -> Platform.runLater(this::refreshData));
    }

    // =========================================================================
    // Auto-refresh
    // =========================================================================

    private void startAutoRefresh() {
        Timer timer = new Timer("lifecycle-dashboard-refresh", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshData();
            }
        }, 10_000L, 10_000L);
    }
}
