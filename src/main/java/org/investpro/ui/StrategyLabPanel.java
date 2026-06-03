package org.investpro.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.local.grpc.AiGrpcHealthStatus;
import org.investpro.ai.local.grpc.LocalAiRuntimeService;
import org.investpro.core.SystemCore;
import org.investpro.data.CandleData;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategySelectionService;
import org.investpro.strategy.lab.StrategyConsensusResult;
import org.investpro.strategy.lab.StrategyLabControlPanel;
import org.investpro.strategy.lab.StrategyLabService;
import org.investpro.strategy.lab.StrategyLabSnapshot;
import org.investpro.strategy.lab.StrategyPerformanceReport;
import org.investpro.strategy.lab.StrategyVote;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.HistoricalDataPrefetcher;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JavaFX panel for InvestPro Strategy Lab.
 * <p>
 * Displays:
 * - Strategy testing controls
 * - Active assignment information
 * - Strategy rankings
 * - Voting / consensus results
 * - Consensus summary
 * - Async test status and logs
 */
@Getter
@Setter
@Slf4j
public class StrategyLabPanel extends BorderPane {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SystemCore systemCore;
    private final StrategyLabService labService;

    private String selectedSymbol = "EUR/USD";
    private Timeframe selectedTimeframe = Timeframe.H1;
    private volatile boolean running;
    private final AtomicLong refreshSequence = new AtomicLong();

    private ComboBox<TradePair> symbolCombo;
    private ComboBox<Timeframe> timeframeCombo;
    private ComboBox<String> strategyCombo;
    private TextField strategyFilterField;

    private Button testSelectedButton;
    private Button testAllStrategiesButton;
    private Button testAllTimeframesButton;
    private Button rankButton;
    private Button assignBestButton;
    private Button manualAssignButton;
    private Button unassignButton;
    private Button disableAssignmentButton;

    private Label activeAssignmentLabel;
    private Label activeStrategyLabel;
    private Label activeScoreLabel;
    private Label activeModeLabel;
    private Label activeAssignedAtLabel;

    private TableView<StrategyPerformanceReport> rankingTable;
    private TableView<StrategyVote> votingTable;

    private Label consensusLabel;
    private Label consensusConfidenceLabel;
    private Label selectedStrategyLabel;
    private Label buyVotesLabel;
    private Label sellVotesLabel;
    private Label holdVotesLabel;
    private Label consensusReasonLabel;
    private Label aiStatusLabel;
    private Label aiLatencyLabel;
    private Label aiCircuitLabel;
    private Label aiConservativeModeLabel;
    private Label aiLastErrorLabel;
    private Label aiReqRateLabel;

    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea logsArea;

    private StrategyLabSnapshot snapshot;
    private StrategyConsensusResult consensus;

    public StrategyLabPanel(@NotNull SystemCore systemCore) throws SQLException, ClassNotFoundException {
        this.systemCore = Objects.requireNonNull(systemCore, "systemCore must not be null");
        this.labService = systemCore.getLabService() == null
                ? StrategyLabService.getInstance()
                : systemCore.getLabService();

        initializeUI();
        LocalizationService.applyTranslations(this);
        refreshUI();
    }

    @SuppressWarnings("unused")
    private static final String BG_DEEP = "-fx-background-color: #07090f;";
    private static final String BG_PANEL = "#0a0e27";
    private static final String BG_CARD = "#111827";
    private static final String BG_CARD2 = "#1a1f35";
    private static final String BG_HEADER = "#0f172a";
    private static final String CLR_ACCENT = "#3b82f6";
    private static final String CLR_TEXT = "#e0e7ff";
    private static final String CLR_MUTED = "#94a3b8";
    private static final String CLR_GREEN = "#22c55e";
    private static final String CLR_RED = "#ef4444";
    private static final String CLR_YELLOW = "#f59e0b";
    private static final String CLR_BORDER = "#1e293b";

    private void initializeUI() throws SQLException, ClassNotFoundException {
        getStyleClass().add("strategy-lab-panel");
        setStyle("-fx-background-color: " + BG_PANEL + ";");

        setTop(createControlsSection());
        setCenter(createContentTabs());
        setBottom(createStatusSection());
        if (snapshot != null && snapshot.hasConsensusData()) {
            consensus = snapshot.getConsensus();

            if (snapshot.isConsensusReached()) {
                consensusLabel.setText("Consensus: " + Objects.requireNonNull(consensus).getConsensusSide());
            } else {
                consensusLabel.setText("Consensus: No strong consensus");
            }
        } else {
            consensusLabel.setText("Consensus: No data");
        }
    }

