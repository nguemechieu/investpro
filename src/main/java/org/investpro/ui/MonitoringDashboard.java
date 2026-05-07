package org.investpro.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.control.Label;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.monitoring.ComponentHealth;
import org.investpro.monitoring.ComponentStatus;
import org.investpro.monitoring.SystemHealthSnapshot;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Modern monitoring dashboard component with real-time metrics and status
 * visualization.
 * Displays system health at a glance with animated indicators and color-coded
 * status.
 */
@Getter
@Setter
@Slf4j
public final class MonitoringDashboard extends VBox {

    private static final String PRIMARY_BG = "#0f172a"; // Deep blue-black
    private static final String SECONDARY_BG = "#1e293b"; // Slate
    private static final String CARD_BG = "#1e293b"; // Slate
    private static final String TEXT_PRIMARY = "#f1f5f9"; // Light
    private static final String TEXT_SECONDARY = "#cbd5e1"; // Light gray
    private static final String ACCENT_COLOR = "#3b82f6"; // Blue

    private static final String HEALTHY_COLOR = "#10b981"; // Green
    private static final String DEGRADED_COLOR = "#f59e0b"; // Amber
    private static final String WARNING_COLOR = "#f59e0b"; // Amber
    private static final String FAILED_COLOR = "#ef4444"; // Red

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final GridPane metricsGrid = new GridPane();
    private final Label uptimeLabel = new Label("-");
    private final Label checkFrequencyLabel = new Label("-");
    private final Label healthyCountLabel = new Label("-");
    private final Label issueCountLabel = new Label("-");
    private final Label lastCheckLabel = new Label("-");
    private final Label systemStatusLabel = new Label("-");

    private final long startTime = System.currentTimeMillis();
    private long checkCount = 0;

    public MonitoringDashboard() {
        this.setStyle("-fx-background-color: " + PRIMARY_BG + ";");
        this.setSpacing(16);
        this.setPadding(new Insets(16));

        this.getChildren().addAll(
                createTitle(),
                createMetricsGrid());

        startUptimeCounter();
    }

    /**
     * Update dashboard with new system health snapshot.
     */
    public void update(SystemHealthSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        checkCount++;

        updateMetrics(snapshot);
        lastCheckLabel.setText(TIME_FORMATTER.format(snapshot.getTimestamp()));
    }

    private @NotNull Label createTitle() {
        Label title = new Label("📊 System Health Dashboard");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");
        return title;
    }

    private @NotNull VBox createMetricsGrid() {
        metricsGrid.setHgap(16);
        metricsGrid.setVgap(12);
        metricsGrid.setPadding(new Insets(12));
        metricsGrid.setStyle("-fx-background-color: " + SECONDARY_BG + "; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8;");

        // Row 0: Status metrics
        metricsGrid.add(createMetricCard("System Status", systemStatusLabel, ACCENT_COLOR), 0, 0);
        metricsGrid.add(createMetricCard("Uptime", uptimeLabel, HEALTHY_COLOR), 1, 0);
        metricsGrid.add(createMetricCard("Check Frequency", checkFrequencyLabel, ACCENT_COLOR), 2, 0);

        // Row 1: Health metrics
        metricsGrid.add(createMetricCard("Healthy Components", healthyCountLabel, HEALTHY_COLOR), 0, 1);
        metricsGrid.add(createMetricCard("Issues Found", issueCountLabel, FAILED_COLOR), 1, 1);
        metricsGrid.add(createMetricCard("Last Check", lastCheckLabel, TEXT_SECONDARY), 2, 1);

        GridPane.setHgrow(metricsGrid, Priority.ALWAYS);

        VBox container = new VBox(metricsGrid);
        container.setStyle("-fx-background-color: " + PRIMARY_BG + ";");
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private @NotNull VBox createMetricCard(String title, @NotNull Label valueLabel, String accentColor) {
        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setPrefHeight(100);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: " + CARD_BG + "; " +
                "-fx-border-color: " + accentColor + "; " +
                "-fx-border-width: 2 0 0 0; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;");
        card.setAlignment(Pos.TOP_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold;");

        valueLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold;");
        valueLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private void updateMetrics(@NotNull SystemHealthSnapshot snapshot) {
        // System status
        String statusColor = getStatusColor(snapshot.getOverallStatus());
        systemStatusLabel.setText(snapshot.getOverallStatus().getDisplayName());
        systemStatusLabel.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold;");

        // Check frequency
        long elapsed = System.currentTimeMillis() - startTime;
        double checksPerSecond = checkCount > 0 ? (checkCount / (elapsed / 1000.0)) : 0;
        checkFrequencyLabel.setText(String.format("%.2f checks/sec", checksPerSecond));

        // Count healthy vs issues
        int healthyCount = 0;
        int issueCount = 0;

        for (var component : getAllComponents(snapshot)) {
            if (component.getStatus() == ComponentStatus.HEALTHY) {
                healthyCount++;
            } else if (component.getStatus().hasIssue()) {
                issueCount++;
            }
        }

        healthyCountLabel.setText(healthyCount + " / 9");
        healthyCountLabel.setStyle("-fx-text-fill: " + HEALTHY_COLOR + "; -fx-font-weight: bold;");

        String issueColor = issueCount > 0 ? FAILED_COLOR : HEALTHY_COLOR;
        issueCountLabel.setText(issueCount + " found");
        issueCountLabel.setStyle("-fx-text-fill: " + issueColor + "; -fx-font-weight: bold;");
    }

    private void startUptimeCounter() {
        Timeline uptimeTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long elapsed = System.currentTimeMillis() - startTime;
            long hours = (elapsed / 3600000) % 24;
            long minutes = (elapsed / 60000) % 60;
            long seconds = (elapsed / 1000) % 60;

            String uptime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            uptimeLabel.setText(uptime);
        }));
        uptimeTimer.setCycleCount(Timeline.INDEFINITE);
        uptimeTimer.play();
    }

    @Contract("_ -> new")
    private @NotNull @Unmodifiable List<ComponentHealth> getAllComponents(@NotNull SystemHealthSnapshot snapshot) {
        return java.util.List.of(
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

    private String getStatusColor(ComponentStatus status) {
        if (status == null)
            return "#6b7280";
        return switch (status) {
            case HEALTHY -> HEALTHY_COLOR;
            case DEGRADED -> DEGRADED_COLOR;
            case WARNING -> WARNING_COLOR;
            case FAILED -> FAILED_COLOR;
            case DISABLED, UNKNOWN -> "#6b7280";
        };
    }
}
