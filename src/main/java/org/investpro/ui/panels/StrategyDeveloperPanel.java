package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.persistence.repository.HistoricalDataRepository;
import org.investpro.persistence.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;
import org.investpro.strategy.management.StrategyAssignmentManager;
import org.investpro.strategy.user.UserStrategyLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Strategy Developer Panel for managing user-developed strategies.
 * <p>
 * Features:
 * - Lists all loaded user strategies in a table
 * - Columns: ID, Name, Source JAR, Warmup Bars, Valid, Status
 * - Buttons: Reload Strategies, Validate Selected, Open Folder, Backtest, Disable
 * - Output log for operation results
 * - Safety: No live trading, backtesting only
 * <p>
 * User strategies are allowed to generate StrategySignal objects only.
 * They should not directly place live orders.
 */
@Slf4j
@Getter
@Setter
public class StrategyDeveloperPanel extends VBox {

    private static final String STRATEGIES_DIR = "strategies";

    private final SystemCore systemCore;
    private final HistoricalDataRepository historicalDataRepository;

    private TableView<UserStrategyRow> strategiesTable;
    private TextArea outputLog;

    private Button reloadButton;
    private Button validateButton;
    private Button openFolderButton;
    private Button backtestButton;
    private Button disableButton;
    private Button refreshButton;

    private UserStrategyLoader currentLoader;

    public StrategyDeveloperPanel(@Nullable SystemCore systemCore) {
        this.systemCore = systemCore;
        this.historicalDataRepository = resolveHistoricalDataRepository(systemCore);

        initializePanel();
    }

    private HistoricalDataRepository resolveHistoricalDataRepository(@Nullable SystemCore systemCore) {
        try {
            if (systemCore != null && systemCore.getHistoricalDataRepository() != null) {
                return systemCore.getHistoricalDataRepository();
            }
        } catch (Exception exception) {
            log.warn("Unable to resolve historical repository from SystemCore: {}", exception.getMessage());
        }

        return HistoricalDataRepositoryImpl.getInstance();
    }

    private void initializePanel() {
        setPrefHeight(600);
        setPadding(new Insets(15));
        setSpacing(10);
        setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;");

        Label titleLabel = new Label("User Strategy Developer");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #00ff00;");

        Label descLabel = new Label("Manage, validate, and backtest your custom trading strategies.");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");

        createStrategiesTable();
        HBox buttonsBar = createButtonsBar();
        createOutputLog();

        Label outputLabel = new Label("Output Log:");
        outputLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        VBox.setVgrow(strategiesTable, Priority.ALWAYS);
        VBox.setVgrow(outputLog, Priority.SOMETIMES);

        VBox userStrategyContent = new VBox(10,
                strategiesTable,
                buttonsBar,
                outputLabel,
                outputLog
        );
        userStrategyContent.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(userStrategyContent, Priority.ALWAYS);

        TabPane lifecycleTabs = new TabPane();
        lifecycleTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        lifecycleTabs.getTabs().addAll(
                new Tab("User Strategies", userStrategyContent),
                createLifecycleTab("Assignments", null),
                createLifecycleTab("Paper Trading", StrategyLifecycleStatus.PAPER_TRADING),
                createLifecycleTab("Live Trading", StrategyLifecycleStatus.LIVE_ACTIVE),
                createLifecycleTab("Degraded Strategies", StrategyLifecycleStatus.DEGRADED),
                createReplacementCandidatesTab(),
                new Tab("AI Reasoning", new AIReasoningPanel())
        );
        VBox.setVgrow(lifecycleTabs, Priority.ALWAYS);

        getChildren().addAll(
                titleLabel,
                descLabel,
                new Separator(),
                lifecycleTabs
        );

        try {
            LocalizationService.applyTranslations(this);
        } catch (Exception exception) {
            log.debug("Localization skipped for StrategyDeveloperPanel: {}", exception.getMessage());
        }

        loadStrategiesIntoTable();
    }

    private Tab createLifecycleTab(String title, @Nullable StrategyLifecycleStatus status) {
        TableView<StrategyLifecycleRecord> table = buildLifecycleTable();
        List<StrategyLifecycleRecord> records = StrategyAssignmentManager.getInstance().getAllRecords();
        if (status != null) {
            records = records.stream()
                    .filter(record -> record.getLifecycleStatus() == status)
                    .toList();
        }
        table.setItems(FXCollections.observableArrayList(records));
        return new Tab(title, table);
    }