    private VBox createControlsSection() throws SQLException, ClassNotFoundException {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle(
                "-fx-background-color: " + BG_HEADER + "; " +
                        "-fx-border-color: " + CLR_BORDER + "; " +
                        "-fx-border-width: 0 0 1 0;");

        HBox row1 = new HBox(8);
        row1.setStyle("-fx-alignment: center-left;");

        Label symbolLabel = styledLabel("Symbol:");
        symbolCombo = new ComboBox<>();
        symbolCombo.setPrefWidth(170);
        styleCombo(symbolCombo);

        if (systemCore.getExchange() != null) {
            List<TradePair> symbols = systemCore.getExchange().getTradePairSymbol();
            if (symbols != null) {
                symbolCombo.getItems().setAll(symbols);
                loadTradableSymbolsAsync(symbols);
            }
        }

        if (!symbolCombo.getItems().isEmpty()) {
            symbolCombo.getSelectionModel().selectFirst();
            selectedSymbol = symbolCombo.getValue().toString('/');
        }

        symbolCombo.setOnAction(event -> {
            TradePair selected = symbolCombo.getValue();
            if (selected != null) {
                selectedSymbol = selected.toString('/');
                refreshUI();
            }
        });

        Label timeframeLabel = styledLabel("Timeframe:");
        timeframeCombo = new ComboBox<>();
        timeframeCombo.setPrefWidth(120);
        styleCombo(timeframeCombo);

        if (systemCore.getExchange() != null && systemCore.getExchange().getSupportedTimeframes() != null) {
            timeframeCombo.getItems().setAll(systemCore.getExchange().getSupportedTimeframes());
        }

        if (timeframeCombo.getItems().contains(Timeframe.H1)) {
            timeframeCombo.setValue(Timeframe.H1);
        } else if (!timeframeCombo.getItems().isEmpty()) {
            timeframeCombo.getSelectionModel().selectFirst();
        }

        if (timeframeCombo.getValue() != null) {
            selectedTimeframe = timeframeCombo.getValue();
        }

        timeframeCombo.setOnAction(event -> {
            Timeframe selected = timeframeCombo.getValue();
            if (selected != null) {
                selectedTimeframe = selected;
                refreshUI();
            }
        });

        Label strategyLabel = styledLabel("Strategy:");
        strategyCombo = new ComboBox<>();
        strategyCombo.setPrefWidth(210);
        styleCombo(strategyCombo);
        strategyCombo.getItems().setAll(loadStrategyChoices());
        selectDefaultStrategy(strategyCombo);

        Label filterLabel = styledLabel("Filter:");
        strategyFilterField = new TextField();
        strategyFilterField.setPromptText("Search strategies...");
        strategyFilterField.setPrefWidth(200);
        strategyFilterField.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: " + CLR_TEXT + "; " +
                        "-fx-border-color: #334155; -fx-border-radius: 3; -fx-prompt-text-fill: " + CLR_MUTED + ";");
        strategyFilterField.textProperty().addListener((obs, oldValue, newValue) -> applyStrategyFilter());

        row1.getChildren().setAll(
                symbolLabel,
                symbolCombo,
                timeframeLabel,
                timeframeCombo,
                new Separator(Orientation.VERTICAL),
                strategyLabel,
                strategyCombo,
                filterLabel,
                strategyFilterField);

        HBox row2 = new HBox(8);
        row2.setStyle("-fx-alignment: center-left;");

        testSelectedButton = button("Test Selected", this::testSelectedStrategy);
        testAllStrategiesButton = button("Test All Strategies", this::testAllStrategies);
        testAllTimeframesButton = button("Test All Timeframes", this::testAllTimeframes);
        rankButton = button("Refresh Rank", this::refreshUI);

        row2.getChildren().setAll(
                testSelectedButton,
                testAllStrategiesButton,
                testAllTimeframesButton,
                rankButton);

        HBox row3 = new HBox(8);
        row3.setStyle("-fx-alignment: center-left;");

        assignBestButton = button("Assign Best", this::assignBest);
        manualAssignButton = button("Manual Assign", this::openManualAssignDialog);
        unassignButton = button("Unassign", this::unassign);
        disableAssignmentButton = button("Disable Assignment", this::openDisableDialog);

        assignBestButton.setStyle(
                "-fx-padding: 7px 14px; -fx-background-color: #14532d; -fx-text-fill: " + CLR_GREEN + "; " +
                        "-fx-border-color: " + CLR_GREEN + "; -fx-border-radius: 4; -fx-cursor: hand;");
        unassignButton.setStyle(
                "-fx-padding: 7px 14px; -fx-background-color: #451a03; -fx-text-fill: " + CLR_YELLOW + "; " +
                        "-fx-border-color: " + CLR_YELLOW + "; -fx-border-radius: 4; -fx-cursor: hand;");
        disableAssignmentButton.setStyle(
                "-fx-padding: 7px 14px; -fx-background-color: #450a0a; -fx-text-fill: " + CLR_RED + "; " +
                        "-fx-border-color: " + CLR_RED + "; -fx-border-radius: 4; -fx-cursor: hand;");

        row3.getChildren().setAll(
                assignBestButton,
                manualAssignButton,
                unassignButton,
                disableAssignmentButton);

