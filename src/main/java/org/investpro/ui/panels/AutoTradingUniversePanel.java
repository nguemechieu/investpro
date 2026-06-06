package org.investpro.ui.panels;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import org.investpro.terminal.autotrading.AutoTradeSymbolState;
import org.investpro.terminal.autotrading.SymbolEligibility;
import org.investpro.terminal.autotrading.TradabilityFailureReason;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AutoTradingUniversePanel extends BorderPane {

    private final ObservableList<SymbolEligibility> rows = FXCollections.observableArrayList();
    private final TableView<SymbolEligibility> table = new TableView<>(rows);

    private Runnable globalToggleHandler = () -> { };
    private Runnable exchangeToggleHandler = () -> { };
    private Runnable symbolToggleHandler = () -> { };
    private Runnable blockSymbolHandler = () -> { };
    private Runnable forceRescanHandler = () -> { };
    private Runnable recheckTradabilityHandler = () -> { };
    private Runnable reassignStrategyHandler = () -> { };
    private Runnable pauseBotHandler = () -> { };
    private Runnable resumeAfterReconciliationHandler = () -> { };
    private Runnable viewDecisionAuditHandler = () -> { };

    public AutoTradingUniversePanel() {
        setPadding(new Insets(10));
        setTop(toolbar());
        setCenter(table);
        configureTable();
    }

    public void setRows(List<SymbolEligibility> eligibility) {
        rows.setAll(eligibility == null ? List.of() : eligibility);
    }

    public SymbolEligibility selectedEligibility() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void onGlobalToggle(Runnable handler) {
        globalToggleHandler = safe(handler);
    }

    public void onExchangeToggle(Runnable handler) {
        exchangeToggleHandler = safe(handler);
    }

    public void onSymbolToggle(Runnable handler) {
        symbolToggleHandler = safe(handler);
    }

    public void onBlockSymbol(Runnable handler) {
        blockSymbolHandler = safe(handler);
    }

    public void onForceRescan(Runnable handler) {
        forceRescanHandler = safe(handler);
    }

    public void onRecheckTradability(Runnable handler) {
        recheckTradabilityHandler = safe(handler);
    }

    public void onReassignStrategy(Runnable handler) {
        reassignStrategyHandler = safe(handler);
    }

    public void onPauseBot(Runnable handler) {
        pauseBotHandler = safe(handler);
    }

    public void onResumeAfterReconciliation(Runnable handler) {
        resumeAfterReconciliationHandler = safe(handler);
    }

    public void onViewDecisionAudit(Runnable handler) {
        viewDecisionAuditHandler = safe(handler);
    }

    private ToolBar toolbar() {
        CheckBox globalToggle = new CheckBox("Global");
        globalToggle.setOnAction(event -> globalToggleHandler.run());

        CheckBox exchangeToggle = new CheckBox("Exchange");
        exchangeToggle.setOnAction(event -> exchangeToggleHandler.run());

        CheckBox symbolToggle = new CheckBox("Symbol");
        symbolToggle.setOnAction(event -> symbolToggleHandler.run());

        Button block = button("Block", () -> blockSymbolHandler.run());
        Button rescan = button("Rescan", () -> forceRescanHandler.run());
        Button recheck = button("Recheck", () -> recheckTradabilityHandler.run());
        Button reassign = button("Strategy", () -> reassignStrategyHandler.run());
        Button pause = button("Pause", () -> pauseBotHandler.run());
        Button resume = button("Resume", () -> resumeAfterReconciliationHandler.run());
        Button audit = button("Audit", () -> viewDecisionAuditHandler.run());

        ToolBar toolBar = new ToolBar(
                globalToggle,
                exchangeToggle,
                symbolToggle,
                block,
                rescan,
                recheck,
                reassign,
                pause,
                resume,
                audit);
        toolBar.setPadding(new Insets(0, 0, 8, 0));
        return toolBar;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(
                column("Exchange", eligibility -> eligibility.instrument().id().providerId()),
                column("Symbol", eligibility -> eligibility.instrument().id().symbol()),
                column("Asset Class", eligibility -> String.valueOf(eligibility.instrument().assetClass())),
                column("Status", eligibility -> String.valueOf(eligibility.instrument().tradingStatus())),
                column("Tradable", eligibility -> String.valueOf(eligibility.tradable())),
                column("Failure", eligibility -> failure(eligibility.failureReason())),
                column("Strategy", SymbolEligibility::assignedStrategy),
                column("Score", eligibility -> eligibility.strategyScore() == null
                        ? ""
                        : String.format("%.2f", eligibility.strategyScore().score())),
                column("Signal", eligibility -> eligibility.latestSignal() == null
                        ? ""
                        : eligibility.latestSignal().action()),
                column("Spread", eligibility -> finite(eligibility.spreadPercent())),
                column("Liquidity", eligibility -> String.valueOf(eligibility.liquidityScore())),
                column("Volume", eligibility -> finite(eligibility.volume24h())),
                column("Data", SymbolEligibility::marketDataStatus),
                column("Bot State", eligibility -> state(eligibility.botState())),
                column("Orders", eligibility -> String.valueOf(eligibility.openOrders())),
                column("Position", eligibility -> String.valueOf(eligibility.openPositions())),
                column("Decision Time", eligibility -> eligibility.lastDecisionTime() == null
                        ? ""
                        : DateTimeFormatter.ISO_INSTANT.format(eligibility.lastDecisionTime())));
    }

    private TableColumn<SymbolEligibility, String> column(
            String title,
            java.util.function.Function<SymbolEligibility, String> valueFactory
    ) {
        TableColumn<SymbolEligibility, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(valueFactory.apply(cellData.getValue())));
        column.setMinWidth(80);
        return column;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Runnable safe(Runnable handler) {
        return handler == null ? () -> { } : handler;
    }

    private String finite(double value) {
        return Double.isFinite(value) ? String.format("%.4f", value) : "";
    }

    private String failure(TradabilityFailureReason reason) {
        return reason == null || reason == TradabilityFailureReason.NONE ? "" : reason.name();
    }

    private String state(AutoTradeSymbolState state) {
        return state == null ? "" : state.name();
    }
}
