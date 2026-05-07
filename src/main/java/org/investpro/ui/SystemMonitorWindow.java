package org.investpro.ui;

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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.monitoring.ComponentHealth;
import org.investpro.monitoring.ComponentStatus;
import org.investpro.monitoring.SystemHealthSnapshot;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
@Getter
@Setter
@Slf4j
public final class SystemMonitorWindow {
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
    private final ProgressIndicator refreshIndicator = new ProgressIndicator(-1);
    private volatile boolean autoRefreshEnabled = true;
    private volatile SystemHealthSnapshot currentSnapshot;

    public SystemMonitorWindow(Supplier<SystemHealthSnapshot> snapshotSupplier) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier cannot be null");
        stage.setTitle("InvestPro System Monitor");
        stage.setMinWidth(1200);
        stage.setMinHeight(800);
        stage.setScene(new Scene(createContent(), 1400, 900));
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
        Button refreshButton = new Button("Refresh");
        refreshButton.setPrefWidth(100);
        refreshButton.setOnAction(event -> refresh());

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        header.setStyle("-fx-border-color: " + SECONDARY_BG + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🎯 InvestPro System Monitor");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(12, lastUpdatedLabel, refreshButton);
        controls.setAlignment(Pos.CENTER_RIGHT);

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

        statusCards.getChildren().addAll(overallCard, tradingCard, summaryCard);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);

        overview.getChildren().addAll(sectionTitle, statusCards);
        return overview;
    }

    private @NotNull VBox createStatusCard(String title, Label statusLabel) {
        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: " + CARD_BG + "; " +
                "-fx-border-color: " + SystemMonitorWindow.ACCENT_COLOR + "; " +
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

        Tab blockersTab = new Tab("⚠️ Blockers", blockersArea);
        blockersTab.setClosable(false);
        blockersTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab warningsTab = new Tab("⚡ Warnings", warningsArea);
        warningsTab.setClosable(false);
        warningsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab detailsTab = new Tab("📋 Details", detailsArea);
        detailsTab.setClosable(false);
        detailsTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        Tab reportTab = new Tab("📊 Report", reportArea);
        reportTab.setClosable(false);
        reportTab.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        tabs.getTabs().addAll(blockersTab, warningsTab, detailsTab, reportTab);
        return tabs;
    }

    private static TextArea readOnlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefHeight(150);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private void startAutoRefresh() {
        Thread autoRefreshThread = new Thread(() -> {
            while (autoRefreshEnabled) {
                try {
                    Thread.sleep(5000); // Refresh every 5 seconds
                    if (autoRefreshEnabled) {
                        Platform.runLater(this::refresh);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "monitor-auto-refresh");
        autoRefreshThread.setDaemon(true);
        autoRefreshThread.start();
    }

    private void refresh() {
        try {
            SystemHealthSnapshot snapshot = snapshotSupplier.get();
            if (snapshot == null) {
                snapshot = notAvailable("System monitor returned no snapshot");
            }
            final SystemHealthSnapshot finalSnapshot = snapshot;
            currentSnapshot = finalSnapshot;

            Platform.runLater(() -> updateUI(finalSnapshot));
        } catch (Exception exception) {
            log.warn("Unable to refresh system monitor window", exception);
            final SystemHealthSnapshot finalFallback = notAvailable("Unable to refresh monitor: " + exception.getMessage());
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

        // Update component cards
        updateComponentCards(snapshot);

        // Update detail areas
        blockersArea.setText(joinList(snapshot.getBlockers()));
        warningsArea.setText(joinList(snapshot.getWarnings()));
        detailsArea.setText(formatDetails(snapshot));
        reportArea.setText(snapshot.toFormattedReport());
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

    private String getStatusColor(ComponentStatus status) {
        if (status == null)
            return UNKNOWN_COLOR;
        return switch (status) {
            case HEALTHY -> HEALTHY_COLOR;
            case DEGRADED -> DEGRADED_COLOR;
            case WARNING -> WARNING_COLOR;
            case FAILED -> FAILED_COLOR;
            case DISABLED -> UNKNOWN_COLOR;
            case UNKNOWN -> UNKNOWN_COLOR;
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
                snapshot.getNotifications());
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
}