    private Tab createReplacementCandidatesTab() {
        TableView<StrategyLifecycleRecord> table = buildLifecycleTable();
        table.setItems(FXCollections.observableArrayList(
                StrategyAssignmentManager.getInstance().getAllRecords().stream()
                        .filter(StrategyLifecycleRecord::needsReplacement)
                        .toList()));
        return new Tab("Replacement Candidates", table);
    }

    @SuppressWarnings("unchecked")
    private TableView<StrategyLifecycleRecord> buildLifecycleTable() {
        TableView<StrategyLifecycleRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StrategyLifecycleRecord, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(new PropertyValueFactory<>("strategyName"));

        TableColumn<StrategyLifecycleRecord, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));

        TableColumn<StrategyLifecycleRecord, String> timeframeCol = new TableColumn<>("Timeframe");
        timeframeCol.setCellValueFactory(new PropertyValueFactory<>("timeframe"));

        TableColumn<StrategyLifecycleRecord, Number> aiScoreCol = new TableColumn<>("AI Score");
        aiScoreCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(
                cell.getValue().getAiConfidence() * 100.0));

        TableColumn<StrategyLifecycleRecord, String> healthCol = new TableColumn<>("Health");
        healthCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getLastHealthReport() == null
                        ? ""
                        : cell.getValue().getLastHealthReport().getHealthLevel().name()));

        TableColumn<StrategyLifecycleRecord, Number> winRateCol = new TableColumn<>("Win Rate");
        winRateCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(
                cell.getValue().getLastHealthReport() == null
                        ? 0.0
                        : cell.getValue().getLastHealthReport().getWinRate()));

        TableColumn<StrategyLifecycleRecord, Number> profitFactorCol = new TableColumn<>("Profit Factor");
        profitFactorCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(
                cell.getValue().getLastHealthReport() == null
                        ? 0.0
                        : cell.getValue().getLastHealthReport().getProfitFactor()));

        TableColumn<StrategyLifecycleRecord, Number> drawdownCol = new TableColumn<>("Drawdown");
        drawdownCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(
                cell.getValue().getLastHealthReport() == null
                        ? 0.0
                        : cell.getValue().getLastHealthReport().getMaxDrawdown()));

        TableColumn<StrategyLifecycleRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getLifecycleStatus() == null ? "" : cell.getValue().getLifecycleStatus().name()));

        table.getColumns().setAll(strategyCol, symbolCol, timeframeCol, aiScoreCol,
                healthCol, winRateCol, profitFactorCol, drawdownCol, statusCol);
        return table;
    }

    private void createStrategiesTable() {
        strategiesTable = new TableView<>();
        strategiesTable.setPrefHeight(250);
        strategiesTable.setStyle("-fx-control-inner-background: #2a2a2a; -fx-text-fill: #ffffff;");

        TableColumn<UserStrategyRow, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        idColumn.setPrefWidth(170);

        TableColumn<UserStrategyRow, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameColumn.setPrefWidth(180);

        TableColumn<UserStrategyRow, String> jarColumn = new TableColumn<>("Source JAR");
        jarColumn.setCellValueFactory(cellData -> cellData.getValue().sourceJarProperty());
        jarColumn.setPrefWidth(170);

        TableColumn<UserStrategyRow, Number> warmupColumn = new TableColumn<>("Warmup Bars");
        warmupColumn.setCellValueFactory(cellData -> cellData.getValue().warmupBarsProperty());
        warmupColumn.setPrefWidth(110);

        TableColumn<UserStrategyRow, String> validColumn = new TableColumn<>("Valid");
        validColumn.setCellValueFactory(cellData -> cellData.getValue().validProperty());
        validColumn.setPrefWidth(80);

        TableColumn<UserStrategyRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusColumn.setPrefWidth(180);

        strategiesTable.getColumns().setAll(
                idColumn,
                nameColumn,
                jarColumn,
                warmupColumn,
                validColumn,
                statusColumn
        );

        strategiesTable.setPlaceholder(new Label("No user strategies loaded yet. Add strategy JARs to the strategies folder."));
    }

    private @NotNull HBox createButtonsBar() {
        HBox buttonsBar = new HBox(10);
        buttonsBar.setPadding(new Insets(10));
        buttonsBar.setStyle("-fx-background-color: #2a2a2a; -fx-border-radius: 5; -fx-background-radius: 5;");
        buttonsBar.setAlignment(Pos.CENTER_LEFT);

        reloadButton = createButton("Reload Strategies", "#00aa00", event -> onReloadStrategies());
        validateButton = createButton("Validate Selected", "#0099ff", event -> onValidateSelected());
        openFolderButton = createButton("Open Folder", "#ff9900", event -> onOpenFolder());
        backtestButton = createButton("Backtest Selected", "#ff6600", event -> onBacktestSelected());
        disableButton = createButton("Disable Selected", "#ff3333", event -> onDisableSelected());
        refreshButton = createButton("Refresh", "#999999", event -> loadStrategiesIntoTable());

        buttonsBar.getChildren().addAll(
                reloadButton,
                validateButton,
                openFolderButton,
                backtestButton,
                disableButton,
                createVerticalSeparator(),
                refreshButton
        );

        return buttonsBar;
    }

    private @NotNull Button createButton(
            String text,
            String color,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler
    ) {
        Button button = new Button(text);
        button.setStyle(String.format(
                "-fx-font-size: 12px; " +
                        "-fx-padding: 8 15 8 15; " +
                        "-fx-background-color: %s; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4; " +
                        "-fx-cursor: hand;",
                color
        ));
        button.setOnAction(handler);
        return button;
    }

    private Separator createVerticalSeparator() {
        Separator separator = new Separator();
        separator.setOrientation(Orientation.VERTICAL);
        return separator;
    }

    private void createOutputLog() {
        outputLog = new TextArea();
        outputLog.setPrefHeight(150);
        outputLog.setEditable(false);
        outputLog.setWrapText(true);
        outputLog.setStyle(
                "-fx-control-inner-background: #1a1a1a; " +
                        "-fx-text-fill: #00ff00; " +
                        "-fx-font-family: 'Courier New';"
        );
    }

    private void loadStrategiesIntoTable() {
        runBackground("load-strategies", () -> {
            appendLog("Loading user strategies from " + STRATEGIES_DIR + "/ directory...");

            UserStrategyLoader loader = new UserStrategyLoader(STRATEGIES_DIR);
            currentLoader = loader;

            int loadedCount = safeLoadIntoRegistry(loader);
            appendLog("Strategy loader finished. Newly loaded: " + loadedCount);

            List<TradingStrategy> userStrategies = safeGetUserStrategies();
            ObservableList<UserStrategyRow> rows = FXCollections.observableArrayList();

            for (TradingStrategy strategy : userStrategies) {
                rows.add(toRow(strategy));
            }

            Platform.runLater(() -> {
                strategiesTable.setItems(rows);
                appendLog("Loaded " + rows.size() + " user strategy row(s) into table.");
            });
        });
    }

    private int safeLoadIntoRegistry(UserStrategyLoader loader) {
        try {
            return loader.loadIntoRegistry();
        } catch (Exception exception) {
            appendLog("Loader error: " + exception.getMessage());
            log.error("Failed to load user strategies", exception);
            return 0;
        }
    }

    private List<TradingStrategy> safeGetUserStrategies() {
        try {
            return StrategyRegistry.getInstance().getUserStrategies();
        } catch (Exception exception) {
            appendLog("Registry error: unable to get user strategies: " + exception.getMessage());
            log.error("Unable to get user strategies", exception);
            return List.of();
        }
    }

    private UserStrategyRow toRow(TradingStrategy strategy) {
        UserStrategyRow row = new UserStrategyRow();

        row.setId(safeStrategyId(strategy));
        row.setName(safeStrategyName(strategy));
        row.setWarmupBars(safeWarmupBars(strategy));
        row.setValid("Unknown");

        if (strategy instanceof UserStrategyAdapter adapter) {
            row.setSourceJar(safeText(adapter.getDescription()));
            row.setStatus(adapter.isEnabled() ? "Loaded" : "Disabled");
        } else {
            row.setSourceJar("Registry");
            row.setStatus("Loaded");
        }

        return row;
    }

    private void onReloadStrategies() {
        runBackground("reload-strategies", () -> {
            appendLog("[Reload] Scanning " + STRATEGIES_DIR + "/ for strategy JARs...");

            UserStrategyLoader loader = new UserStrategyLoader(STRATEGIES_DIR);
            currentLoader = loader;

            int loadedCount = safeLoadIntoRegistry(loader);
            appendLog("[Reload] Loaded " + loadedCount + " new user strategy object(s).");

            Platform.runLater(this::loadStrategiesIntoTable);
        });
    }

    private void onValidateSelected() {
        UserStrategyRow selected = strategiesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            appendLog("[Validate] Please select a strategy first.");
            return;
        }

        runBackground("validate-strategy", () -> {
            appendLog("[Validate] Validating strategy: " + selected.getName());

            Optional<TradingStrategy> strategy = findStrategy(selected.getId());

            if (strategy.isEmpty()) {
                appendLog("[Validate] ERROR: Strategy not found in registry.");
                Platform.runLater(() -> {
                    selected.setValid("No");
                    selected.setStatus("Missing");
                    strategiesTable.refresh();
                });
                return;
            }

            ValidationResult result = validateStrategy(strategy.get());

            appendLog("[Validate] " + result.message());

            Platform.runLater(() -> {
                selected.setValid(result.valid() ? "Yes" : "No");
                selected.setStatus(result.valid() ? "Validated" : "Invalid");
                strategiesTable.refresh();
            });
        });
    }

    private Optional<TradingStrategy> findStrategy(String id) {
        try {
            return StrategyRegistry.getInstance().findById(id);
        } catch (Exception exception) {
            appendLog("[Registry] ERROR: " + exception.getMessage());
            log.error("Failed to find strategy {}", id, exception);
            return Optional.empty();
        }
    }

    private ValidationResult validateStrategy(TradingStrategy strategy) {
        if (strategy == null) {
            return new ValidationResult(false, "Strategy is null.");
        }

        String id = safeStrategyId(strategy);
        String name = safeStrategyName(strategy);
        int warmupBars = safeWarmupBars(strategy);

        if (id.isBlank()) {
            return new ValidationResult(false, "Invalid strategy: ID is blank.");
        }

        if (name.isBlank()) {
            return new ValidationResult(false, "Invalid strategy: name is blank.");
        }

        if (warmupBars <= 0) {
            return new ValidationResult(false, "Invalid strategy: warmup bars must be greater than 0.");
        }

        try {
            strategy.validateConfiguration();
        } catch (Exception exception) {
            return new ValidationResult(false, "Configuration validation failed: " + exception.getMessage());
        }

        return new ValidationResult(
                true,
                "Validation passed. ID=" + id + ", Name=" + name + ", WarmupBars=" + warmupBars
        );
    }

    private void onOpenFolder() {
        try {
            Path strategiesPath = Paths.get(STRATEGIES_DIR).toAbsolutePath();
            File folder = strategiesPath.toFile();

            if (!folder.exists()) {
                appendLog("[OpenFolder] Creating strategies/ directory...");
                boolean created = folder.mkdirs();

                if (!created && !folder.exists()) {
                    appendLog("[OpenFolder] ERROR: Unable to create strategies folder.");
                    return;
                }
            }

            appendLog("[OpenFolder] Opening: " + strategiesPath);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            } else {
                appendLog("[OpenFolder] Desktop integration is not available on this system.");
            }

        } catch (Exception exception) {
            appendLog("[OpenFolder] ERROR: " + exception.getMessage());
            log.error("Error opening strategies folder", exception);
        }
    }

    private void onBacktestSelected() {
        UserStrategyRow selected = strategiesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            appendLog("[Backtest] Please select a strategy first.");
            return;
        }

        appendLog("[Backtest] Opening backtesting panel for: " + selected.getName());

        try {
            BacktestingPanel panel = new BacktestingPanel(

                    historicalDataRepository,
                    systemCore
            );

            Stage backtestStage = new Stage();
            backtestStage.setTitle("Backtest: " + selected.getName());
            backtestStage.setWidth(1200);
            backtestStage.setHeight(850);
            backtestStage.setScene(new Scene(panel));
            backtestStage.show();

            appendLog("[Backtest] Backtest window opened.");

        } catch (Exception exception) {
            appendLog("[Backtest] ERROR: " + exception.getMessage());
            log.error("Error opening backtest panel", exception);
        }
    }

    private void onDisableSelected() {
        UserStrategyRow selected = strategiesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            appendLog("[Disable] Please select a strategy first.");
            return;
        }

        runBackground("disable-strategy", () -> {
            appendLog("[Disable] Disabling strategy: " + selected.getId());

            Optional<TradingStrategy> strategy = findStrategy(selected.getId());

            if (strategy.isEmpty()) {
                appendLog("[Disable] ERROR: Strategy not found in registry.");
                return;
            }

            TradingStrategy tradingStrategy = strategy.get();

            if (tradingStrategy instanceof UserStrategyAdapter adapter) {
                adapter.setEnabled(false);
                appendLog("[Disable] Strategy disabled: " + selected.getId());

                Platform.runLater(() -> {
                    selected.setStatus("Disabled");
                    strategiesTable.refresh();
                });
            } else {
                appendLog("[Disable] Selected strategy is not a user strategy adapter.");
            }
        });
    }

    private void runBackground(String name, Runnable task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                appendLog("[" + name + "] ERROR: " + exception.getMessage());
                log.error("Background task failed: {}", name, exception);
            }
        });

        thread.setName("strategy-developer-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + timestamp + "] " + message + "\n";

        if (outputLog == null) {
            log.info(message);
            return;
        }

        if (Platform.isFxApplicationThread()) {
            outputLog.appendText(line);
        } else {
            Platform.runLater(() -> outputLog.appendText(line));
        }
    }

    private String safeStrategyId(TradingStrategy strategy) {
        if (strategy == null) {
            return "";
        }

        try {
            String id = strategy.getId().toString();
            return id == null ? "" : id.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeStrategyName(TradingStrategy strategy) {
        if (strategy == null) {
            return "";
        }

        try {
            String name = strategy.getName().toString();
            return name == null || name.isBlank() ? safeStrategyId(strategy) : name.trim();
        } catch (Exception ignored) {
            return safeStrategyId(strategy);
        }
    }

    private int safeWarmupBars(TradingStrategy strategy) {
        if (strategy == null) {
            return 0;
        }

        try {
            return Math.max(0, strategy.requiredWarmupBars());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private @NotNull String safeText(String value) {
        return value == null || value.isBlank() ? "User Strategy" : value.trim();
    }

    private record ValidationResult(boolean valid, String message) {
    }

    /**
     * Data model for displaying strategy info in TableView.
     */
    public static class UserStrategyRow {

        private final SimpleStringProperty id = new SimpleStringProperty("");
        private final SimpleStringProperty name = new SimpleStringProperty("");
        private final SimpleStringProperty sourceJar = new SimpleStringProperty("");
        private final SimpleIntegerProperty warmupBars = new SimpleIntegerProperty(0);
        private final SimpleStringProperty valid = new SimpleStringProperty("Unknown");
        private final SimpleStringProperty status = new SimpleStringProperty("Unknown");

        public SimpleStringProperty idProperty() {
            return id;
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public SimpleStringProperty sourceJarProperty() {
            return sourceJar;
        }

        public SimpleIntegerProperty warmupBarsProperty() {
            return warmupBars;
        }

        public SimpleStringProperty validProperty() {
            return valid;
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public String getId() {
            return id.get();
        }

        public void setId(String value) {
            id.set(value == null ? "" : value);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String value) {
            name.set(value == null ? "" : value);
        }

        public String getSourceJar() {
            return sourceJar.get();
        }

        public void setSourceJar(String value) {
            sourceJar.set(value == null ? "" : value);
        }

        public int getWarmupBars() {
            return warmupBars.get();
        }

        public void setWarmupBars(int value) {
            warmupBars.set(Math.max(0, value));
        }

        public String getValid() {
            return valid.get();
        }

        public void setValid(String value) {
            valid.set(value == null ? "Unknown" : value);
        }

        public String getStatus() {
            return status.get();
        }

        public void setStatus(String value) {
            status.set(value == null ? "Unknown" : value);
        }
    }
}
