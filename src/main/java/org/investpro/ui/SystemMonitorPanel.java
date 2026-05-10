package org.investpro.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Separator;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.monitoring.ComponentHealth;
import org.investpro.monitoring.ComponentStatus;
import org.investpro.monitoring.SystemHealthSnapshot;
import org.jetbrains.annotations.NotNull;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Getter
@Setter
@Slf4j
public final class SystemMonitorPanel extends BorderPane {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final String HEALTHY_COLOR = "#10b981"; // Green
    private static final String DEGRADED_COLOR = "#f59e0b"; // Amber
    private static final String WARNING_COLOR = "#f59e0b"; // Amber
    private static final String FAILED_COLOR = "#ef4444"; // Red
    private static final String UNKNOWN_COLOR = "#6b7280"; // Gray

    private static final String PRIMARY_BG = "#0f172a"; // Deep blue-black
    private static final String SECONDARY_BG = "#1e293b"; // Slate
    private static final String CARD_BG = "#1e293b"; // Slate
    private static final String TEXT_PRIMARY = "#f1f5f9"; // Light
    private static final String TEXT_SECONDARY = "#cbd5e1"; // Light gray
    private static final String ACCENT_COLOR = "#3b82f6"; // Blue

    private final Supplier<SystemHealthSnapshot> snapshotSupplier;
    private final Stage stage = new Stage();
    private final Label overallStatusLabel = new Label("-");
    private final Label tradingStatusLabel = new Label("-");
    private final Label lastUpdatedLabel = new Label("-");
    private final Label summaryLabel = new Label("-");
    private final FlowPane componentCardsPane = new FlowPane();
    private final TextArea blockersArea = readOnlyArea();
    private final TextArea warningsArea = readOnlyArea();
    private final TextArea detailsArea = readOnlyArea();
    private final TextArea reportArea = readOnlyArea();
    private final TextArea statisticsArea = readOnlyArea();
    private final TextArea alertsArea = readOnlyArea();
    private final Deque<AlertRecord> alertHistory = new ConcurrentLinkedDeque<>();
    private final ProgressIndicator refreshIndicator = new ProgressIndicator(-1);

    // Network monitoring fields
    private final Label networkStatusLabel = new Label("-");
    private final Label networkLatencyLabel = new Label("-");
    private volatile long lastNetworkLatencyMs = 0;
    private volatile boolean networkConnected = false;

    private volatile SystemHealthSnapshot currentSnapshot;
    private volatile long lastRefreshTime = 0;

    private Spinner<Integer> refreshIntervalSpinner;
    private Label refreshIntervalLabel;
    private CheckBox autoRefreshCheckbox;

