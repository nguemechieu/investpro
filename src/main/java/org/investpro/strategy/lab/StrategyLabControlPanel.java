package org.investpro.strategy.lab;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Professional monitoring panel for the {@link BacktestScheduler}.
 *
 * <p>Displays live stats (queue depth, worker utilisation, throughput,
 * memory, CPU) and provides action buttons (Pause / Resume / Cancel all /
 * Retry failed / Worker slider).
 *
 * <p>All UI state is updated via a {@link ThrottledUIUpdater} so the
 * JavaFX thread is never spammed.
 *
 * <h2>Integration</h2>
 * Add this panel as a tab inside {@code StrategyLabPanel}:
 * <pre>
 *   Tab controlTab = new Tab("⚙ Scheduler", new StrategyLabControlPanel());
 * </pre>
 */
@Slf4j
public final class StrategyLabControlPanel extends BorderPane {

    // ─── Colour palette (matches StrategyLabPanel) ────────────────────────
    private static final String BG_PANEL   = "#0a0e27";
    private static final String BG_CARD    = "#111827";
    private static final String CLR_ACCENT = "#3b82f6";
    private static final String CLR_TEXT   = "#e0e7ff";
    private static final String CLR_MUTED  = "#94a3b8";
    private static final String CLR_GREEN  = "#22c55e";
    private static final String CLR_RED    = "#ef4444";
    private static final String CLR_YELLOW = "#f59e0b";
    private static final String CLR_BORDER = "#1e293b";

    // ─── Live metrics labels ───────────────────────────────────────────────
    private Label queuedLabel;
    private Label runningLabel;
    private Label completedLabel;
    private Label failedLabel;
    private Label cancelledLabel;
    private Label workerLabel;
    private Label avgExecLabel;
    private Label throughputLabel;
    private Label heapLabel;
    private Label cpuLabel;

    private ProgressBar workerBar;
    private ProgressBar heapBar;
    private ProgressBar cpuBar;

    // ─── Job table ─────────────────────────────────────────────────────────
    private TableView<BacktestJob> jobTable;

    // ─── Scheduler reference ───────────────────────────────────────────────
    private final BacktestScheduler scheduler;
    private final ThrottledUIUpdater<SchedulerStats> statsUpdater;

    // ─── Construction ──────────────────────────────────────────────────────

    public StrategyLabControlPanel() {
        this(BacktestScheduler.getInstance());
    }

    public StrategyLabControlPanel(BacktestScheduler scheduler) {
        this.scheduler = scheduler;
        this.statsUpdater = new ThrottledUIUpdater<>(this::applyStats);

        setStyle("-fx-background-color: " + BG_PANEL + ";");
        setPadding(new Insets(10));

        setTop(buildHeader());
        setCenter(buildContent());
        setBottom(buildActionBar());

        // Subscribe to scheduler updates
        scheduler.addStatsListener(statsUpdater::enqueue);

        // Initial paint with current stats
        applyStats(scheduler.getStats());
    }

    // ─── Build UI ──────────────────────────────────────────────────────────

    private VBox buildHeader() {
        Label title = new Label("⚙  Strategy Lab Scheduler Monitor");
        title.setStyle("-fx-text-fill: " + CLR_ACCENT + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        VBox box = new VBox(title);
        box.setPadding(new Insets(0, 0, 8, 0));
        box.setStyle("-fx-border-color: " + CLR_BORDER + "; -fx-border-width: 0 0 1 0;");
        return box;
    }

    private SplitPane buildContent() {
        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + BG_PANEL + ";");
        split.getItems().addAll(buildStatsCard(), buildJobTable());
        split.setDividerPositions(0.35);
        return split;
    }

