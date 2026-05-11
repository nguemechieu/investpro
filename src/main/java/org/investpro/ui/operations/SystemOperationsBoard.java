package org.investpro.ui.operations;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import org.investpro.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Professional Operations Center board for InvestPro.
 * Monitors system health, exchange status, trading engine, risk, and activity
 * logs.
 */
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_TIME;
    private static final int REFRESH_INTERVAL_MS = 2000; // 2 seconds

    public SystemOperationsBoard() {
        this.operationsService = SystemOperationsService.getInstance();
        this.activityBus = SystemActivityBus.getInstance();
        this.tabPane = new TabPane();

        initializeUI();
        setupAutoRefresh();
    }

    private void initializeUI() {
        setStyle("-fx-font-family: 'Segoe UI', 'Helvetica', sans-serif; -fx-font-size: 11;");

        // Create toolbar
        HBox toolbar = createToolbar();
        setTop(toolbar);

        // Create tabs
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                createOverviewTab(),
                createExchangesTab(),
                createTradingEngineTab(),
                createRiskTab(),
                createActivityTab(),
                createSnapshotsTab(),
                createErrorsTab());

        setCenter(tabPane);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8));
        toolbar.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        systemHealthLabel = new Label("🟢 HEALTHY");
        systemHealthLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        uptimeLabel = new Label("Uptime: 0h");
        memoryLabel = new Label("Memory: 0%");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refreshAllData());

        Button exportBtn = new Button("💾 Export Snapshot");
        exportBtn.setOnAction(e -> exportSnapshot());

        Button clearLogsBtn = new Button("🗑️ Clear Logs");
        clearLogsBtn.setOnAction(e -> clearActivityLogs());

        Separator sep = new Separator();
        sep.setPrefWidth(20);

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
    private Tab createOverviewTab() {
        Tab tab = new Tab("Overview", null);
        tab.setClosable(false);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(15));
        grid.setStyle("-fx-background-color: #f5f5f5;");

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
        scrollPane.setStyle("-fx-background: transparent;");
        tab.setContent(scrollPane);

        return tab;
    }

    /**
     * Exchanges tab - List of exchange status
     */
    private Tab createExchangesTab() {
        Tab tab = new Tab("Exchanges", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        exchangeStatusLabel = new Label("No exchanges connected");
        exchangeStatusLabel.setStyle("-fx-font-size: 12;");

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
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        TextArea engineArea = new TextArea();
        engineArea.setEditable(false);
        engineArea.setWrapText(true);
        engineArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");

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
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        TextArea riskArea = new TextArea();
        riskArea.setEditable(false);
        riskArea.setWrapText(true);
        riskArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");

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
        filterBox.setPadding(new Insets(5));
        filterBox.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        ComboBox<String> componentFilter = new ComboBox<>();
        componentFilter.setPromptText("Filter by Component");
        componentFilter.getItems().addAll("ALL", "EXCHANGE", "TRADING_ENGINE", "RISK_MANAGER", "WEBSOCKET");
        componentFilter.setValue("ALL");

        ComboBox<String> severityFilter = new ComboBox<>();
        severityFilter.setPromptText("Filter by Severity");
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
     * Snapshots tab
     */
    private Tab createSnapshotsTab() {
        Tab tab = new Tab("Snapshots", null);
        tab.setClosable(false);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(5));

        Button createBtn = new Button("📸 Create Snapshot");
        createBtn.setOnAction(e -> createAndDisplaySnapshot());

        Button copyBtn = new Button("📋 Copy JSON");
        copyBtn.setOnAction(e -> copySnapshotJson());

        Button exportBtn = new Button("💾 Export JSON");
        exportBtn.setOnAction(e -> exportSnapshotJson());

        buttonBox.getChildren().addAll(createBtn, copyBtn, exportBtn);

        snapshotJsonArea = new TextArea();
        snapshotJsonArea.setEditable(false);
        snapshotJsonArea.setWrapText(true);
        snapshotJsonArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");

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
        statsBox.setPadding(new Insets(5));
        statsBox.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label errorCountLabel = new Label("Errors: 0");
        errorCountLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");

        Label warnCountLabel = new Label("Warnings: 0");
        warnCountLabel.setStyle("-fx-text-fill: #ffa500; -fx-font-weight: bold;");

        Label criticalCountLabel = new Label("Critical: 0");
        criticalCountLabel.setStyle("-fx-text-fill: #c41c3b; -fx-font-weight: bold;");

        statsBox.getChildren().addAll(errorCountLabel, warnCountLabel, criticalCountLabel);

        TextArea errorArea = new TextArea();
        errorArea.setEditable(false);
        errorArea.setWrapText(true);
        errorArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");

        vbox.getChildren().addAll(statsBox, errorArea);
        VBox.setVgrow(errorArea, Priority.ALWAYS);

        tab.setContent(vbox);
        return tab;
    }

    // ==================== Helper Methods ====================

    private VBox createCard(String title, VBox content) {
        VBox card = new VBox(10);
        card.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 12; " +
                "-fx-background-color: white; -fx-border-width: 1;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");

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
        Label labelLbl = new Label(label);
        labelLbl.setStyle("-fx-font-weight: bold; -fx-min-width: 100;");
        Label valueLbl = new Label(value);
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
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(snapshot);
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