    public SystemMonitorPanel(Supplier<SystemHealthSnapshot> snapshotSupplier) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier cannot be null");
        stage.setTitle("System Monitoring");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        stage.setScene(new Scene(createContent(), 1200, 700));
        applyGlobalStyles();
    }

    public void show() {
        refresh();
        startAutoRefresh();
        stage.show();
        stage.toFront();
    }

    public void hide() {
        autoRefreshEnabled = false;
        stage.hide();
    }

    public static SystemHealthSnapshot notAvailable(String summary) {
        ComponentHealth unavailable = ComponentHealth.builder()
                .componentName("SystemCore")
                .status(ComponentStatus.UNKNOWN)
                .summary(summary == null || summary.isBlank() ? "SystemCore is not started" : summary)
                .build();

        return SystemHealthSnapshot.builder()
                .overallStatus(ComponentStatus.UNKNOWN)
                .canTrade(false)
                .summary(unavailable.getSummary())
                .exchange(unavailable)
                .blockers(List.of("SystemCore is not started"))
                .details(Map.of("state", "not_started"))
                .timestamp(Instant.now())
                .build();
    }

    private void applyGlobalStyles() {
        stage.getScene().getStylesheets().add("data:text/css," +
                ".root { " +
                "  -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif; " +
                "  -fx-font-size: 12px; " +
                "  -fx-base: #1e293b; " +
                "  -fx-control-inner-background: #0f172a; " +
                "} " +
                ".text-area { " +
                "  -fx-font-family: 'Monaco', 'Courier New', monospace; " +
                "  -fx-font-size: 11px; " +
                "  -fx-text-fill: #cbd5e1; " +
                "  -fx-control-inner-background: #0f172a; " +
                "} " +
                ".tab-pane .tab-header-background { " +
                "  -fx-background-color: #1e293b; " +
                "} " +
                ".tab-pane .tab { " +
                "  -fx-background-color: #1e293b; " +
                "} " +
                ".tab-pane .tab:selected { " +
                "  -fx-background-color: #3b82f6; " +
                "} " +
                ".button { " +
                "  -fx-text-fill: #f1f5f9; " +
                "  -fx-background-color: #3b82f6; " +
                "  -fx-padding: 8px 16px; " +
                "  -fx-border-radius: 4; " +
                "} " +
                ".button:hover { " +
                "  -fx-background-color: #2563eb; " +
                "} " +
                ".button:pressed { " +
                "  -fx-background-color: #1d4ed8; " +
                "} " +
                ".label { " +
                "  -fx-text-fill: #f1f5f9; " +
                "} " +
                ".scroll-pane { " +
                "  -fx-background-color: #0f172a; " +
                "} " +
                ".scroll-bar { " +
                "  -fx-background-color: #1e293b; " +
                "} ");
    }

    private BorderPane createContent() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + PRIMARY_BG + ";");
        root.setPadding(new Insets(16));

        // Header with title and controls
        root.setTop(createHeaderSection());

        // Main content area with status and components
        VBox mainContent = new VBox(16);
        mainContent.setStyle("-fx-background-color: " + PRIMARY_BG + ";");

        // Status overview cards
        mainContent.getChildren().add(createStatusOverview());

        // Component health cards
        ScrollPane componentScroll = new ScrollPane(componentCardsPane);
        componentScroll.setFitToWidth(true);
        componentScroll.setStyle(
                "-fx-background-color: " + PRIMARY_BG + "; -fx-control-inner-background: " + PRIMARY_BG + ";");
        mainContent.getChildren().add(createComponentsSection(componentScroll));

        // Details tabs
        mainContent.getChildren().add(createDetailsTabs());

        root.setCenter(mainContent);
        return root;
    }

    private HBox createHeaderSection() {
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setPrefWidth(100);
        refreshButton.setOnAction(event -> refresh());

        Button exportButton = new Button("📥 Export");
        exportButton.setPrefWidth(100);
        exportButton.setOnAction(event -> exportReport());

        // Auto-refresh toggle
        autoRefreshCheckbox = new CheckBox("Auto-Refresh");
        autoRefreshCheckbox.setSelected(true);
        autoRefreshCheckbox.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");
        autoRefreshCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            autoRefreshEnabled = newVal;
            refreshIntervalSpinner.setDisable(!newVal);
        });

        // Refresh interval spinner
        refreshIntervalSpinner = new Spinner<>(1, 60, 5, 1);
        refreshIntervalSpinner.setPrefWidth(70);
        refreshIntervalSpinner.setStyle("-fx-control-inner-background: #0f3460;");
        refreshIntervalSpinner.valueProperty().addListener((obs, old, newVal) -> refreshIntervalSeconds = newVal);

        refreshIntervalLabel = new Label("sec");
        refreshIntervalLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        header.setStyle("-fx-border-color: " + SECONDARY_BG + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🎯 InvestPro System Monitor");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getChildren().addAll(
                lastUpdatedLabel,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                autoRefreshCheckbox,
                refreshIntervalSpinner,
                refreshIntervalLabel,
                refreshButton,
                exportButton);

        lastUpdatedLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");

        header.getChildren().addAll(title, spacer, controls);
        return header;
    }

    private VBox createStatusOverview() {
        VBox overview = new VBox(12);
        overview.setPadding(new Insets(16));
        overview.setStyle("-fx-background-color: " + SECONDARY_BG + "; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8;");

        Label sectionTitle = new Label("System Status");
        sectionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sectionTitle.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        HBox statusCards = new HBox(16);
        statusCards.setAlignment(Pos.CENTER_LEFT);

        // Overall Status Card
        VBox overallCard = createStatusCard("Overall Status", overallStatusLabel);

        // Trading Status Card
        VBox tradingCard = createStatusCard("Trading Status", tradingStatusLabel);

        // Network Status Card
        VBox networkCard = createStatusCard("Network Status", networkStatusLabel);

        // Network Latency Card
        VBox latencyCard = createStatusCard("Network Latency", networkLatencyLabel);

        // Summary Card
        VBox summaryCard = new VBox(8);
        summaryCard.setPadding(new Insets(12));
        summaryCard.setStyle("-fx-background-color: " + CARD_BG + "; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;");
        Label summaryTitle = new Label("Summary");
        summaryTitle.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px;");
        summaryCard.getChildren().addAll(summaryTitle, summaryLabel);
        VBox.setVgrow(summaryCard, Priority.ALWAYS);

        statusCards.getChildren().addAll(overallCard, tradingCard, networkCard, latencyCard, summaryCard);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);

        overview.getChildren().addAll(sectionTitle, statusCards);
        return overview;
    }

    private @NotNull VBox createStatusCard(String title, Label statusLabel) {
        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: " + CARD_BG + "; " +
                "-fx-border-color: " + SystemMonitorPanel.ACCENT_COLOR + "; " +
                "-fx-border-width: 2 0 0 0; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold;");

        statusLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold;");

        card.getChildren().addAll(titleLabel, statusLabel);
        return card;
    }

    private VBox createComponentsSection(ScrollPane componentScroll) {
        VBox section = new VBox(12);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: " + SECONDARY_BG + "; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8;");

        Label sectionTitle = new Label("Component Health");
        sectionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sectionTitle.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        componentCardsPane.setPrefWidth(400);
        componentCardsPane.setHgap(12);
        componentCardsPane.setVgap(12);
        componentCardsPane.setPadding(new Insets(0));
        componentCardsPane.setStyle("-fx-background-color: " + SECONDARY_BG + ";");

        componentScroll.setPrefHeight(200);
        componentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        componentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        section.getChildren().addAll(sectionTitle, componentScroll);
        VBox.setVgrow(section, Priority.ALWAYS);
        return section;
    }

    private TabPane createDetailsTabs() {
        TabPane tabs = new TabPane();
        tabs.setPrefHeight(250);
        tabs.setStyle("-fx-background-color: " + SECONDARY_BG + ";");

        Tab blockersTab = new Tab("⚠️ Blockers", createTabWithCopyButton(blockersArea));
        blockersTab.setClosable(false);
        blockersTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab warningsTab = new Tab("⚡ Warnings", createTabWithCopyButton(warningsArea));
        warningsTab.setClosable(false);
        warningsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab detailsTab = new Tab("📋 Details", createTabWithCopyButton(detailsArea));
        detailsTab.setClosable(false);
        detailsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab reportTab = new Tab("📊 Report", createTabWithCopyButton(reportArea));
        reportTab.setClosable(false);
        reportTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab statisticsTab = new Tab("📈 Statistics", createTabWithCopyButton(statisticsArea));
        statisticsTab.setClosable(false);
        statisticsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab alertsTab = new Tab("🔔 Alerts", createTabWithCopyButton(alertsArea));
        alertsTab.setClosable(false);
        alertsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        tabs.getTabs().addAll(blockersTab, warningsTab, detailsTab, reportTab, statisticsTab, alertsTab);
        return tabs;
    }

    private VBox createTabWithCopyButton(TextArea textArea) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(8));
        container.setStyle("-fx-background-color: " + SECONDARY_BG + ";");

        Button copyButton = new Button("📋 Copy to Clipboard");
        copyButton.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px;");
        copyButton.setOnAction(event -> copyToClipboard(textArea.getText()));

        container.getChildren().addAll(copyButton, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return container;
    }

    private static TextArea readOnlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefHeight(150);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private Timeline autoRefreshTimeline;
    private boolean autoRefreshEnabled = false;
    private int refreshIntervalSeconds = 5;

    private void stopAutoRefresh() {
        autoRefreshEnabled = false;

        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
    }

    private void startAutoRefresh() {
        stopAutoRefresh();

        autoRefreshEnabled = true;

        autoRefreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(refreshIntervalSeconds), event -> {
                    if (autoRefreshEnabled) {
                        refresh();
                    }
                }));

        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private void refresh() {
        try {
            SystemHealthSnapshot snapshot = snapshotSupplier.get();
            if (snapshot == null) {
                snapshot = notAvailable("System monitor returned no snapshot");
            }
            final SystemHealthSnapshot finalSnapshot = snapshot;
            currentSnapshot = finalSnapshot;

            // Check network connectivity and latency on background thread
            Thread networkCheckThread = new Thread(this::checkNetworkHealth, "NetworkMonitor");
            networkCheckThread.setDaemon(true);
            networkCheckThread.start();

            Platform.runLater(() -> updateUI(finalSnapshot));
        } catch (Exception exception) {
            log.warn("Unable to refresh system monitor window", exception);
            final SystemHealthSnapshot finalFallback = notAvailable(
                    "Unable to refresh monitor: " + exception.getMessage());
            currentSnapshot = finalFallback;
            Platform.runLater(() -> updateUI(finalFallback));
        }
    }

    private void updateUI(SystemHealthSnapshot snapshot) {
        // Update header labels
        String statusColor = getStatusColor(snapshot.getOverallStatus());
        overallStatusLabel.setText(snapshot.getOverallStatus().getDisplayName());
        overallStatusLabel.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold;");

        String tradingColor = snapshot.isCanTrade() ? HEALTHY_COLOR : FAILED_COLOR;
        tradingStatusLabel.setText(snapshot.isCanTrade() ? "✅ ALLOWED" : "❌ BLOCKED");
        tradingStatusLabel.setStyle("-fx-text-fill: " + tradingColor + "; -fx-font-weight: bold;");

        lastUpdatedLabel.setText("Updated: " + TIME_FORMATTER.format(snapshot.getTimestamp()));
        summaryLabel.setText(snapshot.getSummary());

        // Update network status from last check
        updateNetworkStatusUI();

        // Update component cards
        updateComponentCards(snapshot);

        // Update detail areas
        blockersArea.setText(joinList(snapshot.getBlockers()));
        warningsArea.setText(joinList(snapshot.getWarnings()));
        detailsArea.setText(formatDetails(snapshot));
        reportArea.setText(snapshot.toFormattedReport());

        // Update statistics and alerts
        statisticsArea.setText(generateStatistics(snapshot));
        alertsArea.setText(generateAlertsReport());
    }

    private void updateComponentCards(SystemHealthSnapshot snapshot) {
        componentCardsPane.getChildren().clear();

        List<ComponentHealth> components = components(snapshot);
        for (ComponentHealth component : components) {
            componentCardsPane.getChildren().add(createComponentCard(component));
        }
    }

    private VBox createComponentCard(ComponentHealth component) {
        VBox card = new VBox(6);
        card.setPrefWidth(180);
        card.setPrefHeight(140);
        card.setPadding(new Insets(12));

        String statusColor = getStatusColor(component.getStatus());

        card.setStyle("-fx-background-color: " + CARD_BG + "; " +
                "-fx-border-color: " + statusColor + "; " +
                "-fx-border-width: 2 0 0 0; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;");

        // Component name with status indicator
        HBox nameBox = new HBox(8);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        Circle statusIndicator = new Circle(5);
        statusIndicator.setFill(Color.web(statusColor));

        Label componentName = new Label(component.getComponentName());
        componentName.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold;");
        nameBox.getChildren().addAll(statusIndicator, componentName);

        // Status badge
        Label statusBadge = new Label(
                component.getStatus().getDisplayName());
        statusBadge.setStyle("-fx-text-fill: " + statusColor + "; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold;");

        // Summary text
        Label summary = new Label(component.getSummary());
        summary.setWrapText(true);
        summary.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-size: 11px;");

        // Last checked time
        String checkedTime = TIME_FORMATTER.format(component.getLastCheckedAt());
        Label checkedLabel = new Label(checkedTime);
        checkedLabel.setStyle("-fx-text-fill: #6b7280; " +
                "-fx-font-size: 9px;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(nameBox, statusBadge, summary, spacer, checkedLabel);
        return card;
    }

    /**
     * Checks network connectivity and latency to major endpoints
     */
    private void checkNetworkHealth() {
        try {
            // Test endpoints for different exchanges
            String[] endpoints = {
                    "https://api.binance.us/api/v3/ping",
                    "https://api.coinbase.com/v2/currencies",
                    "https://api.tradingview.com/pine/health"
            };

            long minLatency = Long.MAX_VALUE;
            boolean anyConnected = false;

            for (String endpoint : endpoints) {
                try {
                    long latency = measureLatency(endpoint);
                    if (latency >= 0) {
                        anyConnected = true;
                        minLatency = Math.min(minLatency, latency);
                    }
                } catch (Exception e) {
                    log.debug("Failed to reach endpoint: {}", endpoint, e);
                }
            }

            networkConnected = anyConnected;
            if (minLatency != Long.MAX_VALUE) {
                lastNetworkLatencyMs = minLatency;
            }

            // Alert if network is slow (latency > 500ms)
            if (networkConnected && lastNetworkLatencyMs > 500) {
                String warning = String.format("⚠️ Slow Network: Latency is %dms", lastNetworkLatencyMs);
                recordWarningAlert(warning);
            }
        } catch (Exception e) {
            log.warn("Network health check failed", e);
            networkConnected = false;
        }
    }

    /**
     * Measures latency to a specific endpoint in milliseconds
     */
    private long measureLatency(String endpoint) {
        try {
            HttpClient httpClient = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint).toURL().toURI())
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return endTime - startTime;
            }
            return -1;
        } catch (Exception e) {
            log.debug("Failed to measure latency for {}: {}", endpoint, e.getMessage());
            return -1;
        }
    }

    /**
     * Updates the network status UI labels with current network state
     */
    private void updateNetworkStatusUI() {
        String networkStatus;
        String networkColor;

        if (!networkConnected) {
            networkStatus = "❌ DISCONNECTED";
            networkColor = FAILED_COLOR;
            recordCriticalAlert("⚠️ Network Disconnected - Trading operations may be affected");
        } else if (lastNetworkLatencyMs > 1000) {
            networkStatus = "⚠️ SLOW";
            networkColor = WARNING_COLOR;
        } else if (lastNetworkLatencyMs > 500) {
            networkStatus = "🟠 DEGRADED";
            networkColor = DEGRADED_COLOR;
        } else {
            networkStatus = "✅ HEALTHY";
            networkColor = HEALTHY_COLOR;
        }

        networkStatusLabel.setText(networkStatus);
        networkStatusLabel.setStyle("-fx-text-fill: " + networkColor + "; -fx-font-weight: bold;");

        String latencyText = networkConnected ? lastNetworkLatencyMs + "ms" : "No Connection";
        networkLatencyLabel.setText(latencyText);
        networkLatencyLabel.setStyle("-fx-text-fill: " + networkColor + "; -fx-font-weight: bold;");
    }

    private String getStatusColor(ComponentStatus status) {
        if (status == null)
            return UNKNOWN_COLOR;
        return switch (status) {
            case HEALTHY -> HEALTHY_COLOR;
            case DEGRADED -> DEGRADED_COLOR;
            case WARNING -> WARNING_COLOR;
            case FAILED -> FAILED_COLOR;
            case DISABLED, UNKNOWN -> UNKNOWN_COLOR;
        };
    }

    private List<ComponentHealth> components(SystemHealthSnapshot snapshot) {
        return List.of(
                snapshot.getExchange(),
                snapshot.getMarketData(),
                snapshot.getAccount(),
                snapshot.getStrategy(),
                snapshot.getRisk(),
                snapshot.getExecution(),
                snapshot.getAgents(),
                snapshot.getAi(),
                snapshot.getNotifications()

        );
    }

    private String formatDetails(SystemHealthSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        appendMap(builder, "Snapshot details", snapshot.getDetails());
        for (ComponentHealth component : components(snapshot)) {
            appendMap(builder, "\n" + component.getComponentName() + " details", component.getDetails());
            appendList(builder, component.getComponentName() + " blockers", component.getBlockers());
            appendList(builder, component.getComponentName() + " warnings", component.getWarnings());
        }
        return builder.toString().trim();
    }

    private void appendMap(StringBuilder builder, String title, Map<String, Object> values) {
        builder.append(title).append('\n');
        if (values == null || values.isEmpty()) {
            builder.append("  -\n");
            return;
        }
        values.forEach((key, value) -> builder.append("  ").append(key).append(": ").append(value).append('\n'));
    }

    private void appendList(StringBuilder builder, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(title).append('\n');
        values.forEach(value -> builder.append("  ").append(value).append('\n'));
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return String.join(System.lineSeparator(), values);
    }

    /**
     * Generates a comprehensive statistics report showing uptime, failure rates,
     * and component health
     */
    private String generateStatistics(SystemHealthSnapshot snapshot) {
        StringBuilder report = new StringBuilder();
        report.append("═════════════════════════════════════════════════════════════════\n");
        report.append("📊 SYSTEM STATISTICS REPORT\n");
        report.append("═════════════════════════════════════════════════════════════════\n\n");

        // Overall Statistics
        report.append("OVERALL METRICS:\n");
        report.append("─────────────────────────────────────────────────────────────────\n");
        report.append(String.format("Generated: %s\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        report.append(String.format("System Status: %s\n", snapshot.getOverallStatus().getDisplayName()));
        report.append(String.format("Trading Enabled: %s\n", snapshot.isCanTrade() ? "YES" : "NO"));

        // Component Statistics
        List<ComponentHealth> components = components(snapshot);
        int totalComponents = components.size();
        long healthyCount = components.stream().filter(c -> c.getStatus() == ComponentStatus.HEALTHY).count();
        long degradedCount = components.stream().filter(c -> c.getStatus() == ComponentStatus.DEGRADED).count();
        long warningCount = components.stream().filter(c -> c.getStatus() == ComponentStatus.WARNING).count();
        long failedCount = components.stream().filter(c -> c.getStatus() == ComponentStatus.FAILED).count();

        report.append("\nCOMPONENT HEALTH:\n");
        report.append("─────────────────────────────────────────────────────────────────\n");
        report.append(String.format("Total Components: %d\n", totalComponents));
        report.append(
                String.format("  ✅ Healthy:  %d (%.1f%%)\n", healthyCount, (healthyCount * 100.0 / totalComponents)));
        report.append(String.format("  ⚠️  Degraded: %d (%.1f%%)\n", degradedCount,
                (degradedCount * 100.0 / totalComponents)));
        report.append(
                String.format("  ⚡ Warning:  %d (%.1f%%)\n", warningCount, (warningCount * 100.0 / totalComponents)));
        report.append(
                String.format("  ❌ Failed:   %d (%.1f%%)\n", failedCount, (failedCount * 100.0 / totalComponents)));

        // Overall Uptime Percentage
        double overallHealthScore = ((healthyCount * 100.0) + (degradedCount * 75.0) + (warningCount * 50.0))
                / totalComponents;
        report.append(String.format("\nOverall Health Score: %.1f%%\n", overallHealthScore));

        // Component Details
        report.append("\nCOMPONENT DETAILS:\n");
        report.append("─────────────────────────────────────────────────────────────────\n");
        for (ComponentHealth component : components) {
            report.append(String.format("\n  %s (%s)\n", component.getComponentName(),
                    component.getStatus().getDisplayName()));
            report.append(String.format("    Last Checked: %s\n", TIME_FORMATTER.format(component.getLastCheckedAt())));
            report.append(String.format("    Summary: %s\n", component.getSummary()));
            if (!component.getBlockers().isEmpty()) {
                report.append(String.format("    Blockers: %d\n", component.getBlockers().size()));
            }
            if (!component.getWarnings().isEmpty()) {
                report.append(String.format("    Warnings: %d\n", component.getWarnings().size()));
            }
        }

        // Alerts Summary
        report.append("\n\nALERTS SUMMARY:\n");
        report.append("─────────────────────────────────────────────────────────────────\n");
        report.append(String.format("Total Alerts: %d\n", alertHistory.size()));
        long criticalAlerts = alertHistory.stream().filter(a -> "CRITICAL".equals(a.severity)).count();
        long warningAlerts = alertHistory.stream().filter(a -> "WARNING".equals(a.severity)).count();
        long infoAlerts = alertHistory.stream().filter(a -> "INFO".equals(a.severity)).count();
        report.append(String.format("  🔴 Critical: %d\n", criticalAlerts));
        report.append(String.format("  🟠 Warning:  %d\n", warningAlerts));
        report.append(String.format("  🔵 Info:     %d\n", infoAlerts));

        report.append("\n═════════════════════════════════════════════════════════════════\n");
        return report.toString();
    }

    /**
     * Generates a formatted report of recent alerts and notifications
     */
    private String generateAlertsReport() {
        if (alertHistory.isEmpty()) {
            return "No alerts recorded yet. The system will automatically log alerts as they occur.";
        }

        StringBuilder report = new StringBuilder();
        report.append("═════════════════════════════════════════════════════════════════\n");
        report.append("🔔 ALERT HISTORY (Last ").append(Math.min(20, alertHistory.size())).append(" alerts)\n");
        report.append("═════════════════════════════════════════════════════════════════\n\n");

        List<AlertRecord> recentAlerts = alertHistory.stream()
                .limit(20)
                .collect(Collectors.toCollection(ArrayList::new));

        // Reverse to show most recent first
        for (int i = recentAlerts.size() - 1; i >= 0; i--) {
            AlertRecord alert = recentAlerts.get(i);
            String severityEmoji = switch (alert.severity) {
                case "CRITICAL" -> "🔴";
                case "WARNING" -> "🟠";
                default -> "🔵";
            };
            report.append(String.format("%s [%s] %s\n", severityEmoji, alert.severity,
                    LocalDateTime.ofInstant(alert.timestamp, ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            report.append(String.format("   Message: %s\n\n", alert.message));
        }

        report.append("═════════════════════════════════════════════════════════════════\n");
        return report.toString();
    }

    /**
     * Records an alert with severity, message, and timestamp
     * Maintains up to 500 alerts in history
     */
    public void recordAlert(String severity, String message, Instant timestamp) {
        alertHistory.addLast(new AlertRecord(severity, message, timestamp));
        // Keep only the last 500 alerts to prevent memory issues
        while (alertHistory.size() > 500) {
            alertHistory.removeFirst();
        }
        log.info("Alert recorded: [{}] {}", severity, message);
    }

    /**
     * Records a critical alert with current timestamp
     */
    public void recordCriticalAlert(String message) {
        recordAlert("CRITICAL", message, Instant.now());
    }

    /**
     * Records a warning alert with current timestamp
     */
    public void recordWarningAlert(String message) {
        recordAlert("WARNING", message, Instant.now());
    }

    /**
     * Records an info alert with current timestamp
     */
    public void recordInfoAlert(String message) {
        recordAlert("INFO", message, Instant.now());
    }

    /**
     * Exports the current system report to a CSV/TXT file with timestamp
     */
    private void exportReport() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "system_report_" + timestamp + ".txt";
            Path reportPath = Paths.get(System.getProperty("user.home"), "Downloads", fileName);

            StringBuilder fullReport = new StringBuilder();
            fullReport.append("INVESTPRO SYSTEM MONITORING REPORT\n");
            fullReport.append("════════════════════════════════════════════════════════════════\n\n");
            fullReport.append("Generated: ")
                    .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append("\n\n");

            // Add all sections
            fullReport.append("OVERALL STATUS:\n")
                    .append("─────────────────────────────────────────────────────────────────\n")
                    .append(overallStatusLabel.getText()).append("\n");
            fullReport.append("Trading Status: ").append(tradingStatusLabel.getText()).append("\n\n");

            fullReport.append(statisticsArea.getText()).append("\n\n");
            fullReport.append("BLOCKERS:\n")
                    .append("─────────────────────────────────────────────────────────────────\n")
                    .append(blockersArea.getText()).append("\n\n");

            fullReport.append("WARNINGS:\n")
                    .append("─────────────────────────────────────────────────────────────────\n")
                    .append(warningsArea.getText()).append("\n\n");

            fullReport.append("DETAILED INFORMATION:\n")
                    .append("─────────────────────────────────────────────────────────────────\n")
                    .append(detailsArea.getText()).append("\n\n");

            fullReport.append(alertsArea.getText()).append("\n\n");
            fullReport.append(reportArea.getText());

            // Ensure parent directory exists
            Files.createDirectories(reportPath.getParent());

            // Write report
            try (FileWriter writer = new FileWriter(reportPath.toFile())) {
                writer.write(fullReport.toString());
            }

            // Show success message
            Platform.runLater(() -> {
                Label successLabel = new Label("✅ Report exported to: " + reportPath);
                successLabel.setStyle("-fx-text-fill: " + HEALTHY_COLOR + "; -fx-font-size: 12px;");
                log.info("Report exported successfully to: {}", reportPath);
            });
        } catch (Exception e) {
            log.error("Failed to export report", e);
            Platform.runLater(() -> {
                Label errorLabel = new Label("❌ Failed to export report: " + e.getMessage());
                errorLabel.setStyle("-fx-text-fill: " + FAILED_COLOR + "; -fx-font-size: 12px;");
            });
        }
    }

    /**
     * Copies text to system clipboard
     */
    private void copyToClipboard(String text) {
        try {
            if (text == null || text.isEmpty()) {
                log.warn("Cannot copy empty text to clipboard");
                return;
            }
            StringSelection stringSelection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            log.info("Text copied to clipboard (length: {})", text.length());
        } catch (Exception e) {
            log.error("Failed to copy text to clipboard", e);
        }
    }

    /**
     * Inner class to represent an alert record
     */
    private record AlertRecord(String severity, String message, Instant timestamp) {
    }
}
