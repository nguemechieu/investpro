package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Activity Monitor Panel - displays real-time system logs and events.
 * Shows strategy initialization, bootstrapping progress, and app-wide activity.
 *
 * Features:
 * - Real-time log streaming with timestamps
 * - Severity level filtering (INFO, WARN, ERROR, DEBUG)
 * - Component filtering (StrategyBootstrapper, StrategySelectionService, etc.)
 * - Scroll-to-bottom auto-follow
 * - Clear history button
 * - Log count display
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
@Slf4j
public class ActivityMonitorPanel extends BorderPane {
    private static final String BG_COLOR = "#0f1724";
    private static final String SECONDARY_BG = "#1a1f2e";
    private static final String TEXT_PRIMARY = "#e5edf7";
    private static final String TEXT_SECONDARY = "#9aa7ba";
    private static final String INFO_COLOR = "#2196F3";
    private static final String WARN_COLOR = "#FFC107";
    private static final String ERROR_COLOR = "#F44336";
    private static final String DEBUG_COLOR = "#4CAF50";

    private final ObservableList<ActivityLogEntry> logEntries = FXCollections.observableArrayList();
    private final List<ActivityLogEntry> allLogEntries = new CopyOnWriteArrayList<>();
    private TextArea logTextArea;
    private ListView<String> logListView;
    private Label logCountLabel;
    private ComboBox<String> severityFilterCombo;
    private ComboBox<String> componentFilterCombo;
    private CheckBox autoFollowCheckbox;
    private boolean autoFollow = true;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    public ActivityMonitorPanel() {
        initializeUI();
        startLogCapture();
    }

    private void initializeUI() {
        setStyle("-fx-background-color: " + BG_COLOR + ";");

        // Header
        Label headerLabel = new Label("Activity Monitor");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        headerLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        VBox header = new VBox(5, headerLabel);
        header.setStyle(
                "-fx-background-color: " + SECONDARY_BG + "; -fx-border-color: #263246; -fx-border-width: 0 0 1 0;");
        header.setPadding(new Insets(10, 12, 10, 12));
        setTop(header);

        // Control panel
        VBox controlPanel = createControlPanel();
        setLeft(controlPanel);

        // Log display area
        logTextArea = new TextArea();
        logTextArea.setStyle(
                "-fx-background-color: " + BG_COLOR + "; " +
                        "-fx-text-fill: " + TEXT_PRIMARY + "; " +
                        "-fx-control-inner-background: " + BG_COLOR + "; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 11px;");
        logTextArea.setEditable(false);
        logTextArea.setWrapText(false);
        logTextArea.setPrefRowCount(20);

        ScrollPane scrollPane = new ScrollPane(logTextArea);
        scrollPane.setStyle("-fx-background-color: " + BG_COLOR + ";");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        setCenter(scrollPane);

        // Footer with log count
        logCountLabel = new Label("Logs: 0");
        logCountLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");

        HBox footer = new HBox(10, logCountLabel);
        footer.setStyle(
                "-fx-background-color: " + SECONDARY_BG + "; -fx-border-color: #263246; -fx-border-width: 1 0 0 0;");
        footer.setPadding(new Insets(8, 12, 8, 12));
        setBottom(footer);

        setPrefHeight(400);
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color: " + SECONDARY_BG + "; -fx-padding: 10;");
        panel.setPrefWidth(200);

        // Severity filter
        Label severityLabel = new Label("Severity");
        severityLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        severityFilterCombo = new ComboBox<>();
        severityFilterCombo.setItems(FXCollections.observableArrayList(
                "ALL", "INFO", "WARN", "ERROR", "DEBUG"));
        severityFilterCombo.setValue("ALL");
        severityFilterCombo.setPrefWidth(180);
        severityFilterCombo.setStyle(
                "-fx-background-color: " + BG_COLOR + "; " +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";");
        severityFilterCombo.setOnAction(e -> updateLogDisplay());

        // Component filter
        Label componentLabel = new Label("Component");
        componentLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        componentFilterCombo = new ComboBox<>();
        componentFilterCombo.setItems(FXCollections.observableArrayList("ALL"));
        componentFilterCombo.setValue("ALL");
        componentFilterCombo.setPrefWidth(180);
        componentFilterCombo.setStyle(
                "-fx-background-color: " + BG_COLOR + "; " +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";");
        componentFilterCombo.setOnAction(e -> updateLogDisplay());

        // Auto-follow checkbox
        autoFollowCheckbox = new CheckBox("Auto-Follow");
        autoFollowCheckbox.setSelected(true);
        autoFollowCheckbox.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        autoFollowCheckbox.setOnAction(e -> autoFollow = autoFollowCheckbox.isSelected());

        // Clear button
        Button clearButton = new Button("Clear");
        clearButton.setPrefWidth(180);
        clearButton.setStyle(
                "-fx-background-color: #F44336; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 11px; " +
                        "-fx-padding: 5;");
        clearButton.setOnAction(e -> clearLogs());

        // Export button
        Button exportButton = new Button("Export");
        exportButton.setPrefWidth(180);
        exportButton.setStyle(
                "-fx-background-color: #4CAF50; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 11px; " +
                        "-fx-padding: 5;");
        exportButton.setOnAction(e -> exportLogs());

        panel.getChildren().addAll(
                severityLabel, severityFilterCombo,
                new Separator(),
                componentLabel, componentFilterCombo,
                new Separator(),
                autoFollowCheckbox,
                new Separator(),
                clearButton, exportButton);

        return panel;
    }

