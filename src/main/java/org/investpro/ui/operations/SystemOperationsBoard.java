package org.investpro.ui.operations;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.investpro.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


/**
 * Professional Operations Center board for InvestPro.
 * Monitors system health, exchange status, trading engine, risk, and activity
 * logs.
 */
@Getter
@Setter
@Slf4j
public class SystemOperationsBoard extends BorderPane {

    private final SystemOperationsService operationsService;
    private final SystemActivityBus activityBus;
    private final TabPane tabPane;
    private Timeline refreshTimeline;

    // UI Components
    private Label systemHealthLabel;
    private Label uptimeLabel;
    private Label memoryLabel;
    private TableView<SystemActivityEvent> activityTableView;
    private TextArea snapshotJsonArea;
    private Label exchangeStatusLabel;
    private TableView<SystemSnapshot.ExchangeStatusSnapshot> exchangeTableView;

    private static final int REFRESH_INTERVAL_MS = 2000; // 2 seconds
        private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public SystemOperationsBoard() {
        this.operationsService = SystemOperationsService.getInstance();
        this.activityBus = SystemActivityBus.getInstance();
        this.tabPane = new TabPane();

        initializeUI();
        setupAutoRefresh();
    }

    private void initializeUI() {
        getStyleClass().add("system-operations-board");

        // Create toolbar
        HBox toolbar = createToolbar();
        setTop(toolbar);

        // Create tabs
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("operations-tab-pane");
        tabPane.getTabs().addAll(
                createFeedTab(),
                createTradingEngineTab(),
                createRiskTab(),
                createActivityTab(),
                createErrorsTab());

        // Wrap tabPane in VBox to support grow
        VBox center = new VBox(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        setCenter(center);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("operations-toolbar");

        systemHealthLabel = new Label("🟢 HEALTHY");
        systemHealthLabel.getStyleClass().add("system-health-label");

        uptimeLabel = new Label("Uptime: 0h");
        uptimeLabel.getStyleClass().add("operations-info-label");
        memoryLabel = new Label("Memory: 0%");
        memoryLabel.getStyleClass().add("operations-info-label");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("operations-button");
        refreshBtn.setOnAction(e -> refreshAllData());

        Button exportBtn = new Button("💾 Export Snapshot");
        exportBtn.getStyleClass().add("operations-button");
        exportBtn.setOnAction(e -> exportSnapshot());

        Button clearLogsBtn = new Button("🗑️ Clear Logs");
        clearLogsBtn.getStyleClass().add("operations-button");
        clearLogsBtn.setOnAction(e -> clearActivityLogs());

        Separator sep = new Separator();
        sep.getStyleClass().add("operations-separator");

        toolbar.getChildren().addAll(
                systemHealthLabel,
                new Separator(),
                uptimeLabel,
                memoryLabel,
                sep,
                refreshBtn,
                exportBtn,
                clearLogsBtn);
        HBox.setHgrow(sep, Priority.ALWAYS);

        return toolbar;
    }

    /**
     * Overview tab - System health cards
     */
    Tab createOverviewTab() {
        Tab tab = new Tab("Overview", null);
        tab.setClosable(false);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("operations-overview-grid");

        // System Card
        VBox systemCard = createCard("System", createSystemCardContent());
        GridPane.setColumnSpan(systemCard, 2);

        // Exchange Card
        VBox exchangeCard = createCard("Exchange", createExchangeCardContent());

        // Trading Card
        VBox tradingCard = createCard("Trading Engine", createTradingCardContent());

        // Risk Card
        VBox riskCard = createCard("Risk Manager", createRiskCardContent());

        grid.add(systemCard, 0, 0);
        grid.add(exchangeCard, 0, 1);
        grid.add(tradingCard, 1, 1);
        grid.add(riskCard, 0, 2);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("operations-scroll-pane");
        scrollPane.setStyle("-fx-background: transparent; -fx-padding: 0;");
        tab.setContent(scrollPane);

        return tab;
    }

    /**
     * Exchanges tab - List of exchange status
     */
    private Tab createFeedTab() {
        Tab tab = new Tab("Feed", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        exchangeStatusLabel = new Label("No exchanges connected");
        exchangeStatusLabel.getStyleClass().add("operations-info-label");

        exchangeTableView = new TableView<>();
        setupExchangeTable();

        vbox.getChildren().addAll(exchangeStatusLabel, exchangeTableView);
        VBox.setVgrow(exchangeTableView, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Trading Engine tab
     */
    private Tab createTradingEngineTab() {
        Tab tab = new Tab("Trading Engine", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label titleLabel = new Label("Trading Engine Status");
        titleLabel.setStyle(
                "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + ("-text-primary") + ";");

        TextArea engineArea = new TextArea();
        engineArea.setEditable(false);
        engineArea.setWrapText(true);
        engineArea.getStyleClass().add("operations-text-area");

        vbox.getChildren().addAll(titleLabel, engineArea);
        VBox.setVgrow(engineArea, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Risk tab
     */
    private Tab createRiskTab() {
        Tab tab = new Tab("Risk", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label titleLabel = new Label("Risk Manager Status");
        titleLabel.setStyle(
                "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + ("-text-primary") + ";");

        TextArea riskArea = new TextArea();
        riskArea.setEditable(false);
        riskArea.setWrapText(true);
        riskArea.getStyleClass().add("operations-text-area");

        vbox.getChildren().addAll(titleLabel, riskArea);
        VBox.setVgrow(riskArea, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Activity Log tab
     */
    private Tab createActivityTab() {
        Tab tab = new Tab("Activity Log", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        HBox filterBox = new HBox(10);
        filterBox.getStyleClass().add("operations-filter-box");

        ComboBox<String> componentFilter = new ComboBox<>();
        componentFilter.setPromptText("Filter by Component");
        componentFilter.getStyleClass().add("operations-combo-box");
        componentFilter.getItems().addAll("ALL", "EXCHANGE", "TRADING_ENGINE", "RISK_MANAGER", "WEBSOCKET");
        componentFilter.setValue("ALL");

        ComboBox<String> severityFilter = new ComboBox<>();
        severityFilter.setPromptText("Filter by Severity");
        severityFilter.getStyleClass().add("operations-combo-box");
        severityFilter.getItems().addAll("ALL", "INFO", "WARN", "ERROR", "CRITICAL");
        severityFilter.setValue("ALL");

        filterBox.getChildren().addAll(
                new Label("Component:"), componentFilter,
                new Label("Severity:"), severityFilter);

        activityTableView = new TableView<>();
        setupActivityTable();

        vbox.getChildren().addAll(filterBox, activityTableView);
        VBox.setVgrow(activityTableView, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Agents & AI tab
     */
     Tab createAgentsTab() {
        Tab tab = new Tab("Agents & AI", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        HBox filterBox = new HBox(10);
        filterBox.getStyleClass().add("operations-filter-box");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.setPromptText("Filter by Status");
        statusFilter.getStyleClass().add("operations-combo-box");
        statusFilter.getItems().addAll("ALL", "ACTIVE", "IDLE", "ERROR", "OFFLINE");
        statusFilter.setValue("ALL");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("operations-button");

        filterBox.getChildren().addAll(
                new Label("Status:"), statusFilter,
                new Region(), // spacer
                refreshBtn);
        HBox.setHgrow(filterBox.getChildren().get(2), Priority.ALWAYS);

        ListView<String> agentListView = new ListView<>();
        agentListView.getStyleClass().add("console-content");
        agentListView.setPrefHeight(300);

        // Placeholder items - can be updated dynamically
        ObservableList<String> agents = FXCollections.observableArrayList(
                "Smart Trading Bot - Status: Active",
                "Signal Processor - Status: Active",
                "Risk Manager AI - Status: Idle",
                "Market Analyzer - Status: Active",
                "Portfolio Optimizer - Status: Idle");
        agentListView.setItems(agents);

        vbox.getChildren().addAll(filterBox, agentListView);
        VBox.setVgrow(agentListView, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Snapshots tab
     */
    @SuppressWarnings("unused")
    private Tab createSnapshotsTab() {
        Tab tab = new Tab("Snapshots", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(5));

        Button createBtn = new Button("📸 Create Snapshot");
        createBtn.getStyleClass().add("operations-button");
        createBtn.setOnAction(e -> createAndDisplaySnapshot());

        Button copyBtn = new Button("📋 Copy JSON");
        copyBtn.getStyleClass().add("operations-button");
        copyBtn.setOnAction(e -> copySnapshotJson());

        Button exportBtn = new Button("💾 Export JSON");
        exportBtn.getStyleClass().add("operations-button");
        exportBtn.setOnAction(e -> exportSnapshotJson());

        buttonBox.getChildren().addAll(createBtn, copyBtn, exportBtn);

        snapshotJsonArea = new TextArea();
        snapshotJsonArea.setEditable(false);
        snapshotJsonArea.setWrapText(true);
        snapshotJsonArea.getStyleClass().add("operations-text-area");

        vbox.getChildren().addAll(buttonBox, snapshotJsonArea);
        VBox.setVgrow(snapshotJsonArea, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Errors tab
     */
    private Tab createErrorsTab() {
        Tab tab = new Tab("Errors & Warnings", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        HBox statsBox = new HBox(20);
        statsBox.getStyleClass().add("operations-stats-box");

        Label errorCountLabel = new Label("Errors: 0");
        errorCountLabel.getStyleClass().add("operations-error-count");

        Label warnCountLabel = new Label("Warnings: 0");
        warnCountLabel.getStyleClass().add("operations-warn-count");

        Label criticalCountLabel = new Label("Critical: 0");
        criticalCountLabel.getStyleClass().add("operations-critical-count");

        statsBox.getChildren().addAll(errorCountLabel, warnCountLabel, criticalCountLabel);

        TextArea errorArea = new TextArea();
        errorArea.setEditable(false);
        errorArea.setWrapText(true);
        errorArea.getStyleClass().add("operations-text-area");

        vbox.getChildren().addAll(statsBox, errorArea);
        VBox.setVgrow(errorArea, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    // ==================== Helper Methods ====================

    private VBox createCard(String title, VBox content) {
        VBox card = new VBox(10);
        card.getStyleClass().add("operations-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("operations-card-title");

        card.getChildren().addAll(titleLabel, new Separator(), content);
        return card;
    }

    private VBox createSystemCardContent() {
        VBox vbox = new VBox(8);
        vbox.getChildren().addAll(
                createInfoRow("App:", "InvestPro"),
                createInfoRow("Version:", "1.0.0"),
                createInfoRow("Java:", System.getProperty("java.version")),
                createInfoRow("OS:", System.getProperty("os.name")),
                uptimeLabel,
                memoryLabel);
        return vbox;
    }

    private VBox createExchangeCardContent() {
        VBox vbox = new VBox(8);
        vbox.getChildren().addAll(
                createInfoRow("Status:", "Disconnected"),
                createInfoRow("WebSocket:", "Closed"),
                createInfoRow("REST:", "Unavailable"),
                createInfoRow("Auth:", "Not Authenticated"));
        return vbox;
    }

    private VBox createTradingCardContent() {
        VBox vbox = new VBox(8);
        vbox.getChildren().addAll(
                createInfoRow("Bot:", "Disabled"),
                createInfoRow("Signals Today:", "0"),
                createInfoRow("Trades Approved:", "0"),
                createInfoRow("Trades Rejected:", "0"));
        return vbox;
    }

    private VBox createRiskCardContent() {
        VBox vbox = new VBox(8);
        vbox.getChildren().addAll(
                createInfoRow("Balance:", "$0.00"),
                createInfoRow("Exposure:", "0%"),
                createInfoRow("Blocked Trades:", "0"),
                createInfoRow("Daily Loss:", "$0.00"));
        return vbox;
    }

    private HBox createInfoRow(String label, String value) {
        HBox hbox = new HBox(10);
        hbox.getStyleClass().add("operations-info-row");
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("operations-info-label-bold");
        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("operations-info-value");
        hbox.getChildren().addAll(labelLbl, valueLbl);
        return hbox;
    }

    private void setupExchangeTable() {
        TableColumn<SystemSnapshot.ExchangeStatusSnapshot, String> nameCol = new TableColumn<>("Exchange");
        nameCol.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getExchangeName()));

        TableColumn<SystemSnapshot.ExchangeStatusSnapshot, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isConnected() ? "Connected" : "Disconnected"));

        TableColumn<SystemSnapshot.ExchangeStatusSnapshot, String> wsCol = new TableColumn<>("WebSocket");
        wsCol.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getWebsocketState()));

        TableColumn<SystemSnapshot.ExchangeStatusSnapshot, String> restCol = new TableColumn<>("REST");
        restCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isRestAvailable() ? "Available" : "Unavailable"));

        exchangeTableView.getColumns().addAll(nameCol, statusCol, wsCol, restCol);
    }

    private void setupActivityTable() {
        TableColumn<SystemActivityEvent, String> timestampCol = new TableColumn<>("Time");
        timestampCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getTimestamp().toString()));

        TableColumn<SystemActivityEvent, String> componentCol = new TableColumn<>("Component");
        componentCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getComponent().toString()));

        TableColumn<SystemActivityEvent, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getSeverity().toString()));

        TableColumn<SystemActivityEvent, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getMessage()));

        activityTableView.getColumns().addAll(timestampCol, componentCol, severityCol, messageCol);

        refreshActivityTable();
    }

    private void refreshActivityTable() {
        ObservableList<SystemActivityEvent> events = FXCollections.observableArrayList(
                operationsService.getRecentEvents(200));
        activityTableView.setItems(events);
    }

    private void createAndDisplaySnapshot() {
        SystemSnapshot snapshot = operationsService.createSnapshot();
        displaySnapshot(snapshot);
    }

    private void displaySnapshot(SystemSnapshot snapshot) {
        try {
            String json = SNAPSHOT_MAPPER.writeValueAsString(snapshot);
            snapshotJsonArea.setText(json);
        } catch (Exception e) {
            snapshotJsonArea.setText("Error: " + e.getMessage());
            log.error("Error displaying snapshot", e);
        }
    }

    private void copySnapshotJson() {
        String text = snapshotJsonArea.getText();
        if (text != null && !text.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            showNotification("Snapshot copied to clipboard");
        }
    }

    private void exportSnapshotJson() {
        // TODO: Implement file save dialog
        showNotification("Export not yet implemented");
    }

    private void exportSnapshot() {
        // TODO: Implement full export dialog
        showNotification("Exporting system snapshot...");
    }

    private void clearActivityLogs() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Activity Logs");
        alert.setHeaderText("Clear all activity logs?");
        alert.setContentText("This action cannot be undone.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            operationsService.clearActivityHistory();
            refreshActivityTable();
            showNotification("Activity logs cleared");
        }
    }

    private void refreshAllData() {
        SystemSnapshot snapshot = operationsService.createSnapshot();
        updateHealthStatus(snapshot);
        updateAllTabs(snapshot);
        refreshActivityTable();
    }

    private void updateHealthStatus(SystemSnapshot snapshot) {
        Platform.runLater(() -> {
            String badge = snapshot.getHealthBadge();
            String emoji = switch (badge) {
                case "CRITICAL" -> "🔴";
                case "DEGRADED" -> "🟠";
                case "WARNING" -> "🟡";
                default -> "🟢";
            };

            systemHealthLabel.setText(emoji + " " + badge);
            uptimeLabel.setText("Uptime: " + formatUptime(snapshot.getUptimeSeconds()));
            memoryLabel.setText(String.format("Memory: %.1f%% (%dMB/%dMB)",
                    snapshot.getMemoryPercent(),
                    snapshot.getMemoryUsedMb(),
                    snapshot.getMemoryMaxMb()));
        });
    }

    private void updateAllTabs(SystemSnapshot snapshot) {
        // Update exchange table
        ObservableList<SystemSnapshot.ExchangeStatusSnapshot> exchangeData = FXCollections
                .observableArrayList(snapshot.getExchanges());
        Platform.runLater(() -> exchangeTableView.setItems(exchangeData));
    }

    private void setupAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(
                javafx.util.Duration.millis(REFRESH_INTERVAL_MS),
                e -> refreshAllData()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    public void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    private String formatUptime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }

    private void showNotification(String message) {
        // TODO: Implement toast notification or similar
        log.info(message);
    }
}
