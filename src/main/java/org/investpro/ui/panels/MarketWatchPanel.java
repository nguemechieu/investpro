package org.investpro.ui.panels;

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.market.MarketInstrumentService;
import org.investpro.trading.tradability.ExchangeTradabilityProvider;
import org.investpro.trading.tradability.MarketWatchTradabilityFilter;
import org.investpro.trading.tradability.ProductTradabilityStatus;
import org.investpro.trading.tradability.TradabilityProvider;
import org.investpro.trading.tradability.TradabilityProviderRegistry;
import org.investpro.trading.tradability.TradabilityRefreshScheduler;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.ui.market.MarketWatchProductFilter;
import org.investpro.ui.models.MarketWatchRow;
import org.investpro.ui.utils.CurrencyIconLoader;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MarketWatch Panel displays symbol-level agent state and trading readiness.
 * <p>
 * Shows:
 * - Market data (bid, ask, spread)
 * - Agent state (training, paper trading, live ready, live trading, blocked,
 * etc.)
 * - Active strategy and timeframe
 * - Strategy score
 * - Live readiness status
 * - Issues/reasons if blocked
 */
@Getter
@Setter
@Slf4j

public class MarketWatchPanel extends StackPane {

    private final SystemCore systemCore;
    private SymbolAgentManager symbolAgentManager;
    private final TradabilityProvider tradabilityProvider;
    private final TableView<MarketWatchRow> table = new TableView<>();
    private final Map<String, MarketWatchRow> rowCache = new LinkedHashMap<>();
    private final Map<String, ProductTradabilityStatus> tradabilityCache = new ConcurrentHashMap<>();
    private final Map<String, SymbolTradability> exchangeTradabilityCache = new ConcurrentHashMap<>();
    private final Set<String> favoriteSymbols = ConcurrentHashMap.newKeySet();
    private final ComboBox<MarketWatchTradabilityFilter> filterSelector = new ComboBox<>();
    private final ComboBox<MarketWatchProductFilter> productFilterSelector = new ComboBox<>();
    private final ComboBox<String> sortSelector = new ComboBox<>();
    private final TradabilityRefreshScheduler refreshScheduler;
    private final MarketInstrumentService marketInstrumentService = new MarketInstrumentService();
    private final Map<String, MarketInstrument> instrumentsBySymbol = new ConcurrentHashMap<>();
    private final AtomicReference<MarketWatchTradabilityFilter> activeFilter = new AtomicReference<>(
            MarketWatchTradabilityFilter.SHOW_ALL);
    private final AtomicReference<MarketWatchProductFilter> activeProductFilter = new AtomicReference<>(
            MarketWatchProductFilter.ALL);

    private Timeline refreshTimer;
    private static final long REFRESH_INTERVAL_MS = 3000; // Refresh every 3 seconds
    private Set<String> lastSymbolKeys = Set.of();

    public MarketWatchPanel(@NotNull SystemCore systemCore) {
        this.systemCore = systemCore;

        // Safely get SymbolAgentManager with error logging
        try {

            this.symbolAgentManager = systemCore.getSymbolAgentManager();
            if (this.symbolAgentManager == null) {
                log.error("SymbolAgentManager is null from SmartBot");
            }

        } catch (Exception e) {
            log.error("Error initializing SymbolAgentManager in MarketWatchPanel", e);

        }

        this.tradabilityProvider = resolveTradabilityProvider();
        this.refreshScheduler = new TradabilityRefreshScheduler(() -> {
            refreshMarketWatchData();
            return null;
        });

        initializeUI();
        startAutoRefresh();
        refreshMarketInstrumentsAsync();
        refreshMarketWatchData();
        refreshScheduler.start(5);
    }

    private TradabilityProvider resolveTradabilityProvider() {
        TradabilityProvider provider = TradabilityProviderRegistry.getInstance().getProvider().orElse(null);
        if (provider != null) {
            return provider;
        }

        if (systemCore != null && systemCore.getExchange() != null) {
            if (systemCore.getExchange() instanceof org.investpro.exchange.Coinbase coinbase) {
                return new org.investpro.trading.tradability.CoinbaseTradabilityService(coinbase);
            }
            return new ExchangeTradabilityProvider(systemCore.getExchange(),
                    systemCore.getUniversalTradabilityService());
        }

        return null;
    }