    /**
     * Add an activity log entry from system events
     */
    public void recordActivity(String severity, String component, String message, Instant timestamp) {
        Platform.runLater(() -> {
            ActivityLogEntry entry = new ActivityLogEntry(
                    severity,
                    component,
                    message,
                    timestamp);

            allLogEntries.add(entry);
            updateComponentFilter();
            updateLogDisplay();

            // Keep only last 1000 entries to prevent memory issues
            if (allLogEntries.size() > 1000) {
                allLogEntries.remove(0);
            }
        });
    }

    /**
     * Convenience method to record INFO level activity
     */
    public void recordInfo(String component, String message) {
        recordActivity("INFO", component, message, Instant.now());
    }

    /**
     * Convenience method to record WARN level activity
     */
    public void recordWarning(String component, String message) {
        recordActivity("WARN", component, message, Instant.now());
    }

    /**
     * Convenience method to record ERROR level activity
     */
    public void recordError(String component, String message) {
        recordActivity("ERROR", component, message, Instant.now());
    }

    /**
     * Convenience method to record DEBUG level activity
     */
    public void recordDebug(String component, String message) {
        recordActivity("DEBUG", component, message, Instant.now());
    }

    private void updateComponentFilter() {
        Set<String> components = new HashSet<>();
        components.add("ALL");
        for (ActivityLogEntry entry : allLogEntries) {
            components.add(entry.component());
        }

        String currentSelection = componentFilterCombo.getValue();
        componentFilterCombo.setItems(FXCollections.observableArrayList(
                components.stream().sorted().toList()));

        if (components.contains(currentSelection)) {
            componentFilterCombo.setValue(currentSelection);
        } else {
            componentFilterCombo.setValue("ALL");
        }
    }

    private void updateLogDisplay() {
        String severityFilter = severityFilterCombo.getValue();
        String componentFilter = componentFilterCombo.getValue();

        StringBuilder displayText = new StringBuilder();

        for (ActivityLogEntry entry : allLogEntries) {
            boolean matchesSeverity = severityFilter.equals("ALL") || entry.severity().equals(severityFilter);
            boolean matchesComponent = componentFilter.equals("ALL") || entry.component().equals(componentFilter);

            if (matchesSeverity && matchesComponent) {
                String timeStr = timeFormatter.format(entry.timestamp());
                String line = String.format("[%s] [%-5s] [%-30s] %s\n",
                        timeStr,
                        entry.severity(),
                        entry.component(),
                        entry.message());
                displayText.append(line);
            }
        }

        logTextArea.setText(displayText.toString());
        logCountLabel.setText("Logs: " + allLogEntries.size());

        if (autoFollow) {
            logTextArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void clearLogs() {
        allLogEntries.clear();
        logTextArea.clear();
        logCountLabel.setText("Logs: 0");
        componentFilterCombo.setItems(FXCollections.observableArrayList("ALL"));
        componentFilterCombo.setValue("ALL");
    }

    private void exportLogs() {
        try {
            StringBuilder export = new StringBuilder();
            export.append("=== Activity Monitor Export ===\n");
            export.append("Exported at: ").append(Instant.now()).append("\n");
            export.append("Total entries: ").append(allLogEntries.size()).append("\n\n");

            for (ActivityLogEntry entry : allLogEntries) {
                String timeStr = timeFormatter.format(entry.timestamp());
                export.append(String.format("[%s] [%-5s] [%-30s] %s\n",
                        timeStr,
                        entry.severity(),
                        entry.component(),
                        entry.message()));
            }

            // In real implementation, write to file
            log.info("Activity monitor export generated: {} entries", allLogEntries.size());
        } catch (Exception e) {
            log.error("Error exporting activity logs", e);
        }
    }

    private void startLogCapture() {
        // Hook into SLF4J/Logback to capture logs
        // This is handled by ActivityLogAppender configured in logback.xml
        log.debug("Activity monitor log capture started");
    }

    /**
     * Internal record class for activity log entries
     */
    public record ActivityLogEntry(
            String severity,
            String component,
            String message,
            Instant timestamp) {
    }
}