    private ScrollPane buildStatsCard() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: " + BG_CARD + "; -fx-border-color: " + CLR_BORDER
                + "; -fx-border-radius: 4;");

        // ── Queue metrics
        box.getChildren().add(sectionLabel("📋 Queue"));
        queuedLabel    = metricLabel("Queued:    0");
        runningLabel   = metricLabel("Running:   0");
        completedLabel = metricLabel("Completed: 0");
        failedLabel    = metricLabel("Failed:    0");
        cancelledLabel = metricLabel("Cancelled: 0");
        box.getChildren().addAll(queuedLabel, runningLabel, completedLabel, failedLabel, cancelledLabel);

        box.getChildren().add(separator());

        // ── Worker utilisation
        box.getChildren().add(sectionLabel("👷 Workers"));
        workerLabel = metricLabel("Workers: 0 / 0");
        workerBar   = progressBar(CLR_ACCENT);
        box.getChildren().addAll(workerLabel, workerBar);

        box.getChildren().add(separator());

        // ── Performance
        box.getChildren().add(sectionLabel("⚡ Performance"));
        avgExecLabel   = metricLabel("Avg exec: —");
        throughputLabel = metricLabel("Throughput: —");
        box.getChildren().addAll(avgExecLabel, throughputLabel);

        box.getChildren().add(separator());

        // ── Resources
        box.getChildren().add(sectionLabel("🖥️ Resources"));
        heapLabel = metricLabel("Heap: —");
        heapBar   = progressBar(CLR_GREEN);
        cpuLabel  = metricLabel("CPU: —");
        cpuBar    = progressBar(CLR_YELLOW);
        box.getChildren().addAll(heapLabel, heapBar, cpuLabel, cpuBar);

        // ── Worker count slider
        box.getChildren().add(separator());
        box.getChildren().add(sectionLabel("🔧 Max Workers"));
        Slider workerSlider = new Slider(1, Runtime.getRuntime().availableProcessors() * 2, scheduler.getMaxWorkers());
        workerSlider.setBlockIncrement(1);
        workerSlider.setMajorTickUnit(1);
        workerSlider.setMinorTickCount(0);
        workerSlider.setSnapToTicks(true);
        workerSlider.setShowTickLabels(true);
        workerSlider.setStyle("-fx-control-inner-background: " + BG_CARD + ";");
        Label sliderValueLabel = metricLabel("Current: " + (int) workerSlider.getValue());
        workerSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int n = newV.intValue();
            sliderValueLabel.setText("Current: " + n);
            scheduler.setMaxWorkers(n);
        });
        box.getChildren().addAll(workerSlider, sliderValueLabel);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background: " + BG_PANEL + ";");
        return scroll;
    }

    private VBox buildJobTable() {
        Label tableTitle = new Label("Active & Recent Jobs");
        tableTitle.setStyle("-fx-text-fill: " + CLR_MUTED + "; -fx-font-size: 11px;");

        jobTable = new TableView<>();
        jobTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        jobTable.setStyle("-fx-background-color: " + BG_CARD + "; -fx-control-inner-background: " + BG_CARD + ";");
        jobTable.setPlaceholder(new Label("No jobs submitted yet"));

        TableColumn<BacktestJob, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getJobId().substring(0, 8)));
        idCol.setPrefWidth(80);

        TableColumn<BacktestJob, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getRequest().getStrategyName()));

        TableColumn<BacktestJob, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getRequest().getSymbol()
                        + " " + cd.getValue().getRequest().getTimeframe().getCode()));
        symbolCol.setPrefWidth(100);

        TableColumn<BacktestJob, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getPriority().name()));
        priorityCol.setPrefWidth(90);

        TableColumn<BacktestJob, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStatus().name()));
        statusCol.setPrefWidth(90);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-background-color: transparent;");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    String color = switch (item) {
                        case "RUNNING"   -> CLR_ACCENT;
                        case "COMPLETED" -> CLR_GREEN;
                        case "FAILED"    -> CLR_RED;
                        case "CANCELLED" -> CLR_YELLOW;
                        default          -> CLR_MUTED;
                    };
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + color
                            + "; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<BacktestJob, String> durationCol = new TableColumn<>("ms");
        durationCol.setCellValueFactory(cd -> {
            long ms = cd.getValue().getDurationMs();
            return new SimpleStringProperty(ms < 0 ? "—" : String.valueOf(ms));
        });
        durationCol.setPrefWidth(70);

        // Cancel button column
        TableColumn<BacktestJob, Void> cancelCol = new TableColumn<>("⛔");
        cancelCol.setPrefWidth(50);
        cancelCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-padding: 2 6; -fx-font-size: 10px; -fx-background-color: #450a0a; "
                        + "-fx-text-fill: " + CLR_RED + "; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    BacktestJob job = getTableView().getItems().get(getIndex());
                    if (job != null && !job.isTerminal()) {
                        scheduler.cancel(job.getJobId());
                        refreshJobTable();
                    }
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setStyle("-fx-background-color: transparent;");
                if (empty) {
                    setGraphic(null);
                } else {
                    BacktestJob job = getTableView().getItems().get(getIndex());
                    setGraphic(job != null && !job.isTerminal() ? btn : null);
                }
            }
        });

        jobTable.getColumns().setAll(idCol, strategyCol, symbolCol, priorityCol, statusCol, durationCol, cancelCol);

        VBox wrapper = new VBox(5, tableTitle, jobTable);
        wrapper.setPadding(new Insets(8));
        wrapper.setStyle("-fx-background-color: " + BG_PANEL + ";");
        VBox.setVgrow(jobTable, Priority.ALWAYS);
        return wrapper;
    }

    private HBox buildActionBar() {
        Button pauseBtn  = actionButton("⏸ Pause All",    () -> { scheduler.pause();         refreshStatus(); });
        Button resumeBtn = actionButton("▶ Resume All",   () -> { scheduler.resume();        refreshStatus(); });
        Button clearBtn  = actionButton("🗑 Clear Queue", () -> { scheduler.cancelAllQueued(); refreshJobTable(); });
        Button refreshBtn = actionButton("🔄 Refresh",    this::refreshStatus);

        HBox bar = new HBox(8, pauseBtn, resumeBtn, clearBtn, refreshBtn);
        bar.setPadding(new Insets(8, 0, 0, 0));
        bar.setStyle("-fx-border-color: " + CLR_BORDER + "; -fx-border-width: 1 0 0 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ─── Apply stats ───────────────────────────────────────────────────────

    /**
     * Applies a {@link SchedulerStats} snapshot to all UI labels and progress bars.
     * Must be called on the JavaFX Application Thread.
     */
    private void applyStats(SchedulerStats s) {
        queuedLabel.setText(    "Queued:    " + s.getQueued());
        runningLabel.setText(   "Running:   " + s.getRunning());
        completedLabel.setText( "Completed: " + s.getCompleted());
        failedLabel.setText(    "Failed:    " + s.getFailed());
        cancelledLabel.setText( "Cancelled: " + s.getCancelled());

        workerLabel.setText("Workers: " + s.getActiveWorkers() + " / " + s.getMaxWorkers());
        workerBar.setProgress(s.getWorkerUtilization());
        setBarColor(workerBar, s.getWorkerUtilization(), CLR_ACCENT, CLR_YELLOW, CLR_RED);

        avgExecLabel.setText(    "Avg exec:   " + (s.getAvgExecTimeMs() > 0
                ? String.format("%.0f ms", s.getAvgExecTimeMs()) : "—"));
        throughputLabel.setText( "Throughput: " + String.format("%.2f /s", s.getThroughputPerSec()));

        heapLabel.setText("Heap: " + String.format("%.0f / %.0f MiB", s.getHeapUsedMiB(), s.getHeapMaxMiB()));
        heapBar.setProgress(s.getHeapUtilization());
        setBarColor(heapBar, s.getHeapUtilization(), CLR_GREEN, CLR_YELLOW, CLR_RED);

        if (s.getSystemCpuLoad() >= 0) {
            cpuLabel.setText("CPU: " + String.format("%.0f%%", s.getSystemCpuLoad() * 100));
            cpuBar.setProgress(s.getSystemCpuLoad());
            setBarColor(cpuBar, s.getSystemCpuLoad(), CLR_GREEN, CLR_YELLOW, CLR_RED);
        } else {
            cpuLabel.setText("CPU: n/a");
            cpuBar.setProgress(0);
        }

        refreshJobTable();
    }

    private void refreshStatus() {
        applyStats(scheduler.getStats());
    }

    private void refreshJobTable() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshJobTable);
            return;
        }
        Collection<BacktestJob> jobs = scheduler.getAllJobs();
        jobTable.setItems(FXCollections.observableArrayList(jobs));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** Shuts down the throttled updater and unsubscribes from the scheduler. */
    public void dispose() {
        statsUpdater.shutdown();
        scheduler.removeStatsListener(statsUpdater::enqueue);
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + CLR_ACCENT + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private static Label metricLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + CLR_TEXT + "; -fx-font-size: 11px;");
        return l;
    }

    private static ProgressBar progressBar(String color) {
        ProgressBar pb = new ProgressBar(0);
        pb.setPrefWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: " + color + ";");
        return pb;
    }

    private static Separator separator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + CLR_BORDER + ";");
        return s;
    }

    private static Button actionButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-padding: 5 12; -fx-font-size: 11px; -fx-background-color: #1e293b; "
                + "-fx-text-fill: " + CLR_TEXT + "; -fx-border-color: #334155; "
                + "-fx-border-radius: 3; -fx-cursor: hand;");
        b.setOnAction(e -> { if (action != null) action.run(); });
        return b;
    }

    private static void setBarColor(ProgressBar bar, double fraction, String low, String mid, String high) {
        String color = fraction < 0.5 ? low : fraction < 0.80 ? mid : high;
        bar.setStyle("-fx-accent: " + color + ";");
    }
}
