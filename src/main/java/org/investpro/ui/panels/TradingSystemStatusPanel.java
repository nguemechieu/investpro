package org.investpro.ui.panels;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.monitoring.SystemAlert;
import org.investpro.monitoring.TradingSystemStatusSnapshot;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Trading System Status Panel - displays comprehensive system health and
 * operational metrics.
 * Organized into multiple tabs for different aspects of system monitoring.
 */
@Slf4j
@Data
public class TradingSystemStatusPanel extends VBox {

    private TradingSystemStatusSnapshot snapshot;
    private final Supplier<TradingSystemStatusSnapshot> snapshotSupplier;
    private final TabPane tabPane;
    private final Label systemHealthLabel;
    private final Timeline autoRefreshTimeline;

    private Label activeStrategiesValue;
    private Label bestStrategyValue;
    private Label worstStrategyValue;
    private Label lastSignalValue;
    private TableView<TradingSystemStatusSnapshot.StrategyStatus> strategyTable;

    public TradingSystemStatusPanel(TradingSystemStatusSnapshot snapshot) {
        this(() -> snapshot);
    }

    public TradingSystemStatusPanel(Supplier<TradingSystemStatusSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
        this.snapshot = snapshotSupplier.get();
        this.tabPane = new TabPane();
        this.systemHealthLabel = new Label();
        this.autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), ignored -> refreshSnapshot()));
        this.autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        initializePanel();
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                autoRefreshTimeline.stop();
            } else {
                refreshSnapshot();
                autoRefreshTimeline.play();
            }
        });
    }

    private void initializePanel() {
        setStyle("-fx-background-color: #0f172a; -fx-text-fill: #f1f5f9;");
        setPadding(new Insets(12));
        setSpacing(12);

        // Top banner with quick system status
        VBox banner = createSystemBanner();

        // Tabbed interface for detailed status
        tabPane.setStyle("""
                -fx-control-inner-background: #1e293b;
                -fx-background-color: #0f172a;
                -fx-text-fill: #f1f5f9;
                -fx-tab-min-height: 32;
                -fx-padding: 8;
                """);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Add all tabs
        tabPane.getTabs().addAll(
                createSystemOverviewTab(),
                createBrokerHealthTab(),
                createAccountTab(),
                createRiskTab(),
                createStrategiesTab(),
                createAiTab(),
                createMarketSessionsTab(),
                createDataQualityTab(),
                createEventBusTab(),
                createAlertsTab());

        // Control buttons at bottom
        HBox controlBox = createControlBox();

        getChildren().addAll(
                banner,
                new Separator(),
                tabPane,
                new Separator(),
                controlBox);
    }

    private VBox createSystemBanner() {
        VBox banner = new VBox(8);
        banner.setStyle("""
                -fx-background-color: #1e293b;
                -fx-border-color: #475569;
                -fx-border-width: 1;
                -fx-padding: 12;
                -fx-border-radius: 6;
                """);
        banner.setPrefHeight(120);

        // Top row: System state, Broker, Mode, Risk
        HBox topRow = new HBox(20);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPrefHeight(40);

        VBox systemStateLabel = createStatusLabel("System",
                snapshot.systemState().getLabel(),
                snapshot.systemState().getColorHex());
        VBox brokerLabel = createStatusLabel("Broker", snapshot.brokerName(), "#3b82f6");
        VBox modeLabel = createStatusLabel("Mode", snapshot.tradingMode(), "#f59e0b");
        VBox riskLabel = createStatusLabel("Risk", snapshot.riskStatus().getLabel(),
                snapshot.riskStatus().getColorHex());

        topRow.getChildren().addAll(
                systemStateLabel, new Separator(),
                brokerLabel, new Separator(),
                modeLabel, new Separator(),
                riskLabel);

        // Middle row: Key metrics
        HBox middleRow = new HBox(20);
        middleRow.setAlignment(Pos.CENTER_LEFT);

        Label autoTradingLabel = createInfoLabel(
                snapshot.autoTradingEnabled() ? "✓ Auto Trading" : "✗ Auto Trading",
                snapshot.autoTradingEnabled() ? "#10b981" : "#94a3b8");
        Label killSwitchLabel = createInfoLabel(
                snapshot.killSwitchArmed() ? "✓ Kill Switch Armed" : "✗ Kill Switch Disarmed",
                snapshot.killSwitchArmed() ? "#10b981" : "#ef4444");
        Label uptimeLabel = createInfoLabel(
                "Uptime: " + formatDuration(snapshot.uptimeSeconds()),
                "#38bdf8");

        middleRow.getChildren().addAll(autoTradingLabel, new Separator(), killSwitchLabel, new Separator(),
                uptimeLabel);

        // Bottom row: Health score and last heartbeat
        HBox bottomRow = new HBox(20);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        systemHealthLabel.setText(String.format("System Health: %.1f%%", snapshot.systemHealthScore()));
        systemHealthLabel.setStyle("""
                -fx-font-size: 12pt;
                -fx-font-weight: bold;
                -fx-text-fill:\s""" + getHealthColor(snapshot.systemHealthScore()) + ";");

        String heartbeatAge = formatDuration(java.time.Duration.between(snapshot.lastHeartbeat(), Instant.now()).getSeconds());
        Label heartbeatLabel = createInfoLabel("Last Heartbeat: " + heartbeatAge + " ago", "#38bdf8");

        bottomRow.getChildren().addAll(systemHealthLabel, new Separator(), heartbeatLabel);

        banner.getChildren().addAll(topRow, middleRow, bottomRow);
        return banner;
    }

    private Tab createSystemOverviewTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("System State:", true), 0, row);
        grid.add(createLabel(snapshot.systemState().getLabel(), false), 1, row);

        grid.add(createLabel("Trading Mode:", true), 2, row);
        grid.add(createLabel(snapshot.tradingMode(), false), 3, row++);

        grid.add(createLabel("Broker:", true), 0, row);
        grid.add(createLabel(snapshot.brokerName(), false), 1, row);

        grid.add(createLabel("Venue:", true), 2, row);
        grid.add(createLabel(snapshot.activeVenue(), false), 3, row++);

        grid.add(createLabel("Connected Since:", true), 0, row);
        grid.add(createLabel(formatInstant(snapshot.connectedSince()), false), 1, row);

        grid.add(createLabel("Uptime:", true), 2, row);
        grid.add(createLabel(formatDuration(snapshot.uptimeSeconds()), false), 3, row++);

        grid.add(createLabel("Auto Trading:", true), 0, row);
        grid.add(createLabel(snapshot.autoTradingEnabled() ? "Enabled" : "Disabled", false), 1, row);

        grid.add(createLabel("Kill Switch:", true), 2, row);
        grid.add(createLabel(snapshot.killSwitchArmed() ? "Armed" : "Disarmed", false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("System Overview", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createBrokerHealthTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("REST API:", true), 0, row);
        grid.add(createStatusBadge(snapshot.restApiConnected()), 1, row);

        grid.add(createLabel("WebSocket:", true), 2, row);
        grid.add(createStatusBadge(snapshot.webSocketConnected()), 3, row++);

        grid.add(createLabel("Ticker Stream:", true), 0, row);
        grid.add(createStatusBadge(snapshot.tickerStreamActive()), 1, row);

        grid.add(createLabel("Candle Stream:", true), 2, row);
        grid.add(createStatusBadge(snapshot.candleStreamActive()), 3, row++);

        grid.add(createLabel("Order Book Stream:", true), 0, row);
        grid.add(createStatusBadge(snapshot.orderBookStreamActive()), 1, row);

        grid.add(createLabel("Account Stream:", true), 2, row);
        grid.add(createStatusBadge(snapshot.accountStreamActive()), 3, row++);

        grid.add(createLabel("Latency:", true), 0, row);
        grid.add(createLabel(snapshot.latencyMillis() + " ms", false), 1, row);

        grid.add(createLabel("Rate Limit:", true), 2, row);
        grid.add(createLabel(snapshot.rateLimitStatus(), false), 3, row++);

        grid.add(createLabel("Reconnects:", true), 0, row);
        grid.add(createLabel(String.valueOf(snapshot.reconnectCount()), false), 1, row);

        grid.add(createLabel("Last Market Tick:", true), 2, row);
        grid.add(createLabel(formatInstant(snapshot.lastMarketTick()), false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Broker Health", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createAccountTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("Balance:", true), 0, row);
        grid.add(createLabel(formatCurrency(snapshot.balance()), false), 1, row);

        grid.add(createLabel("Equity:", true), 2, row);
        grid.add(createLabel(formatCurrency(snapshot.equity()), false), 3, row++);

        grid.add(createLabel("Available Balance:", true), 0, row);
        grid.add(createLabel(formatCurrency(snapshot.availableBalance()), false), 1, row);

        grid.add(createLabel("Margin Used:", true), 2, row);
        grid.add(createLabel(String.format("%.2f%%", snapshot.marginUsed() * 100), false), 3, row++);

        grid.add(createLabel("Free Margin:", true), 0, row);
        grid.add(createLabel(formatCurrency(snapshot.freeMargin()), false), 1, row);

        grid.add(createLabel("Open Positions:", true), 2, row);
        grid.add(createLabel(String.valueOf(snapshot.openPositionCount()), false), 3, row++);

        grid.add(createLabel("Open Orders:", true), 0, row);
        grid.add(createLabel(String.valueOf(snapshot.openOrderCount()), false), 1, row);

        grid.add(createLabel("Unrealized P&L:", true), 2, row);
        grid.add(createLabel(formatCurrency(snapshot.unrealizedPnl()), false), 3, row++);

        grid.add(createLabel("Realized P&L Today:", true), 0, row);
        grid.add(createLabel(formatCurrency(snapshot.realizedPnlToday()), false), 1, row);

        grid.add(createLabel("Fees & Commission:", true), 2, row);
        grid.add(createLabel(formatCurrency(snapshot.feesAndCommission()), false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Account", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createRiskTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("Risk Status:", true), 0, row);
        grid.add(createLabel(snapshot.riskStatus().getLabel(), false), 1, row);

        grid.add(createLabel("Daily Loss:", true), 2, row);
        grid.add(createLabel(
                String.format("$%.2f / $%.2f", snapshot.dailyLoss(), snapshot.maxDailyLoss()),
                false), 3, row++);

        grid.add(createLabel("Portfolio Heat:", true), 0, row);
        grid.add(createLabel(String.format("%.2f%%", snapshot.portfolioHeat() * 100), false), 1, row);

        grid.add(createLabel("Max Drawdown:", true), 2, row);
        grid.add(createLabel(String.format("%.2f%% / %.2f%%",
                snapshot.currentDrawdown() * 100, snapshot.maxDrawdown() * 100), false), 3, row++);

        grid.add(createLabel("Concentration Risk:", true), 0, row);
        grid.add(createLabel(snapshot.concentrationRisk(), false), 1, row);

        grid.add(createLabel("Correlation Risk:", true), 2, row);
        grid.add(createLabel(snapshot.correlationRisk(), false), 3, row++);

        grid.add(createLabel("Last Risk Decision:", true), 0, row);
        grid.add(createLabel(snapshot.lastRiskDecision(), false), 1, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Risk", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createStrategiesTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        // Summary section
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(20);
        summaryGrid.setVgap(12);
        summaryGrid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        activeStrategiesValue = createLabel(String.valueOf(snapshot.activeStrategies()), false);
        bestStrategyValue = createLabel(snapshot.bestStrategyToday(), false);
        worstStrategyValue = createLabel(snapshot.worstStrategyToday(), false);
        lastSignalValue = createLabel(snapshot.lastSignal(), false);

        summaryGrid.add(createLabel("Active Strategies:", true), 0, 0);
        summaryGrid.add(activeStrategiesValue, 1, 0);

        summaryGrid.add(createLabel("Best Today:", true), 2, 0);
        summaryGrid.add(bestStrategyValue, 3, 0);

        summaryGrid.add(createLabel("Worst Today:", true), 0, 1);
        summaryGrid.add(worstStrategyValue, 1, 1);

        summaryGrid.add(createLabel("Last Signal:", true), 2, 1);
        summaryGrid.add(lastSignalValue, 3, 1);

        // Strategy table
        strategyTable = new TableView<>();
        strategyTable.setStyle("""
                -fx-control-inner-background: #1e293b;
                -fx-background-color: #0f172a;
                -fx-text-fill: #f1f5f9;
                """);

        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> symbolCol = new TableColumn<>("Symbol");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> strategyCol = new TableColumn<>("Strategy");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> modeCol = new TableColumn<>("Mode");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> signalCol = new TableColumn<>("Signal");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> confidenceCol = new TableColumn<>("Confidence");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> pnlCol = new TableColumn<>("P&L");
        TableColumn<TradingSystemStatusSnapshot.StrategyStatus, String> statusCol = new TableColumn<>("Status");

        symbolCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(cv.getValue().symbol()));
        strategyCol.setCellValueFactory(
                cv -> new javafx.beans.property.SimpleStringProperty(cv.getValue().strategyName()));
        modeCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(cv.getValue().mode()));
        signalCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(cv.getValue().lastSignal()));
        confidenceCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.0f%%", cv.getValue().confidence() * 100)));
        pnlCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cv.getValue().pnl())));
        statusCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(cv.getValue().status()));

        strategyTable.getColumns().addAll(symbolCol, strategyCol, modeCol, signalCol, confidenceCol, pnlCol, statusCol);
        strategyTable.getItems().addAll(snapshot.strategyStatus());
        strategyTable.setPrefHeight(250);

        content.getChildren().addAll(summaryGrid, new Label(""), new Label("Strategy Details"), strategyTable);
        Tab tab = new Tab("Strategies", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createAiTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("AI Provider:", true), 0, row);
        grid.add(createLabel(snapshot.aiProvider(), false), 1, row);

        grid.add(createLabel("AI Enabled:", true), 2, row);
        grid.add(createLabel(snapshot.aiEnabled() ? "Yes" : "No", false), 3, row++);

        grid.add(createLabel("Review Mode:", true), 0, row);
        grid.add(createLabel(snapshot.aiReviewMode(), false), 1, row);

        grid.add(createLabel("Confidence Threshold:", true), 2, row);
        grid.add(createLabel(String.format("%.0f%%", snapshot.confidenceThreshold() * 100), false), 3, row++);

        grid.add(createLabel("Last Decision:", true), 0, row);
        grid.add(createLabel(snapshot.lastAiDecision(), false), 1, row);

        grid.add(createLabel("Learning Engine:", true), 2, row);
        grid.add(createLabel(snapshot.learningEngineActive() ? "Active" : "Inactive", false), 3, row++);

        grid.add(createLabel("Feedback Samples:", true), 0, row);
        grid.add(createLabel(String.valueOf(snapshot.feedbackSamples()), false), 1, row);

        grid.add(createLabel("Prompt Version:", true), 2, row);
        grid.add(createLabel(snapshot.promptVersion(), false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("AI/Reasoning", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createMarketSessionsTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("Market Status:", true), 0, row);
        grid.add(createLabel(snapshot.primaryMarketStatus(), false), 1, row);

        grid.add(createLabel("Session:", true), 2, row);
        grid.add(createLabel(snapshot.sessionName(), false), 3, row++);

        grid.add(createLabel("Liquidity:", true), 0, row);
        grid.add(createLabel(snapshot.liquidityCondition(), false), 1, row);

        grid.add(createLabel("Rollover Risk:", true), 2, row);
        grid.add(createLabel(snapshot.rolloverRiskActive() ? "Yes" : "No", false), 3, row++);

        grid.add(createLabel("News Lockout:", true), 0, row);
        grid.add(createLabel(snapshot.newsLockoutActive() ? "Active" : "Inactive", false), 1, row);

        grid.add(createLabel("Time to Close:", true), 2, row);
        grid.add(createLabel(formatDuration(snapshot.timeToMarketCloseSeconds()), false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Market Sessions", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createDataQualityTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("Candles Loaded:", true), 0, row);
        grid.add(createLabel(
                String.format("%d / %d", snapshot.candlesLoaded(), snapshot.minimumCandlesRequired()),
                false), 1, row);

        grid.add(createLabel("Indicator Warmup:", true), 2, row);
        grid.add(createLabel(snapshot.indicatorWarmupComplete() ? "Complete" : "Pending", false), 3, row++);

        grid.add(createLabel("Missing Gaps:", true), 0, row);
        grid.add(createLabel(String.valueOf(snapshot.missingCandleGaps()), false), 1, row);

        grid.add(createLabel("Backtest Ready:", true), 2, row);
        grid.add(createLabel(snapshot.backtestReady() ? "Yes" : "No", false), 3, row++);

        grid.add(createLabel("Paper Test Ready:", true), 0, row);
        grid.add(createLabel(snapshot.paperTestReady() ? "Yes" : "No", false), 1, row);

        grid.add(createLabel("Live Ready:", true), 2, row);
        grid.add(createLabel(snapshot.liveReady() ? "Yes" : "No", false), 3, row++);

        grid.add(createLabel("Last Update:", true), 0, row);
        grid.add(createLabel(formatInstant(snapshot.lastDataUpdate()), false), 1, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Data Quality", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createEventBusTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setStyle("-fx-background-color: #1e293b; -fx-padding: 12; -fx-border-radius: 6;");

        int row = 0;
        grid.add(createLabel("Event Bus:", true), 0, row);
        grid.add(createLabel(snapshot.eventBusRunning() ? "Running" : "Stopped", false), 1, row);

        grid.add(createLabel("Queue Size:", true), 2, row);
        grid.add(createLabel(String.valueOf(snapshot.eventQueueSize()), false), 3, row++);

        grid.add(createLabel("Events/sec:", true), 0, row);
        grid.add(createLabel(String.format("%.1f", snapshot.eventsPerSecond()), false), 1, row);

        grid.add(createLabel("Dropped Events:", true), 2, row);
        grid.add(createLabel(String.valueOf(snapshot.droppedEvents()), false), 3, row++);

        grid.add(createLabel("Dead Letter Queue:", true), 0, row);
        grid.add(createLabel(String.valueOf(snapshot.deadLetterQueueSize()), false), 1, row);

        grid.add(createLabel("Active Subscribers:", true), 2, row);
        grid.add(createLabel(String.valueOf(snapshot.activeSubscribers()), false), 3, row++);

        grid.add(createLabel("Last Event:", true), 0, row);
        grid.add(createLabel(snapshot.lastEventType(), false), 1, row);

        grid.add(createLabel("Replay Available:", true), 2, row);
        grid.add(createLabel(snapshot.replayAvailable() ? "Yes" : "No", false), 3, row);

        content.getChildren().add(grid);
        Tab tab = new Tab("Event Bus", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createAlertsTab() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f172a; -fx-padding: 12;");

        TableView<SystemAlert> alertTable = new TableView<>();
        alertTable.setStyle("""
                -fx-control-inner-background: #1e293b;
                -fx-background-color: #0f172a;
                -fx-text-fill: #f1f5f9;
                """);

        TableColumn<SystemAlert, String> severityCol = new TableColumn<>("Severity");
        TableColumn<SystemAlert, String> sourceCol = new TableColumn<>("Source");
        TableColumn<SystemAlert, String> messageCol = new TableColumn<>("Message");
        TableColumn<SystemAlert, String> timeCol = new TableColumn<>("Time");

        severityCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                cv.getValue().severity().name()));
        sourceCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                cv.getValue().source()));
        messageCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                cv.getValue().message()));
        timeCol.setCellValueFactory(cv -> new javafx.beans.property.SimpleStringProperty(
                formatInstant(cv.getValue().timestamp())));

        alertTable.getColumns().addAll(severityCol, sourceCol, messageCol, timeCol);
        alertTable.getItems().addAll(snapshot.alerts());
        alertTable.setPrefHeight(300);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button clearButton = new Button("Clear Non-Critical");
        clearButton.setStyle("""
                -fx-padding: 8 16;
                -fx-background-color: #94a3b8;
                -fx-text-fill: white;
                -fx-cursor: hand;
                """);
        clearButton.setOnAction(e -> {
            alertTable.getItems().removeIf(alert -> alert.severity() != SystemAlert.AlertSeverity.CRITICAL);
        });

        buttonBox.getChildren().add(clearButton);
        content.getChildren().addAll(alertTable, buttonBox);

        Tab tab = new Tab("Alerts", content);
        tab.setClosable(false);
        return tab;
    }

    private @NonNull HBox createControlBox() {
        HBox controlBox = new HBox(12);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.setStyle("-fx-padding: 12;");

        // Safe buttons (no confirmation needed)
        Button startButton = createButton("Start System", "#10b981");
        Button pauseButton = createButton("Pause Trading", "#f59e0b");
        Button resumeButton = createButton("Resume Trading", "#38bdf8");
        Button refreshButton = createButton("Refresh Account", "#38bdf8");
        Button reconnectButton = createButton("Reconnect Broker", "#38bdf8");
        Button healthCheckButton = createButton("Run Health Check", "#38bdf8");

        // Dangerous buttons (require confirmation)
        Button stopButton = createDangerButton("Stop Bot", "#ef4444");
        Button emergencyButton = createDangerButton("Emergency Stop", "#dc2626");
        Button cancelAllButton = createDangerButton("Cancel All Orders", "#ef4444");
        Button closeAllButton = createDangerButton("Close All Positions", "#ef4444");

        startButton.setOnAction(e -> log.info("Start System clicked"));
        pauseButton.setOnAction(e -> log.info("Pause Trading clicked"));
        resumeButton.setOnAction(e -> log.info("Resume Trading clicked"));
        stopButton.setOnAction(e -> confirmAndExecute("Stop the bot?", "Stopping bot..."));
        emergencyButton
                .setOnAction(e -> confirmAndExecute("EMERGENCY STOP - Are you sure?", "Emergency stop activated!"));
        cancelAllButton.setOnAction(e -> confirmAndExecute("Cancel ALL pending orders?", "Cancelling orders..."));
        closeAllButton.setOnAction(e -> confirmAndExecute("Close ALL open positions?", "Closing positions..."));
        refreshButton.setOnAction(e -> log.info("Refresh Account clicked"));
        reconnectButton.setOnAction(e -> log.info("Reconnect Broker clicked"));
        healthCheckButton.setOnAction(e -> log.info("Health Check started"));

        controlBox.getChildren().addAll(
                startButton, pauseButton, resumeButton, stopButton,
                new Separator(),
                emergencyButton, cancelAllButton, closeAllButton,
                new Separator(),
                refreshButton, reconnectButton, healthCheckButton);

        return controlBox;
    }

    private Button createButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(String.format("""
                -fx-padding: 8 16;
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-cursor: hand;
                -fx-font-weight: bold;
                """, color));
        return button;
    }

    private Button createDangerButton(String text, String color) {
        Button button = createButton(text, color);
        button.setStyle(button.getStyle() + """
                -fx-border-width: 2;
                -fx-border-color: #ef4444;
                """);
        return button;
    }

    private void confirmAndExecute(String message, String successMessage) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Action");
        alert.setContentText(message);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            log.info(successMessage);
        }
    }

    private void refreshSnapshot() {
        try {
            TradingSystemStatusSnapshot refreshed = snapshotSupplier.get();
            if (refreshed == null) {
                return;
            }
            snapshot = refreshed;
            updateStrategiesTab();
            systemHealthLabel.setText(String.format("System Health: %.1f%%", snapshot.systemHealthScore()));
            systemHealthLabel.setStyle("""
                    -fx-font-size: 12pt;
                    -fx-font-weight: bold;
                    -fx-text-fill:\s""" + getHealthColor(snapshot.systemHealthScore()) + ";");
        } catch (Exception exception) {
            log.debug("Unable to auto-refresh trading system status snapshot: {}", exception.getMessage(), exception);
        }
    }

    private void updateStrategiesTab() {
        if (activeStrategiesValue != null) {
            activeStrategiesValue.setText(String.valueOf(snapshot.activeStrategies()));
        }
        if (bestStrategyValue != null) {
            bestStrategyValue.setText(snapshot.bestStrategyToday());
        }
        if (worstStrategyValue != null) {
            worstStrategyValue.setText(snapshot.worstStrategyToday());
        }
        if (lastSignalValue != null) {
            lastSignalValue.setText(snapshot.lastSignal());
        }
        if (strategyTable != null) {
            strategyTable.getItems().setAll(snapshot.strategyStatus());
        }
    }

    // Utility helper methods
    private VBox createStatusLabel(String title, String value, String color) {
        VBox box = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 10pt; -fx-text-fill: #94a3b8;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format("""
                -fx-font-size: 14pt;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """, color));
        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private Label createInfoLabel(String text, String color) {
        Label label = new Label(text);
        label.setStyle(String.format("""
                -fx-font-size: 11pt;
                -fx-text-fill: %s;
                """, color));
        return label;
    }

    private Label createStatusBadge(boolean status) {
        Label badge = new Label(status ? "✓ OK" : "✗ OFFLINE");
        badge.setStyle(String.format("""
                -fx-padding: 4 8;
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-border-radius: 4;
                -fx-background-radius: 4;
                """, status ? "#10b981" : "#ef4444"));
        return badge;
    }

    private Label createLabel(String text, boolean bold) {
        Label label = new Label(text);
        String style = "-fx-font-size: 11pt; -fx-text-fill: #f1f5f9;";
        if (bold) {
            style += " -fx-font-weight: bold; -fx-text-fill: #cbd5e1;";
        }
        label.setStyle(style);
        return label;
    }

    private String formatCurrency(double value) {
        return String.format("$%.2f", value);
    }

    private String formatInstant(Instant instant) {
        return instant.atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    private String getHealthColor(double healthScore) {
        if (healthScore >= 90)
            return "#10b981"; // Green
        if (healthScore >= 70)
            return "#f59e0b"; // Orange
        if (healthScore >= 50)
            return "#f97316"; // Dark Orange
        return "#ef4444"; // Red
    }
}