    private void initializeUI() {
        // Setup table columns
        setupTableColumns();

        // Setup table styling with modern appearance
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-control-inner-background: #0f172a; " +
                        "-fx-table-cell-border-color: #1e293b; " +
                        "-fx-text-fill: #e0e7ff;");

        // Add alternating row colors for better readability
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MarketWatchRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    // Alternate colors for better readability
                    String bgColor = getIndex() % 2 == 0 ? "#0f172a" : "#1a1f3a";
                    setStyle("-fx-background-color: " + bgColor + "; " +
                            "-fx-text-fill: #e0e7ff;");
                }
            }
        });

        table.setPrefHeight(400);
        table.setMinHeight(200);
        table.setFixedCellSize(26); // Slightly increased for better visibility

        // Create enhanced controls bar
        HBox controls = createControlsBar();

        // Layout
        VBox content = new VBox(8, controls, table);
        content.setPadding(new Insets(8));
        content.setMinHeight(300);
        content.setStyle("-fx-background-color: #0a0e27;");
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        this.getChildren().add(content);
        this.setPadding(new Insets(4));
        this.setStyle("-fx-background-color: #0a0e27;");
    }

    private void setupTableColumns() {
        // Currency Icon column
        TableColumn<MarketWatchRow, String> iconCol = new TableColumn<>("");
        iconCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDisplaySymbol()));
        iconCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    // Extract base currency (before underscore) for icon lookup
                    String baseCurrency = item.split("[_/\\-]")[0];
                    Image icon = CurrencyIconLoader.loadCurrencyIcon(baseCurrency);
                    if (icon != null) {
                        ImageView imageView = new ImageView(icon);
                        imageView.setFitHeight(20);
                        imageView.setFitWidth(20);
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                    } else {
                        setText("\uD83D\uDCB1");
                        setGraphic(null);
                    }
                }
            }
        });
        iconCol.setPrefWidth(32);
        iconCol.setResizable(false);

        // Symbol column
        TableColumn<MarketWatchRow, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDisplaySymbol()));
        symbolCol.setPrefWidth(120);

        TableColumn<MarketWatchRow, String> marketTypeCol = new TableColumn<>("Market");
        marketTypeCol.setCellValueFactory(cellData -> cellData.getValue().marketBadgeProperty());
        marketTypeCol.setPrefWidth(110);

        TableColumn<MarketWatchRow, String> venueCol = new TableColumn<>("Venue");
        venueCol.setCellValueFactory(cellData -> cellData.getValue().venueProperty());
        venueCol.setPrefWidth(130);

        TableColumn<MarketWatchRow, String> tradabilityCol = new TableColumn<>("Tradability");
        tradabilityCol.setCellValueFactory(cellData -> cellData.getValue().tradabilityStatusProperty());
        tradabilityCol.setPrefWidth(110);

        // Bid column
        TableColumn<MarketWatchRow, Double> bidCol = new TableColumn<>("Bid");
        bidCol.setCellValueFactory(cellData -> cellData.getValue().bidProperty().asObject());
        bidCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : String.format("%.5f", item == null ? 0.0 : item));
            }
        });
        bidCol.setPrefWidth(80);

        // Ask column
        TableColumn<MarketWatchRow, Double> askCol = new TableColumn<>("Ask");
        askCol.setCellValueFactory(cellData -> cellData.getValue().askProperty().asObject());
        askCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : String.format("%.5f", item == null ? 0.0 : item));
            }
        });
        askCol.setPrefWidth(80);

        // Spread % column
        TableColumn<MarketWatchRow, Double> spreadCol = new TableColumn<>("Spread %");
        spreadCol.setCellValueFactory(
                cellData -> (ObservableValue<Double>) (Object) cellData.getValue().spreadPercentProperty());
        spreadCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.4f", item));
            }
        });
        spreadCol.setPrefWidth(70);

        // Session column
        TableColumn<MarketWatchRow, String> sessionCol = new TableColumn<>("Session");
        sessionCol.setCellValueFactory(cellData -> cellData.getValue().sessionProperty());
        sessionCol.setPrefWidth(60);

        // Agent State column
        TableColumn<MarketWatchRow, String> agentStateCol = new TableColumn<>("State");
        agentStateCol.setCellValueFactory(cellData -> cellData.getValue().agentStateProperty());
        agentStateCol.setPrefWidth(90);

        // Trading Mode column (with color coding)
        TableColumn<MarketWatchRow, String> modeCol = new TableColumn<>("Mode");
        modeCol.setCellValueFactory(cellData -> cellData.getValue().tradingModeProperty());
        modeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    applyModeStyle(item);
                }
            }

            private void applyModeStyle(String mode) {
                String style = switch (mode.toLowerCase()) {
                    case "training / evaluating" ->
                        "-fx-text-fill: #94a3b8; -fx-font-weight: normal;";
                    case "paper trading" ->
                        "-fx-text-fill: #3b82f6; -fx-font-weight: normal;";
                    case "live ready" ->
                        "-fx-text-fill: #22c55e; -fx-font-weight: bold;";
                    case "live trading" ->
                        "-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-background-color: rgba(16,185,129,0.1);";
                    case "blocked" ->
                        "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
                    case "paused" ->
                        "-fx-text-fill: #f59e0b; -fx-font-weight: normal;";
                    case "failed" ->
                        "-fx-text-fill: #dc2626; -fx-font-weight: bold;";
                    default -> "-fx-text-fill: #6b7280;";
                };
                setStyle(style);
            }
        });
        modeCol.setPrefWidth(110);

        // Active Strategy column
        TableColumn<MarketWatchRow, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(cellData -> cellData.getValue().activeStrategyProperty());
        strategyCol.setPrefWidth(110);

        // Timeframe column
        TableColumn<MarketWatchRow, String> tfCol = new TableColumn<>("TF");
        tfCol.setCellValueFactory(cellData -> cellData.getValue().activeTimeframeProperty());
        tfCol.setPrefWidth(50);

        // Strategy Score column
        TableColumn<MarketWatchRow, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(
                cellData -> (ObservableValue<Double>) (Object) cellData.getValue().strategyScoreProperty());
        scoreCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item == 0.0) {
                    setText("");
                } else {
                    setText(String.format("%.2f", item));
                    // Color code the score
                    String style = item > 0.75 ? "-fx-text-fill: #22c55e;"
                            : item > 0.50 ? "-fx-text-fill: #3b82f6;"
                                    : item > 0.25 ? "-fx-text-fill: #f59e0b;" : "-fx-text-fill: #ef4444;";
                    setStyle(style);
                }
            }
        });
        scoreCol.setPrefWidth(60);

        // Signal column — shows latest signal direction and confidence
        TableColumn<MarketWatchRow, String> signalCol = new TableColumn<>("Signal");
        signalCol.setCellValueFactory(cellData -> cellData.getValue().lastSignalProperty());
        signalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    // Green for BUY signals, red for SELL
                    String style = item.contains("BUY")
                            ? "-fx-text-fill: #22c55e; -fx-font-weight: bold;"
                            : item.contains("SELL")
                                    ? "-fx-text-fill: #ef4444; -fx-font-weight: bold;"
                                    : "-fx-text-fill: #94a3b8;";
                    setStyle(style);
                }
            }
        });
        signalCol.setPrefWidth(90);

        // Live Ready column
        TableColumn<MarketWatchRow, Boolean> liveReadyCol = new TableColumn<>("Live Ready");
        liveReadyCol.setCellValueFactory(cellData -> cellData.getValue().liveReadyProperty());
        liveReadyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item ? "✓ Yes" : "✗ No");
                    String style = item ? "-fx-text-fill: #22c55e; -fx-font-weight: bold;" : "-fx-text-fill: #ef4444;";
                    setStyle(style);
                }
            }
        });
        liveReadyCol.setPrefWidth(75);

        // Assigned Strategy column — shows the strategy configured/evaluated for this
        // symbol
        TableColumn<MarketWatchRow, String> assignedStrategyCol = new TableColumn<>("Assigned Strategy");
        assignedStrategyCol.setCellValueFactory(cellData -> cellData.getValue().assignedStrategyProperty());
        assignedStrategyCol.setPrefWidth(200);
        assignedStrategyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("—");
                    setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #60a5fa; -fx-font-weight: bold;");
                }
            }
        });

        // Add columns to table
        table.getColumns().add(iconCol);
        table.getColumns().add(symbolCol);
        table.getColumns().add(marketTypeCol);
        table.getColumns().add(venueCol);
        table.getColumns().add(bidCol);
        table.getColumns().add(askCol);
        table.getColumns().add(spreadCol);
        table.getColumns().add(sessionCol);
        table.getColumns().add(tradabilityCol);
        table.getColumns().add(modeCol);
        table.getColumns().add(strategyCol);
        table.getColumns().add(tfCol);
        table.getColumns().add(signalCol);
        table.getColumns().add(scoreCol);
        table.getColumns().add(liveReadyCol);
        table.getColumns().add(assignedStrategyCol);



    }

    private HBox createControlsBar() {
        Label titleLabel = new Label("\uD83D\uDCCA Market Watch - Symbol Agent Status");
        titleLabel.setStyle(
                "-fx-font-size: 13px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #60a5fa; " +
                        "-fx-font-family: 'Segoe UI', sans-serif;");

        Label statusLabel = new Label("Status: ");
        Label symbolCountLabel = new Label("Symbols: 0");
        symbolCountLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        productFilterSelector.getItems().setAll(MarketWatchProductFilter.values());
        productFilterSelector.setValue(MarketWatchProductFilter.ALL);
        productFilterSelector.setOnAction(event -> {
            MarketWatchProductFilter selected = productFilterSelector.getValue();
            activeProductFilter.set(selected == null ? MarketWatchProductFilter.ALL : selected);
            applyProductFilterToTable();
            symbolCountLabel.setText("Symbols: " + table.getItems().size());
        });

        Button refreshButton = new Button("⟳");
        refreshButton.setStyle(
                "-fx-padding: 6 12; " +
                        "-fx-background-color: #3b82f6; " +
                        "-fx-text-fill: white; " +
                        "-fx-border-radius: 3; " +
                        "-fx-cursor: hand;");
        refreshButton.setOnAction(e -> {
            refreshMarketWatchData();
            symbolCountLabel.setText("Symbols: " + rowCache.size());
        });

        Button pauseButton = new Button("⏸ ");
        pauseButton.setStyle(
                "-fx-padding: 6 12; " +
                        "-fx-background-color: #8b5cf6; " +
                        "-fx-text-fill: white; " +
                        "-fx-border-radius: 3; " +
                        "-fx-cursor: hand;");
        pauseButton.setOnAction(e -> {
            if (refreshTimer != null && refreshTimer.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                refreshTimer.stop();
                pauseButton.setText("▶ ");
                pauseButton.setStyle(
                        "-fx-padding: 6 12; " +
                                "-fx-background-color: #10b981; " +
                                "-fx-text-fill: white; " +
                                "-fx-border-radius: 3; " +
                                "-fx-cursor: hand;");
            } else {
                startAutoRefresh();
                pauseButton.setText("⏸ Pause");
                pauseButton.setStyle(
                        "-fx-padding: 6 12; " +
                                "-fx-background-color: #8b5cf6; " +
                                "-fx-text-fill: white; " +
                                "-fx-border-radius: 3; " +
                                "-fx-cursor: hand;");
            }
        });

        Button exportButton = new Button("\uD83D\uDCE5 Export");
        exportButton.setStyle(
                "-fx-padding: 6 12; " +
                        "-fx-background-color: #06b6d4; " +
                        "-fx-text-fill: white; " +
                        "-fx-border-radius: 3; " +
                        "-fx-cursor: hand;");
        exportButton.setOnAction(e -> exportToCSV());

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox controls = new HBox(12, titleLabel, statusLabel, symbolCountLabel, productFilterSelector, spacer,
                refreshButton, pauseButton, exportButton);
        controls.setPadding(new Insets(8, 12, 8, 12));
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        controls.setStyle(
                "-fx-background-color: linear-gradient(to right, #1a1f35, #1e293b); " +
                        "-fx-border-color: #334155; " +
                        "-fx-border-width: 0 0 1 0;");
        return controls;
    }

    private void startAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }

        refreshTimer = new Timeline(
                new javafx.animation.KeyFrame(
                        Duration.millis(REFRESH_INTERVAL_MS),
                        event -> refreshMarketWatchData()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    /**
     * Refresh market watch data from SymbolAgentManager.
     * <p>
     * This pulls the latest symbol states and updates the table.
     * Only updates if the data has actually changed.
     */
    public synchronized void refreshMarketWatchData() {
        if (symbolAgentManager == null) {
            log.warn("SymbolAgentManager is not available - skipping refresh");
            return;
        }

        Platform.runLater(() -> {
            try {
                List<SymbolAgentState> allStates = symbolAgentManager.getAllStates();

                if (allStates == null) {
                    log.error("getAllStates returned null from SymbolAgentManager");
                    return;
                }

                log.debug("Refreshing market watch with {} states", allStates.size());

                Set<String> currentSymbolKeys = allStates.stream()
                        .filter(Objects::nonNull)
                        .map(MarketWatchPanel::stateKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                // Remove rows for symbols no longer in the manager.
                rowCache.keySet().retainAll(currentSymbolKeys);

                // Always update all rows — MarketWatchRow uses JavaFX Property types
                // so property changes auto-notify the table cells without table.refresh().
                // Skipping this loop when row count is stable was the bug: bid/ask/spread/
                // signal data would never update after initial population.
                for (SymbolAgentState state : allStates) {
                    if (state != null) {
                        TradePair symbol = state.getSymbol();
                        String symbolKey = stateKey(state);
                        MarketWatchRow row = rowCache.computeIfAbsent(
                                symbolKey,
                                key -> MarketWatchRow.builder()
                                        .symbol(symbol)
                                        .build());
                        row.setMarketInstrument(resolveInstrument(state));
                        row.updateSymbolState(state);
                    }
                }

                // Re-populate table items when the symbol set changes. Row properties update
                // cells in-place between set changes.
                if (!currentSymbolKeys.equals(lastSymbolKeys)) {
                    applyProductFilterToTable();
                    lastSymbolKeys = Set.copyOf(currentSymbolKeys);
                    log.debug("Refreshed MarketWatch with {} symbols", rowCache.size());
                }
            } catch (Exception e) {
                log.error("Error refreshing market watch data", e);
            }
        });
    }

    private void refreshMarketInstrumentsAsync() {
        if (systemCore == null || systemCore.getExchange() == null) {
            return;
        }
        marketInstrumentService.loadForExchange(systemCore.getExchange())
                .thenAccept(instruments -> {
                    Map<String, MarketInstrument> refreshed = new LinkedHashMap<>();
                    for (MarketInstrument instrument : instruments) {
                        if (instrument == null) {
                            continue;
                        }
                        refreshed.put(instrumentKey(instrument), instrument);
                        if (instrument.tradePair() != null) {
                            refreshed.putIfAbsent(symbolKey(instrument.tradePair()), instrument);
                        }
                        if (instrument.nativeSymbol() != null && !instrument.nativeSymbol().isBlank()) {
                            refreshed.putIfAbsent(instrument.nativeSymbol().trim().toUpperCase(Locale.ROOT), instrument);
                        }
                    }
                    instrumentsBySymbol.clear();
                    instrumentsBySymbol.putAll(refreshed);
                    Platform.runLater(() -> {
                        rowCache.values().forEach(row -> row.setMarketInstrument(resolveInstrument(row.getSymbol())));
                        applyProductFilterToTable();
                    });
                })
                .exceptionally(exception -> {
                    log.warn("Unable to refresh market instrument metadata: {}", exception.getMessage());
                    return null;
                });
    }

    private MarketInstrument resolveInstrument(TradePair pair) {
        if (pair == null) {
            return null;
        }
        MarketInstrument byPair = instrumentsBySymbol.get(symbolKey(pair));
        if (byPair != null) {
            return byPair;
        }
        String nativeSymbol = pair.getNativeSymbol();
        return nativeSymbol == null || nativeSymbol.isBlank()
                ? null
                : instrumentsBySymbol.get(nativeSymbol.trim().toUpperCase(Locale.ROOT));
    }

    private MarketInstrument resolveInstrument(SymbolAgentState state) {
        if (state == null) {
            return null;
        }
        MarketInstrument stateInstrument = state.getMarketInstrument();
        if (stateInstrument != null) {
            MarketInstrument byIdentity = instrumentsBySymbol.get(instrumentKey(stateInstrument));
            return byIdentity == null ? stateInstrument : byIdentity;
        }
        return resolveInstrument(state.getSymbol());
    }

    private void applyProductFilterToTable() {
        MarketWatchProductFilter filter = activeProductFilter.get();
        List<MarketWatchRow> rows = rowCache.values().stream()
                .filter(row -> filter == null || filter.accepts(row.getMarketInstrument()))
                .toList();
        table.getItems().setAll(rows);
    }

    /**
     * Shutdown the panel and cleanup resources.
     */
    public void shutdown() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        refreshScheduler.stop();
    }

    /**
     * Export market watch data to CSV file
     */
    private void exportToCSV() {
        try {
            // Create CSV header
            StringBuilder csv = new StringBuilder();
            csv.append("Symbol,Bid,Ask,Spread %,Session,Trading Mode,Strategy,Timeframe,Score,Live Ready,Issue\n");

            // Add rows
            for (MarketWatchRow row : rowCache.values()) {
                csv.append(String.format("%s,%.5f,%.5f,%.4f,%s,%s,%s,%s,%.2f,%s,%s\n",
                        row.getDisplaySymbol(),
                        row.getBid(),
                        row.getAsk(),
                        row.getSpreadPercent(),
                        row.getSession(),
                        row.getTradingMode(),
                        row.getActiveStrategy(),
                        row.getActiveTimeframe(),
                        row.getStrategyScore(),
                        row.isLiveReady(),
                        row.getIssue() == null ? "" : row.getIssue()));
            }

            // Save to file
            java.nio.file.Path exportPath = java.nio.file.Paths.get(System.getProperty("user.home"),
                    "Downloads",
                    "MarketWatch_" + java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");

            java.nio.file.Files.writeString(exportPath, csv.toString());
            log.info("Market watch data exported to {}", exportPath);
        } catch (Exception e) {
            log.error("Failed to export market watch data", e);
        }
    }

    private static @NotNull String symbolKey(@NotNull TradePair symbol) {
        return symbol.toString('/').trim().toUpperCase(Locale.ROOT);
    }

    private static @NotNull String stateKey(@NotNull SymbolAgentState state) {
        MarketInstrument instrument = state.getMarketInstrument();
        if (instrument != null) {
            return instrumentKey(instrument);
        }
        return symbolKey(state.getSymbol());
    }

    private static @NotNull String instrumentKey(@NotNull MarketInstrument instrument) {
        String exchange = safeKey(instrument.exchangeId());
        String routingExchange = safeKey(instrument.routingExchange());
        String nativeSymbol = safeKey(instrument.nativeSymbol());
        String pair = instrument.tradePair() == null ? "" : symbolKey(instrument.tradePair());
        String symbol = nativeSymbol.isBlank() ? pair : nativeSymbol;
        return String.join("|", exchange, routingExchange, symbol);
    }

    private static String safeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
