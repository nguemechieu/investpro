package org.investpro.ui.panels;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;

/**
 * Live resource monitor panel with basic CPU, memory, and disk statistics.
 */
@Slf4j
public class ResourceMonitorPanel extends BorderPane {
    private static final DecimalFormat PERCENT = new DecimalFormat("0.0");
    private static final DecimalFormat MB = new DecimalFormat("0.0");

    private final Label cpuLabel = new Label("CPU: n/a");
    private final Label heapLabel = new Label("Heap: n/a");
    private final Label nonHeapLabel = new Label("Non-heap: n/a");
    private final Label diskLabel = new Label("Disk: n/a");
    private final Label processLabel = new Label("Uptime: n/a");
    private final TextArea detailsArea = new TextArea();
    private final Timeline refreshTimeline;

    public ResourceMonitorPanel() {
        initializeUi();
        refreshMetrics();
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> refreshMetrics()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void initializeUi() {
        setPadding(new Insets(12));
        setStyle("-fx-background-color: linear-gradient(to bottom, #0f172a, #111827);");

        Label title = new Label("Resource Monitor");
        title.setStyle("-fx-text-fill: #e5edf7; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("Live JVM and machine utilization overview");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        VBox header = new VBox(3, title, subtitle, new Separator());
        header.setPadding(new Insets(0, 0, 10, 0));
        setTop(header);

        GridPane metrics = new GridPane();
        metrics.setHgap(12);
        metrics.setVgap(10);
        metrics.setPadding(new Insets(8, 0, 8, 0));

        styleMetricLabel(cpuLabel);
        styleMetricLabel(heapLabel);
        styleMetricLabel(nonHeapLabel);
        styleMetricLabel(diskLabel);
        styleMetricLabel(processLabel);

        metrics.add(rowLabel("CPU"), 0, 0);
        metrics.add(cpuLabel, 1, 0);
        metrics.add(rowLabel("Heap"), 0, 1);
        metrics.add(heapLabel, 1, 1);
        metrics.add(rowLabel("Non-heap"), 0, 2);
        metrics.add(nonHeapLabel, 1, 2);
        metrics.add(rowLabel("Disk"), 0, 3);
        metrics.add(diskLabel, 1, 3);
        metrics.add(rowLabel("Process"), 0, 4);
        metrics.add(processLabel, 1, 4);

        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setStyle(
                "-fx-control-inner-background: #0b1220;"
                        + "-fx-font-family: 'Consolas', 'Monaco', monospace;"
                        + "-fx-font-size: 12px;"
                        + "-fx-text-fill: #dbeafe;");

        Button refreshButton = new Button("Refresh Now");
        refreshButton.setOnAction(event -> refreshMetrics());

        Button snapshotButton = new Button("Snapshot");
        snapshotButton.setOnAction(event -> detailsArea.appendText("Snapshot captured at %s%n".formatted(Instant.now())));

        HBox actions = new HBox(8, refreshButton, snapshotButton);
        actions.setPadding(new Insets(8, 0, 0, 0));

        VBox center = new VBox(10, metrics, actions, detailsArea);
        setCenter(center);
    }

    private Label rowLabel(String text) {
        Label label = new Label(text + ":");
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        return label;
    }

    private void styleMetricLabel(Label label) {
        label.setStyle("-fx-text-fill: #dbeafe; -fx-font-size: 12px;");
    }

    private void refreshMetrics() {
        try {
            double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            cpuLabel.setText(cpuLoad < 0 ? "CPU: n/a" : "CPU load: " + PERCENT.format(cpuLoad));

            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
            heapLabel.setText(formatMemory("Heap", heap));
            nonHeapLabel.setText(formatMemory("Non-heap", nonHeap));

            FileStore rootStore = null;
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                rootStore = FileSystems.getDefault().provider().getFileStore(root);
                break;
            }
            if (rootStore != null) {
                long total = rootStore.getTotalSpace();
                long usable = rootStore.getUsableSpace();
                long used = Math.max(0L, total - usable);
                double usedPct = total <= 0 ? 0.0 : (double) used / (double) total * 100.0;
                diskLabel.setText("Disk: %s used / %s total (%s%%)".formatted(
                        formatBytes(used), formatBytes(total), PERCENT.format(usedPct)));
            }

            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            processLabel.setText("Uptime: " + formatDuration(uptimeMillis));

            detailsArea.setText(String.join(System.lineSeparator(),
                    "Updated: " + Instant.now(),
                    "Available processors: " + Runtime.getRuntime().availableProcessors(),
                    "Max memory: " + formatBytes(Runtime.getRuntime().maxMemory()),
                    "Total memory: " + formatBytes(Runtime.getRuntime().totalMemory()),
                    "Free memory: " + formatBytes(Runtime.getRuntime().freeMemory())));
        } catch (Exception exception) {
            log.warn("Unable to refresh resource metrics", exception);
            detailsArea.setText("Resource metrics unavailable: " + exception.getMessage());
        }
    }

    private String formatMemory(String label, MemoryUsage usage) {
        if (usage == null) {
            return label + ": n/a";
        }
        return "%s: %s used / %s committed / %s max".formatted(
                label,
                formatBytes(usage.getUsed()),
                formatBytes(usage.getCommitted()),
                usage.getMax() < 0 ? "unbounded" : formatBytes(usage.getMax()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        double mb = bytes / 1024.0 / 1024.0;
        return MB.format(mb) + " MB";
    }

    private String formatDuration(long uptimeMillis) {
        long seconds = uptimeMillis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return "%02dh %02dm %02ds".formatted(hours, minutes, remainingSeconds);
    }
}
