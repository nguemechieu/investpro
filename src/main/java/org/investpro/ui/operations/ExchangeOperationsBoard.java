package org.investpro.ui.operations;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.AdaptivePollingEngine;
import org.investpro.exchange.resilience.ExchangeConnectivityManager;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.WebSocketOrchestrator;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.exchange.resilience.model.ExchangeHealthGrade;
import org.investpro.exchange.resilience.model.ExchangeHealthReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Institutional-grade exchange monitoring dashboard for InvestPro.
 *
 * <p>Displays live exchange health metrics in a JavaFX panel:
 * <ul>
 *   <li>Exchange health grade (color-coded GREEN / YELLOW / ORANGE / RED)</li>
 *   <li>WebSocket status and reconnect count</li>
 *   <li>Per-endpoint circuit breaker state</li>
 *   <li>Per-endpoint request latency</li>
 *   <li>Adaptive polling level</li>
 *   <li>Stale cache hit counter</li>
 *   <li>Request volume (req/s)</li>
 *   <li>Smart Idle mode indicator</li>
 * </ul>
 *
 * <p>All UI updates are dispatched to the JavaFX Application Thread via
 * {@link Platform#runLater(Runnable)} so this panel is safe to attach
 * from any thread.
 */
@Slf4j
public final class ExchangeOperationsBoard extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int REFRESH_INTERVAL_SECONDS = 2;

    private final ExchangeConnectivityManager connectivityManager;
    @Nullable
    private final ExchangeTelemetryEngine telemetryEngine;
    @Nullable
    private final AdaptivePollingEngine pollingEngine;
    @Nullable
    private final WebSocketOrchestrator wsOrchestrator;

    // ── Health grade banner ────────────────────────────────────────────────
    private final Label healthGradeLabel = createBannerLabel("INITIALIZING");
    private final Circle healthGradeIndicator = new Circle(12);

    // ── WebSocket row ──────────────────────────────────────────────────────
    private final Label wsStatusLabel = styledLabel("--");
    private final Label wsReconnectsLabel = styledLabel("--");
    private final Label wsMessagesLabel = styledLabel("--");
    private final Label restFallbackLabel = styledLabel("--");

    // ── Adaptive polling row ───────────────────────────────────────────────
    private final Label pollingLevelLabel = styledLabel("--");
    private final Label idleModeLabel = styledLabel("--");

    // ── Telemetry summary ─────────────────────────────────────────────────
    private final Label reqPerSecLabel = styledLabel("--");
    private final Label staleCacheHitsLabel = styledLabel("--");
    private final Label circuitTripsLabel = styledLabel("--");
    private final Label circuitRecoveriesLabel = styledLabel("--");

    // ── Endpoint table ────────────────────────────────────────────────────
    private final TableView<EndpointRow> endpointTable = new TableView<>();
    private final Map<EndpointType, EndpointRow> endpointRows = new EnumMap<>(EndpointType.class);

    private final ScheduledExecutorService refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ops-board-refresh");
        t.setDaemon(true);
        return t;
    });

    // ── Constructor ───────────────────────────────────────────────────────

    public ExchangeOperationsBoard(
            @NotNull ExchangeConnectivityManager connectivityManager,
            @Nullable ExchangeTelemetryEngine telemetryEngine,
            @Nullable AdaptivePollingEngine pollingEngine,
            @Nullable WebSocketOrchestrator wsOrchestrator) {
        this.connectivityManager = connectivityManager;
        this.telemetryEngine = telemetryEngine;
        this.pollingEngine = pollingEngine;
        this.wsOrchestrator = wsOrchestrator;

        buildLayout();
        startRefreshLoop();
    }

    /** Stops the background refresh scheduler. Call on panel close. */
    public void shutdown() {
        refreshScheduler.shutdownNow();
    }

    // ── Layout construction ───────────────────────────────────────────────

    private void buildLayout() {
        setSpacing(12);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #1a1d23;");

        getChildren().addAll(
                buildBannerSection(),
                buildDivider(),
                buildInfoGrid(),
                buildDivider(),
                buildEndpointTable()
        );
    }

    private Node buildBannerSection() {
        Label title = new Label("Exchange Operations Board");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        healthGradeIndicator.setFill(Color.GRAY);
        HBox gradeRow = new HBox(10, healthGradeIndicator, healthGradeLabel);
        gradeRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, title, gradeRow);
        box.setPadding(new Insets(0, 0, 4, 0));
        return box;
    }

    private Node buildInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(8);

        int row = 0;

        // WebSocket section
        grid.add(sectionLabel("WebSocket"), 0, row, 4, 1);
        row++;
        grid.add(captionLabel("Status:"), 0, row);    grid.add(wsStatusLabel, 1, row);
        grid.add(captionLabel("Reconnects:"), 2, row); grid.add(wsReconnectsLabel, 3, row);
        row++;
        grid.add(captionLabel("Messages:"), 0, row);   grid.add(wsMessagesLabel, 1, row);
        grid.add(captionLabel("REST Fallback:"), 2, row); grid.add(restFallbackLabel, 3, row);
        row++;

        // Adaptive polling section
        grid.add(sectionLabel("Adaptive Polling"), 0, row, 4, 1);
        row++;
        grid.add(captionLabel("Level:"), 0, row);    grid.add(pollingLevelLabel, 1, row);
        grid.add(captionLabel("Smart Idle:"), 2, row); grid.add(idleModeLabel, 3, row);
        row++;

        // Telemetry section
        grid.add(sectionLabel("Telemetry"), 0, row, 4, 1);
        row++;
        grid.add(captionLabel("Req/s:"), 0, row);      grid.add(reqPerSecLabel, 1, row);
        grid.add(captionLabel("Stale Cache:"), 2, row); grid.add(staleCacheHitsLabel, 3, row);
        row++;
        grid.add(captionLabel("CB Trips:"), 0, row);   grid.add(circuitTripsLabel, 1, row);
        grid.add(captionLabel("CB Recoveries:"), 2, row); grid.add(circuitRecoveriesLabel, 3, row);

        return grid;
    }

    @SuppressWarnings("unchecked")
    private Node buildEndpointTable() {
        Label header = sectionLabel("Endpoint Health");

        TableColumn<EndpointRow, String> nameCol = new TableColumn<>("Endpoint");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(130);

        TableColumn<EndpointRow, String> critCol = new TableColumn<>("Type");
        critCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().critical()));
        critCol.setPrefWidth(70);

        TableColumn<EndpointRow, String> circuitCol = new TableColumn<>("Circuit");
        circuitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().circuit()));
        circuitCol.setPrefWidth(85);
        circuitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "CLOSED"    -> "-fx-text-fill: #4caf50;";
                    case "OPEN"      -> "-fx-text-fill: #f44336; -fx-font-weight: bold;";
                    case "HALF_OPEN" -> "-fx-text-fill: #ff9800;";
                    default          -> "-fx-text-fill: #9e9e9e;";
                });
            }
        });

        TableColumn<EndpointRow, String> latencyCol = new TableColumn<>("Avg Latency");
        latencyCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().latency()));
        latencyCol.setPrefWidth(95);

        TableColumn<EndpointRow, String> failCol = new TableColumn<>("Failures");
        failCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().failures()));
        failCol.setPrefWidth(70);

        TableColumn<EndpointRow, String> successCol = new TableColumn<>("Success %");
        successCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().successRatio()));
        successCol.setPrefWidth(75);

        endpointTable.getColumns().addAll(nameCol, critCol, circuitCol, latencyCol, failCol, successCol);
        endpointTable.setStyle("-fx-background-color: #21252b; -fx-control-inner-background: #21252b;");
        endpointTable.setPrefHeight(200);
        endpointTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Pre-populate rows
        for (EndpointType ep : EndpointType.values()) {
            EndpointRow rowData = new EndpointRow(ep.name(),
                    ep.critical ? "CRITICAL" : "non-critical",
                    "CLOSED", "--", "0", "100%");
            endpointRows.put(ep, rowData);
            endpointTable.getItems().add(rowData);
        }

        return new VBox(6, header, endpointTable);
    }

    // ── Refresh loop ──────────────────────────────────────────────────────

    private void startRefreshLoop() {
        refreshScheduler.scheduleAtFixedRate(() -> {
            try {
                refreshData();
            } catch (Exception e) {
                log.debug("ExchangeOperationsBoard refresh error: {}", e.getMessage());
            }
        }, 1, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshData() {
        // fixed: was getHealthReport() — correct method is buildHealthReport()
        ExchangeHealthReport report = connectivityManager.buildHealthReport();

        // Gather telemetry snapshot
        ExchangeTelemetryEngine.ExchangeTelemetrySnapshot telemetry =
                telemetryEngine != null ? telemetryEngine.snapshot() : null;

        // Gather WS info
        boolean wsActive = wsOrchestrator != null && wsOrchestrator.isWebSocketActive();
        boolean restFallback = wsOrchestrator != null && wsOrchestrator.isRestFallbackActive();
        int reconnects = wsOrchestrator != null ? wsOrchestrator.getReconnectAttempts() : 0;
        long wsMessages = wsOrchestrator != null ? wsOrchestrator.getWsMessagesReceived() : 0;

        // Gather polling info
        boolean smartIdle = pollingEngine != null && pollingEngine.isSmartIdleMode();
        // fixed: was getActivityLevel() — correct method is currentActivityLevel()
        String pollingLevel = pollingEngine != null ? pollingEngine.currentActivityLevel().name() : "--";

        Platform.runLater(() -> updateUi(report, telemetry, wsActive, restFallback,
                reconnects, wsMessages, smartIdle, pollingLevel));
    }

    private void updateUi(
            ExchangeHealthReport report,
            @Nullable ExchangeTelemetryEngine.ExchangeTelemetrySnapshot telemetry,
            boolean wsActive, boolean restFallback,
            int reconnects, long wsMessages,
            boolean smartIdle, String pollingLevel) {

        // fixed: was report.healthGrade() / report.overallScore() — correct accessors are grade() / compositeScore()
        ExchangeHealthGrade grade = report.grade();
        healthGradeLabel.setText("Health: " + grade.name() + " (" + String.format("%.0f%%", report.compositeScore() * 100) + ")");
        healthGradeLabel.setTextFill(gradeColor(grade));
        healthGradeIndicator.setFill(gradeColor(grade));

        // WebSocket
        wsStatusLabel.setText(wsActive ? "ACTIVE" : "OFFLINE");
        wsStatusLabel.setTextFill(wsActive ? Color.web("#4caf50") : Color.web("#f44336"));
        wsReconnectsLabel.setText(String.valueOf(reconnects));
        wsMessagesLabel.setText(String.format("%,d", wsMessages));
        restFallbackLabel.setText(restFallback ? "ACTIVE" : "off");
        restFallbackLabel.setTextFill(restFallback ? Color.web("#ff9800") : Color.web("#9e9e9e"));

        // Adaptive polling
        pollingLevelLabel.setText(pollingLevel);
        idleModeLabel.setText(smartIdle ? "ON" : "off");
        idleModeLabel.setTextFill(smartIdle ? Color.web("#ff9800") : Color.web("#9e9e9e"));

        // Telemetry
        if (telemetry != null) {
            reqPerSecLabel.setText(String.format("%.1f", telemetry.requestsPerSecond()));
            // fixed: was staleCacheHits() — correct accessor is staleCacheServed()
            staleCacheHitsLabel.setText(String.format("%,d", telemetry.staleCacheServed()));
            circuitTripsLabel.setText(String.format("%,d", telemetry.circuitBreakerTrips()));
            circuitRecoveriesLabel.setText(String.format("%,d", telemetry.circuitBreakerRecoveries()));
        }

        // fixed: was report.endpointHealth() — correct accessor is endpointSnapshots()
        report.endpointSnapshots().forEach((ep, snapshot) -> {
            EndpointRow row = endpointRows.get(ep);
            if (row == null) return;
            int idx = endpointTable.getItems().indexOf(row);
            if (idx < 0) return;
            EndpointRow updated = new EndpointRow(
                    ep.name(),
                    ep.critical ? "CRITICAL" : "non-critical",
                    snapshot.circuitState().name(),
                    // fixed: was snapshot.avgLatencyMs() — correct accessor is averageLatencyMs()
                    snapshot.averageLatencyMs() < 0 ? "--" : String.format("%dms", (long) snapshot.averageLatencyMs()),
                    String.valueOf(snapshot.consecutiveFailures()),
                    String.format("%.0f%%", snapshot.successRatio() * 100)
            );
            endpointTable.getItems().set(idx, updated);
            endpointRows.put(ep, updated);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Color gradeColor(ExchangeHealthGrade grade) {
        return switch (grade) {
            case GREEN  -> Color.web("#4caf50");
            case YELLOW -> Color.web("#ffeb3b");
            case ORANGE -> Color.web("#ff9800");
            case RED    -> Color.web("#f44336");
        };
    }

    private static Label createBannerLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 18));
        l.setTextFill(Color.web("#4caf50"));
        return l;
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#e0e0e0"));
        l.setFont(Font.font("System", 12));
        return l;
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#90caf9"));
        return l;
    }

    private static Label captionLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#9e9e9e"));
        l.setFont(Font.font("System", 11));
        return l;
    }

    private static Separator buildDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333842;");
        return sep;
    }

    /** Mutable data row for the endpoint table. */
    private record EndpointRow(
            String name,
            String critical,
            String circuit,
            String latency,
            String failures,
            String successRatio
    ) {}
}