        box.getChildren().setAll(row1, row2, row3);
        return box;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-padding: 7px 14px; " +
                        "-fx-background-color: #1e293b; " +
                        "-fx-text-fill: " + CLR_TEXT + "; " +
                        "-fx-border-color: #334155; " +
                        "-fx-border-radius: 4; " +
                        "-fx-cursor: hand; " +
                        "-fx-font-size: 11px;");
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-padding: 7px 14px; " +
                        "-fx-background-color: #334155; " +
                        "-fx-text-fill: white; " +
                        "-fx-border-color: " + CLR_ACCENT + "; " +
                        "-fx-border-radius: 4; " +
                        "-fx-cursor: hand; " +
                        "-fx-font-size: 11px;"));
        button.setOnMouseExited(e -> button.setStyle(
                "-fx-padding: 7px 14px; " +
                        "-fx-background-color: #1e293b; " +
                        "-fx-text-fill: " + CLR_TEXT + "; " +
                        "-fx-border-color: #334155; " +
                        "-fx-border-radius: 4; " +
                        "-fx-cursor: hand; " +
                        "-fx-font-size: 11px;"));
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private Label styledLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + CLR_MUTED + "; -fx-font-size: 11px;");
        return label;
    }

    private <T> void styleCombo(ComboBox<T> combo) {
        combo.setStyle(
                "-fx-background-color: #1e293b; " +
                        "-fx-text-fill: " + CLR_TEXT + "; " +
                        "-fx-border-color: #334155; " +
                        "-fx-border-radius: 3; " +
                        "-fx-font-size: 11px;");
    }

    private void applyStrategyFilter() {
        if (strategyCombo == null) {
            return;
        }

        String filter = strategyFilterField == null ? "" : safe(strategyFilterField.getText()).toLowerCase();
        List<String> all = new ArrayList<>(StrategyCatalog.availableStrategyNames());

        if (!filter.isBlank()) {
            all = all.stream()
                    .filter(name -> name != null && name.toLowerCase().contains(filter))
                    .toList();
        }

        strategyCombo.getItems().setAll(all);
        selectDefaultStrategy(strategyCombo);
    }

    private void loadTradableSymbolsAsync(List<TradePair> symbols) {
        if (symbols == null || symbols.isEmpty() || systemCore.getExchange() == null) {
            return;
        }

        List<TradePair> snapshotSymbols = List.copyOf(symbols);
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        UniversalTradabilityService tradabilityService = new UniversalTradabilityService(
                                systemCore.getExchange(),
                                null);
                        List<SymbolTradability> statuses = tradabilityService
                                .getTradability(snapshotSymbols)
                                .get(8, TimeUnit.SECONDS);
                        List<TradePair> filtered = new ArrayList<>();
                        for (SymbolTradability status : statuses) {
                            if (status != null && status.tradePair() != null && status.marketDataAllowed()) {
                                filtered.add(status.tradePair());
                            }
                        }
                        return filtered.isEmpty() ? snapshotSymbols : filtered;
                    } catch (Exception exception) {
                        Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                                ? exception.getCause()
                                : exception;
                        if (cause instanceof TimeoutException) {
                            log.debug("StrategyLabPanel tradability filter timed out; using unfiltered symbols");
                        } else {
                            log.warn("Unable to apply tradability filter in StrategyLabPanel: {}", exception.getMessage());
                        }
                        return snapshotSymbols;
                    }
                })
                .thenAccept(filtered -> runOnFx(() -> {
                    TradePair previous = symbolCombo.getValue();
                    symbolCombo.getItems().setAll(filtered);
                    if (previous != null && symbolCombo.getItems().contains(previous)) {
                        symbolCombo.setValue(previous);
                        return;
                    }
                    if (!symbolCombo.getItems().isEmpty()) {
                        symbolCombo.getSelectionModel().selectFirst();
                        TradePair selected = symbolCombo.getValue();
                        selectedSymbol = selected == null ? selectedSymbol : selected.toString('/');
                        refreshUI();
                    }
                }));
    }

    private List<String> loadStrategyChoices() {
        List<String> names = new ArrayList<>(StrategyCatalog.availableStrategyNames());
        if (names.stream().noneMatch(StrategyCatalog.defaultStrategyName()::equalsIgnoreCase)) {
            names.add(0, StrategyCatalog.defaultStrategyName());
        }
        if (names.isEmpty()) {
            names.add(StrategyCatalog.defaultStrategyName());
        }
        return names;
    }

    private void selectDefaultStrategy(ComboBox<String> combo) {
        if (combo == null) {
            return;
        }
        if (combo.getItems().isEmpty()) {
            combo.getItems().add(StrategyCatalog.defaultStrategyName());
        }
        String current = combo.getValue();
        if (current != null && !current.isBlank() && combo.getItems().contains(current)) {
            return;
        }
        combo.getItems().stream()
                .filter(StrategyCatalog.defaultStrategyName()::equalsIgnoreCase)
                .findFirst()
                .ifPresentOrElse(combo::setValue, () -> combo.getSelectionModel().selectFirst());
    }

    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle(
                "-fx-background-color: " + BG_PANEL + "; " +
                        "-fx-tab-min-width: 130px; " +
                        "-fx-font-size: 12px;");

        tabPane.getTabs().setAll(
                createAssignmentTab(),
                createRankingTab(),
                createVotingTab(),
                createConsensusTab(),
                createAiRuntimeTab(),
                createSchedulerTab());

        return tabPane;
    }

    private Tab createAssignmentTab() {
        Tab tab = new Tab("🎯 Active Assignment");

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: " + BG_PANEL + ";");

        box.getChildren().add(createCard(
                "📌 Current Strategy Assignment",
                createAssignmentInfoContent()));

        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background: " + BG_PANEL + ";");

        tab.setContent(scrollPane);
        return tab;
    }

    private VBox createAssignmentInfoContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        activeAssignmentLabel = new Label("No assignment");
        activeAssignmentLabel.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + CLR_ACCENT + ";");

        activeStrategyLabel = infoLabel("Strategy: —");
        activeScoreLabel = infoLabel("Score: —");
        activeModeLabel = infoLabel("Mode: —");
        activeAssignedAtLabel = infoLabel("Assigned: —");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + CLR_BORDER + ";");

        box.getChildren().setAll(
                activeAssignmentLabel,
                sep,
                activeStrategyLabel,
                activeScoreLabel,
                activeModeLabel,
                activeAssignedAtLabel);

        return box;
    }

    private Label infoLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + CLR_TEXT + "; -fx-font-size: 12px;");
        return label;
    }

    private Tab createRankingTab() {
        Tab tab = new Tab("📊 Strategy Rankings");

        rankingTable = new TableView<>();
        rankingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        rankingTable.setPlaceholder(styledPlaceholder("No strategy rankings available. Run a test first."));
        applyDarkTableStyle(rankingTable);

        TableColumn<StrategyPerformanceReport, Integer> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                rankingTable.getItems().indexOf(cellData.getValue()) + 1));
        rankCol.setPrefWidth(65);

        TableColumn<StrategyPerformanceReport, String> nameCol = new TableColumn<>("Strategy");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                safe(cellData.getValue().getStrategyName())));
        nameCol.setPrefWidth(220);

        TableColumn<StrategyPerformanceReport, Double> scoreCol = doubleColumn("Score",
                StrategyPerformanceReport::getScore, "%.1f");

        TableColumn<StrategyPerformanceReport, Double> winRateCol = doubleColumn("Win Rate",
                report -> report.getWinRate() * 100.0, "%.1f%%");

        TableColumn<StrategyPerformanceReport, Double> returnCol = doubleColumn("Return",
                StrategyPerformanceReport::getTotalReturn, "%.2f%%");

        TableColumn<StrategyPerformanceReport, Double> drawdownCol = doubleColumn("Max DD",
                report -> report.getMaxDrawdown() * 100.0, "%.1f%%");

        TableColumn<StrategyPerformanceReport, Double> profitFactorCol = doubleColumn("Profit Factor",
                StrategyPerformanceReport::getProfitFactor, "%.2f");

        TableColumn<StrategyPerformanceReport, Integer> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                cellData.getValue().getTotalTrades()));
        tradesCol.setPrefWidth(80);

        // Add Assign column
        TableColumn<StrategyPerformanceReport, Void> assignCol = new TableColumn<>("Action");
        assignCol.setPrefWidth(100);
        assignCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button assignButton = new Button("Assign");

            {
                assignButton.setStyle(
                        "-fx-padding: 3px 10px; -fx-font-size: 10px; " +
                                "-fx-background-color: #1e3a5f; -fx-text-fill: " + CLR_ACCENT + "; " +
                                "-fx-border-color: " + CLR_ACCENT + "; -fx-border-radius: 3; -fx-cursor: hand;");
                assignButton.setOnAction(event -> {
                    StrategyPerformanceReport report = getTableView().getItems().get(getIndex());
                    if (report != null) {
                        assignStrategyFromRanking(report);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-background-color: transparent;");
                setGraphic(empty ? null : assignButton);
            }
        });

        rankingTable.getColumns().setAll(
                rankCol,
                nameCol,
                scoreCol,
                winRateCol,
                returnCol,
                drawdownCol,
                profitFactorCol,
                tradesCol,
                assignCol);

        VBox wrapper = new VBox(rankingTable);
        wrapper.setStyle("-fx-background-color: " + BG_PANEL + ";");
        VBox.setVgrow(rankingTable, Priority.ALWAYS);

        tab.setContent(wrapper);
        return tab;
    }

    private TableColumn<StrategyPerformanceReport, Double> doubleColumn(
            String title,
            java.util.function.Function<StrategyPerformanceReport, Double> extractor,
            String format) {
        TableColumn<StrategyPerformanceReport, Double> column = new TableColumn<>(title);

        column.setCellValueFactory(cellData -> {
            Double value = extractor.apply(cellData.getValue());
            return new SimpleObjectProperty<>(value == null ? 0.0 : value);
        });

        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(format, item));
            }
        });

        return column;
    }

    private Tab createVotingTab() {
        Tab tab = new Tab("🗳️ Voting Results");

        votingTable = new TableView<>();
        votingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        votingTable.setPlaceholder(styledPlaceholder("No voting data available. Run a strategy test first."));
        applyDarkTableStyle(votingTable);

        TableColumn<StrategyVote, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                safe(cellData.getValue().getStrategyName())));

        TableColumn<StrategyVote, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getSide().toString()));
        sideCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-background-color: transparent;");
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(item);
                    String color = "BUY".equalsIgnoreCase(item) ? CLR_GREEN
                            : "SELL".equalsIgnoreCase(item) ? CLR_RED : CLR_YELLOW;
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<StrategyVote, Double> confidenceCol = voteDoubleColumn("Confidence", StrategyVote::getConfidence,
                "%.2f");

        TableColumn<StrategyVote, Double> scoreCol = voteDoubleColumn("Score", StrategyVote::getScore, "%.1f");

        TableColumn<StrategyVote, Double> weightCol = voteDoubleColumn("Weight", StrategyVote::getWeight, "%.2f");

        TableColumn<StrategyVote, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                safe(cellData.getValue().getReason())));
        reasonCol.setPrefWidth(320);

        votingTable.getColumns().setAll(
                strategyCol,
                sideCol,
                confidenceCol,
                scoreCol,
                weightCol,
                reasonCol);

        VBox wrapper = new VBox(votingTable);
        wrapper.setStyle("-fx-background-color: " + BG_PANEL + ";");
        VBox.setVgrow(votingTable, Priority.ALWAYS);

        tab.setContent(wrapper);
        return tab;
    }

    private TableColumn<StrategyVote, Double> voteDoubleColumn(
            String title,
            java.util.function.Function<StrategyVote, Double> extractor,
            String format) {
        TableColumn<StrategyVote, Double> column = new TableColumn<>(title);

        column.setCellValueFactory(cellData -> {
            Double value = extractor.apply(cellData.getValue());
            return new SimpleObjectProperty<>(value == null ? 0.0 : value);
        });

        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(format, item));
            }
        });

        return column;
    }

    private Tab createConsensusTab() {
        Tab tab = new Tab("🧠 Consensus Summary");

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: " + BG_PANEL + ";");
        box.getChildren().add(createCard("📈 Consensus Result", createConsensusContent()));

        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background: " + BG_PANEL + ";");

        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createAiRuntimeTab() {
        Tab tab = new Tab("🤖 AI Runtime");

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: " + BG_PANEL + ";");
        box.getChildren().add(createCard("Local gRPC Advisory Runtime", createAiRuntimeContent()));

        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background: " + BG_PANEL + ";");

        tab.setContent(scrollPane);
        return tab;
    }

    private VBox createAiRuntimeContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        aiStatusLabel = infoLabel("Status: —");
        aiLatencyLabel = infoLabel("Latency: —");
        aiCircuitLabel = infoLabel("Circuit: —");
        aiConservativeModeLabel = infoLabel("Conservative mode: —");
        aiReqRateLabel = infoLabel("Requests/min: —");
        aiLastErrorLabel = infoLabel("Last error: —");
        aiLastErrorLabel.setWrapText(true);

        box.getChildren().setAll(
                aiStatusLabel,
                aiLatencyLabel,
                aiCircuitLabel,
                aiConservativeModeLabel,
                aiReqRateLabel,
                aiLastErrorLabel);
        return box;
    }

    private VBox createConsensusContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        consensusLabel = new Label("Consensus: HOLD");
        consensusLabel.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + CLR_ACCENT + ";");

        consensusConfidenceLabel = infoLabel("Confidence: —");
        selectedStrategyLabel = infoLabel("Selected Strategy: —");

        HBox votesBox = new HBox(24);
        buyVotesLabel = new Label("BUY: 0 votes");
        buyVotesLabel.setStyle("-fx-text-fill: " + CLR_GREEN + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        sellVotesLabel = new Label("SELL: 0 votes");
        sellVotesLabel.setStyle("-fx-text-fill: " + CLR_RED + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        holdVotesLabel = new Label("HOLD: 0 votes");
        holdVotesLabel.setStyle("-fx-text-fill: " + CLR_YELLOW + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        votesBox.getChildren().setAll(buyVotesLabel, sellVotesLabel, holdVotesLabel);

        consensusReasonLabel = new Label("Reason: No consensus data");
        consensusReasonLabel.setWrapText(true);
        consensusReasonLabel.setStyle("-fx-text-fill: " + CLR_MUTED + "; -fx-font-size: 11px; -fx-font-style: italic;");

        Separator sep1 = darkSeparator();
        Separator sep2 = darkSeparator();
        Separator sep3 = darkSeparator();

        box.getChildren().setAll(
                consensusLabel,
                sep1,
                consensusConfidenceLabel,
                selectedStrategyLabel,
                sep2,
                votesBox,
                sep3,
                consensusReasonLabel);

        return box;
    }

    private Separator darkSeparator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + CLR_BORDER + ";");
        return sep;
    }

    private VBox createStatusSection() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle(
                "-fx-background-color: " + BG_HEADER + "; " +
                        "-fx-border-color: " + CLR_BORDER + "; " +
                        "-fx-border-width: 1 0 0 0;");
        box.setPrefHeight(160);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: " + CLR_ACCENT + ";");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + CLR_MUTED + ";");

        logsArea = new TextArea();
        logsArea.setEditable(false);
        logsArea.setPrefHeight(100);
        logsArea.setStyle(
                "-fx-control-inner-background: #07090f; " +
                        "-fx-font-family: 'Courier New', monospace; " +
                        "-fx-font-size: 10px; " +
                        "-fx-text-fill: #a3e635; " +
                        "-fx-border-color: " + CLR_BORDER + ";");

        Label progressLbl = styledLabel("Progress:");
        HBox progressRow = new HBox(8, progressLbl, progressBar);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        progressRow.setStyle("-fx-alignment: center-left;");

        Label logsLbl = styledLabel("Logs:");
        box.getChildren().setAll(progressRow, statusLabel, logsLbl, logsArea);

        return box;
    }

    private VBox createCard(String title, VBox content) {
        VBox card = new VBox();
        card.setStyle(
                "-fx-border-color: " + CLR_BORDER + "; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-color: " + BG_CARD + "; " +
                        "-fx-background-radius: 6;");

        Label titleLabel = new Label(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle(
                "-fx-padding: 10px 14px; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: " + CLR_TEXT + "; " +
                        "-fx-background-color: " + BG_CARD2 + "; " +
                        "-fx-background-radius: 6 6 0 0;");

        content.setStyle("-fx-background-color: " + BG_CARD + ";");
        card.getChildren().setAll(titleLabel, content);
        return card;
    }

    private <T> void applyDarkTableStyle(TableView<T> table) {
        table.setStyle(
                "-fx-background-color: " + BG_PANEL + "; " +
                        "-fx-control-inner-background: " + BG_PANEL + "; " +
                        "-fx-table-cell-border-color: " + CLR_BORDER + "; " +
                        "-fx-font-size: 11px; " +
                        "-fx-text-fill: " + CLR_TEXT + ";");
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    String bg = getIndex() % 2 == 0 ? BG_PANEL : BG_CARD2;
                    setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + CLR_TEXT + ";");
                }
            }
        });
    }

    private Label styledPlaceholder(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + CLR_MUTED + "; -fx-font-style: italic; -fx-font-size: 12px;");
        return label;
    }

    private void refreshUI() {
        String symbol = selectedSymbol;
        Timeframe timeframe = selectedTimeframe;
        long requestId = refreshSequence.incrementAndGet();

        CompletableFuture
                .supplyAsync(() -> labService.getSnapshot(symbol, timeframe))
                .whenComplete((loadedSnapshot, throwable) -> runOnFx(() -> {
                    if (requestId != refreshSequence.get()) {
                        return;
                    }
                    try {
                        if (rankingTable == null || votingTable == null) {
                            return;
                        }

                        if (throwable != null) {
                            throw new IllegalStateException(rootMessage(throwable), throwable);
                        }

                        snapshot = loadedSnapshot;

                        if (snapshot == null) {
                            clearSnapshotViews();
                            statusLabel.setText("No strategy lab snapshot available.");
                            return;
                        }

                        rankingTable.setItems(FXCollections.observableArrayList(snapshot.getRankings()));

                        updateAssignmentView(snapshot);
                        updateConsensusView(snapshot);
                        updateAiRuntimeView();

                        if (!running) {
                            statusLabel.setText("Ready");
                        }

                    } catch (Exception exception) {
                        log.error("Failed to refresh Strategy Lab UI", exception);
                        appendLog("Refresh failed: " + rootMessage(exception));
                        statusLabel.setText("Refresh failed.");
                    }
                }));
    }

    private void updateConsensusView(@NotNull StrategyLabSnapshot snapshot) {
        if (!snapshot.hasConsensusData()) {
            clearConsensusView();
            return;
        }

        StrategyConsensusResult result = snapshot.getConsensus();

        Objects.requireNonNull(result);
        String consensusSide = result.getConsensusSide().toString();

        consensusLabel.setText(
                snapshot.isConsensusReached()
                        ? "Consensus: " + consensusSide
                        : "Consensus: No strong consensus");

        consensusConfidenceLabel.setText(
                String.format("Confidence: %.1f%%", safeDouble(result.getConsensusConfidence()) * 100.0));

        selectedStrategyLabel.setText(
                "Selected: " + safeText(result.getSelectedStrategyName(), "-"));

        buyVotesLabel.setText(
                String.format("BUY: %d votes (%.1f)", result.getBuyVotes(), safeDouble(result.getBuyScore())));

        sellVotesLabel.setText(
                String.format("SELL: %d votes (%.1f)", result.getSellVotes(), safeDouble(result.getSellScore())));

        holdVotesLabel.setText(
                String.format("HOLD: %d votes (%.1f)", result.getHoldVotes(), safeDouble(result.getHoldScore())));

        consensusReasonLabel.setText(
                "Reason: " + safeText(result.getReason(), "No reason provided"));

        votingTable.getItems().setAll(
                result.getVotes() == null ? List.of() : result.getVotes());
    }

    private void clearConsensusView() {
        clearSnapshotViews();
    }

    private void clearSnapshotViews() {
        consensus = null;
        consensusLabel.setText("Consensus: No data");
        consensusConfidenceLabel.setText("Confidence: —");
        selectedStrategyLabel.setText("Selected: —");
        buyVotesLabel.setText("BUY: 0 votes");
        sellVotesLabel.setText("SELL: 0 votes");
        holdVotesLabel.setText("HOLD: 0 votes");
        consensusReasonLabel.setText("Reason: No consensus generated");
        votingTable.getItems().clear();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double safeDouble(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private void updateAssignmentView(@NotNull StrategyLabSnapshot snapshot) {
        if (!snapshot.hasAssignment()) {
            activeAssignmentLabel.setText("No assignment");
            activeAssignmentLabel
                    .setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + CLR_MUTED + ";");
            activeStrategyLabel.setText("Strategy: —");
            activeScoreLabel.setText("Score: —");
            activeModeLabel.setText("Mode: —");
            activeAssignedAtLabel.setText("Assigned: —");
            return;
        }

        StrategyAssignment assignment = snapshot.getAssignment();

        if (assignment == null) {
            activeAssignmentLabel.setText("No assignment");
            activeAssignmentLabel
                    .setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + CLR_MUTED + ";");
            activeStrategyLabel.setText("Strategy: —");
            activeScoreLabel.setText("Score: —");
            activeModeLabel.setText("Mode: —");
            activeAssignedAtLabel.setText("Assigned: —");
            return;
        }

        activeAssignmentLabel.setText(
                safe(assignment.getStrategyId()) + " (" + assignment.getMode().getDisplayName() + ")");
        activeAssignmentLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + CLR_GREEN + ";");
        activeStrategyLabel.setText("Strategy: " + safe(assignment.getStrategyId()));
        activeScoreLabel.setText(String.format("Score: %.1f", assignment.getScoreAtAssignment()));
        activeModeLabel.setText("Mode: " + assignment.getMode().getDisplayName());
        activeAssignedAtLabel.setText("Assigned: " + assignment.getAssignedAt());
    }

    private void updateAiRuntimeView() {
        if (aiStatusLabel == null) {
            return;
        }

        if (!(systemCore.getAiReasoningService() instanceof LocalAiRuntimeService runtimeService)) {
            aiStatusLabel.setText("Status: Disabled (not using local gRPC runtime)");
            aiLatencyLabel.setText("Latency: —");
            aiCircuitLabel.setText("Circuit: —");
            aiConservativeModeLabel.setText("Conservative mode: —");
            aiReqRateLabel.setText("Requests/min: —");
            aiLastErrorLabel.setText("Last error: —");
            return;
        }

        AiGrpcHealthStatus status = runtimeService.healthStatus();
        aiStatusLabel.setText("Status: " + safe(status.status()));
        aiLatencyLabel.setText(String.format("Latency: %.1f ms", status.avgLatencyMs()));
        aiCircuitLabel.setText("Circuit: " + safe(status.circuitState()));
        aiConservativeModeLabel.setText("Conservative mode: " + (status.conservativeMode() ? "ENABLED" : "DISABLED"));
        aiReqRateLabel.setText("Requests/min: " + status.requestsPerMinute());
        aiLastErrorLabel.setText("Last error: " + safe(status.lastError()));
    }

    private void testSelectedStrategy() {
        String strategyName = strategyCombo == null ? null : strategyCombo.getValue();

        if (strategyName == null || strategyName.isBlank()) {
            strategyName = StrategyCatalog.defaultStrategyName();
            if (strategyCombo != null) {
                if (!strategyCombo.getItems().contains(strategyName)) {
                    strategyCombo.getItems().add(0, strategyName);
                }
                strategyCombo.setValue(strategyName);
            }
            appendLog("No strategy was selected; using default strategy: " + strategyName + ".");
        }
        String selectedStrategyName = strategyName;

        appendLog("Testing strategy '" + selectedStrategyName + "' on " + selectedSymbol + "/"
                + selectedTimeframe.getCode()
                + "...");
        setRunning(true, "Testing selected strategy with real candles...");

        fetchHistoricalCandles()
                .thenCompose(candles -> labService.evaluateAndAssignBest(
                        selectedSymbol,
                        selectedTimeframe,
                        candles,
                        List.of(selectedStrategyName)))
                .whenComplete((assignment, throwable) -> runOnFx(() -> {
                    if (throwable != null) {
                        appendLog("Test failed: " + rootMessage(throwable));
                    } else if (assignment != null) {
                        appendLog("Real-candle test completed and assigned: " + assignment.getStrategyId()
                                + " (score " + String.format("%.1f", assignment.getScoreAtAssignment()) + ").");
                        refreshUI();
                    } else {
                        appendLog("Real-candle test completed, but no strategy could be assigned. "
                                + "Check trade count, score, and candle history.");
                        refreshUI();
                    }

                    setRunning(false, "Ready");
                }));
    }

    private void testAllStrategies() {
        appendLog("Testing all strategies on " + selectedSymbol + "/" + selectedTimeframe.getCode() + "...");
        setRunning(true, "Testing all strategies with real candles...");

        fetchHistoricalCandles()
                .thenCompose(candles -> labService.evaluateAndAssignBest(
                        selectedSymbol,
                        selectedTimeframe,
                        candles,
                        new ArrayList<>(StrategyCatalog.availableStrategyNames())))
                .whenComplete((assignment, throwable) -> runOnFx(() -> {
                    if (throwable != null) {
                        appendLog("Tests failed: " + rootMessage(throwable));
                    } else if (assignment != null) {
                        appendLog("All real-candle tests completed and assigned: " + assignment.getStrategyId()
                                + " (score " + String.format("%.1f", assignment.getScoreAtAssignment()) + ").");
                        refreshUI();
                    } else {
                        appendLog("All real-candle tests completed, but no strategy could be assigned. "
                                + "Check trade count, score, and candle history.");
                        refreshUI();
                    }

                    setRunning(false, "Ready");
                }));
    }

    private void testAllTimeframes() {
        appendLog("Testing all strategies on all timeframes for " + selectedSymbol + "...");
        setRunning(true, "Fetching candles for all timeframes...");

        fetchHistoricalCandlesByTimeframe(List.of(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1))
                .thenCompose(candlesByTimeframe -> labService.evaluateAndAssignBestAcrossTimeframes(
                        selectedSymbol,
                        candlesByTimeframe))
                .whenComplete((assignment, throwable) -> runOnFx(() -> {
                    if (throwable != null) {
                        appendLog("Tests failed: " + rootMessage(throwable));
                    } else if (assignment != null) {
                        appendLog("All timeframe tests completed and assigned: " + assignment.getStrategyId()
                                + " (score " + String.format("%.1f", assignment.getScoreAtAssignment()) + ").");
                        refreshUI();
                    } else {
                        appendLog("All timeframe tests completed, but no strategy could be assigned. "
                                + "Check candle history and minimum score settings.");
                        refreshUI();
                    }

                    setRunning(false, "Ready");
                }));
    }

    private void assignBest() {
        appendLog("Assigning best strategy for " + selectedSymbol + "/" + selectedTimeframe.getCode() + "...");
        setRunning(true, "Assigning best from real-candle evaluation...");

        fetchHistoricalCandles()
                .thenCompose(candles -> labService.evaluateAndAssignBest(
                        selectedSymbol,
                        selectedTimeframe,
                        candles))
                .whenComplete((assignment, throwable) -> runOnFx(() -> {
                    if (throwable != null) {
                        log.error("Failed to assign best strategy", throwable);
                        appendLog("Assign best failed: " + rootMessage(throwable));
                    } else if (assignment != null) {
                        appendLog("Assigned: " + assignment.getStrategyId());
                        refreshUI();
                    } else {
                        appendLog("No suitable strategy found.");
                        refreshUI();
                    }
                    setRunning(false, "Ready");
                }));
    }

    private void openManualAssignDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Manual Strategy Assignment");
        dialog.setHeaderText("Select a strategy to assign");

        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(new ArrayList<>(StrategyCatalog.availableStrategyNames()));

        if (!combo.getItems().isEmpty()) {
            combo.getSelectionModel().selectFirst();
        }

        VBox content = new VBox(10, new Label("Strategy:"), combo);
        content.setPadding(new Insets(15));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? combo.getValue() : null);

        dialog.showAndWait().ifPresent(strategyName -> {
            try {
                StrategyAssignment assignment = StrategySelectionService.getInstance()
                        .manuallyAssign(selectedSymbol, selectedTimeframe, strategyName, true,
                                "Manual assignment from Strategy Lab");

                if (assignment != null) {
                    appendLog("Manually assigned: " + assignment.getStrategyId());
                }

                refreshUI();

            } catch (Exception exception) {
                log.error("Manual assignment failed", exception);
                appendLog("Manual assignment failed: " + rootMessage(exception));
            }
        });
    }

    private void unassign() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unassign Strategy");
        alert.setHeaderText("Unassign strategy for " + selectedSymbol + "/" + selectedTimeframe.getCode() + "?");
        alert.setContentText("This symbol/timeframe will have no automatic strategy trading.");

        alert.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }

            try {
                StrategyAssignment active = StrategyAssignmentRepository.getInstance()
                        .getActive(selectedSymbol, selectedTimeframe);
                if (active != null) {
                    StrategyAssignmentRepository.getInstance().delete(active.getAssignmentId());
                    appendLog("Unassigned: " + selectedSymbol + "/" + selectedTimeframe.getCode());
                } else {
                    appendLog("No active assignment to unassign.");
                }
                refreshUI();

            } catch (Exception exception) {
                log.error("Unassign failed", exception);
                appendLog("Unassign failed: " + rootMessage(exception));
            }
        });
    }

    private void openDisableDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Disable Assignment");
        dialog.setHeaderText("Disable strategy assignment");

        TextArea reasonArea = new TextArea();
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(3);
        reasonArea.setPromptText("Reason for disabling...");

        VBox content = new VBox(10, new Label("Reason:"), reasonArea);
        content.setPadding(new Insets(15));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? reasonArea.getText() : null);

        dialog.showAndWait().ifPresent(reason -> {
            try {
                StrategyAssignment assignment = StrategyAssignmentRepository.getInstance()
                        .getActive(selectedSymbol, selectedTimeframe);
                if (assignment == null) {
                    appendLog("No active assignment to disable.");
                    return;
                }
                StrategyAssignmentRepository.getInstance().save(assignment.disabled(reason));
                appendLog("Disabled: " + assignment.getStrategyId());
                refreshUI();

            } catch (Exception exception) {
                log.error("Disable assignment failed", exception);
                appendLog("Disable failed: " + rootMessage(exception));
            }
        });
    }

    private CompletableFuture<List<CandleData>> fetchHistoricalCandles() {
        TradePair pair = symbolCombo == null ? null : symbolCombo.getValue();
        if (pair == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Select a symbol first."));
        }
        if (systemCore.getExchange() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Connect an exchange first."));
        }

        try {
            return fetchHistoricalCandles(pair, selectedTimeframe);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletableFuture<Map<Timeframe, List<CandleData>>> fetchHistoricalCandlesByTimeframe(
            List<Timeframe> timeframes) {
        TradePair pair = symbolCombo == null ? null : symbolCombo.getValue();
        if (pair == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Select a symbol first."));
        }
        if (systemCore.getExchange() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Connect an exchange first."));
        }

        List<CompletableFuture<Map.Entry<Timeframe, List<CandleData>>>> futures = timeframes.stream()
                .filter(Objects::nonNull)
                .map(timeframe -> fetchHistoricalCandles(pair, timeframe)
                        .thenApply(candles -> Map.entry(timeframe, candles)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<Timeframe, List<CandleData>> candlesByTimeframe = new LinkedHashMap<>();
                    for (CompletableFuture<Map.Entry<Timeframe, List<CandleData>>> future : futures) {
                        Map.Entry<Timeframe, List<CandleData>> entry = future.join();
                        if (!entry.getValue().isEmpty()) {
                            candlesByTimeframe.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return candlesByTimeframe;
                });
    }

    private CompletableFuture<List<CandleData>> fetchHistoricalCandles(TradePair pair, Timeframe timeframe) {
        if (timeframe == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Select a timeframe first."));
        }
        HistoricalDataPrefetcher preFetcher = HistoricalDataPrefetcher.forCurrentExchange(
                systemCore.getExchange(),
                systemCore.getHistoricalDataRepository());
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = historicalWindowStart(end, timeframe);
        appendLog("Fetching historical candles for " + pair.toString('/') + "/" + timeframe.getCode() + "...");

        return preFetcher.fetchAndCacheData(
                pair,
                start,
                end,
                timeframe.getCode(),
                progress -> {
                    if (progress >= 0) {
                        runOnFx(() -> setRunning(true,
                                "Fetching " + timeframe.getCode() + " candles: " + progress + "%"));
                    }
                }).thenApply(candles -> validateBacktestDataDepth(pair, timeframe, candles));
    }

    private List<CandleData> validateBacktestDataDepth(
            TradePair pair,
            Timeframe timeframe,
            List<CandleData> candles) {
        List<CandleData> safeCandles = candles == null ? List.of() : candles;
        int candleCount = safeCandles.size();
        if (!HistoricalDataPrefetcher.hasEnoughDataForBasicTesting(candleCount)) {
            throw new IllegalStateException("Not enough historical candles for "
                    + pair.toString('/') + "/" + timeframe.getCode()
                    + ": " + candleCount + " loaded. Basic backtesting requires at least 100.");
        }
        String quality = HistoricalDataPrefetcher.hasEnoughDataForStrongTesting(candleCount)
                ? "strong"
                : HistoricalDataPrefetcher.hasEnoughDataForGoodTesting(candleCount) ? "good" : "basic";
        appendLog("Loaded " + candleCount + " candles for " + pair.toString('/') + "/"
                + timeframe.getCode() + " (" + quality + " data depth).");
        return safeCandles;
    }

    private LocalDateTime historicalWindowStart(LocalDateTime end, Timeframe timeframe) {
        long seconds = Math.max(60L, timeframe.getSeconds());
        long days = Math.max(30L, Math.min(730L, (seconds * 1_000L) / 86_400L + 14L));
        return end.minusDays(days);
    }

    private void appendLog(String message) {
        runOnFx(() -> {
            if (logsArea == null) {
                return;
            }

            String timestamp = java.time.LocalDateTime.now().format(LOG_TIME_FORMAT);
            logsArea.appendText("[" + timestamp + "] " + safe(message) + "\n");
        });
    }

    private void setRunning(boolean running, String status) {
        this.running = running;

        runOnFx(() -> {
            testSelectedButton.setDisable(running);
            testAllStrategiesButton.setDisable(running);
            testAllTimeframesButton.setDisable(running);
            rankButton.setDisable(running);
            assignBestButton.setDisable(running);
            manualAssignButton.setDisable(running);
            unassignButton.setDisable(running);
            disableAssignmentButton.setDisable(running);

            statusLabel.setText(status == null || status.isBlank() ? "Ready" : status);
            progressBar.setProgress(running ? ProgressBar.INDETERMINATE_PROGRESS : 0.0);
        });
    }

    private void assignStrategyFromRanking(@NotNull StrategyPerformanceReport report) {
        String strategyName = report.getStrategyName();
        String symbol = report.getSymbol();
        Timeframe timeframe = report.getTimeframe();

        // Confirm assignment with user
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Assign Strategy");
        confirmAlert.setHeaderText("Assign strategy from backtesting results?");
        confirmAlert.setContentText(
                "Strategy: " + strategyName + "\n" +
                        "Symbol: " + symbol + "\n" +
                        "Timeframe: " + timeframe.getCode() + "\n" +
                        "Score: " + String.format("%.1f", report.getScore()) + "\n" +
                        "Win Rate: " + String.format("%.1f%%", report.getWinRate() * 100.0) + "\n" +
                        "Return: " + String.format("%.2f%%", report.getTotalReturn()));

        confirmAlert.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }

            try {
                StrategyAssignment assignment = StrategySelectionService.getInstance()
                        .manuallyAssign(symbol, timeframe, strategyName, true,
                                "Manual assignment from Strategy Lab ranking");

                if (assignment != null) {
                    appendLog("Assigned from backtest results: " + assignment.getStrategyId() +
                            " (Score: " + String.format("%.1f", report.getScore()) + ")");
                    refreshUI();
                } else {
                    appendLog("Assignment failed for strategy: " + strategyName);
                }

            } catch (Exception exception) {
                log.error("Assignment from ranking failed", exception);
                appendLog("Assignment failed: " + rootMessage(exception));
            }
        });
    }

    private void runOnFx(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    /**
     * Creates the ⚙ Scheduler tab that hosts the {@link StrategyLabControlPanel}.
     */
    private Tab createSchedulerTab() {
        Tab tab = new Tab("⚙ Scheduler");
        tab.setContent(new StrategyLabControlPanel());
        return tab;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
