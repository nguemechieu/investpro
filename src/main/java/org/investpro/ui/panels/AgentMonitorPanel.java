package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.agent.TradingAgentManager;
import org.investpro.agent.symbol.SymbolAgent;
import org.investpro.agent.symbol.SymbolAgentState;

@Slf4j
public class AgentMonitorPanel extends BorderPane {

    private final TradingAgentManager manager;
    private final ObservableList<SymbolAgentState> rows = FXCollections.observableArrayList();
    private final TableView<SymbolAgentState> table = new TableView<>();

    public AgentMonitorPanel() {
        this(new TradingAgentManager());
    }

    public AgentMonitorPanel(TradingAgentManager manager) {
        this.manager = manager;
        setPadding(new Insets(12));
        getStyleClass().add("agent-monitor-panel");
        setTop(toolbar());
        setCenter(table());
        refresh();
    }

    private HBox toolbar() {
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        Button pause = new Button("Pause");
        pause.setOnAction(event -> selectedAgent().ifPresent(agent -> {
            agent.pause();
            refresh();
        }));
        Button resume = new Button("Resume");
        resume.setOnAction(event -> selectedAgent().ifPresent(agent -> {
            agent.resume();
            refresh();
        }));
        Button stop = new Button("Stop");
        stop.setOnAction(event -> selectedAgent().ifPresent(agent -> {
            manager.stopAgent(agent.state().exchangeId(), agent.pair());
            refresh();
        }));
        Button evaluate = new Button("Evaluate Now");
        evaluate.setOnAction(event -> selectedAgent().ifPresent(agent ->
                agent.evaluateNow("manual-monitor").whenComplete((ignored, throwable) -> Platform.runLater(this::refresh))));
        Button review = new Button("Review Strategy");
        review.setOnAction(event -> selectedAgent().ifPresent(agent ->
                agent.reviewStrategyNow("manual-monitor").whenComplete((ignored, throwable) -> Platform.runLater(this::refresh))));

        HBox box = new HBox(8, refresh, pause, resume, stop, evaluate, review);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private TableView<SymbolAgentState> table() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        addColumn("Exchange", state -> state.exchangeId());
        addColumn("Symbol", state -> state.pair() == null ? "" : state.pair().toString('/'));
        addColumn("Status", state -> state.status().name());
        addColumn("Mode", state -> state.mode().name());
        addColumn("Assigned Strategy", state -> state.assignedStrategy() == null ? "" : state.assignedStrategy().getName());
        addColumn("Last Signal", SymbolAgentState::lastSignal);
        addColumn("Candles", state -> String.valueOf(state.candlesLoaded()));
        addColumn("Tradable", state -> String.valueOf(state.tradable()));
        addColumn("Open Position", state -> String.valueOf(state.hasOpenPosition()));
        addColumn("Pending Order", state -> String.valueOf(state.hasPendingOrder()));
        addColumn("Last Evaluation", state -> state.lastEvaluationAt() == null ? "" : state.lastEvaluationAt().toString());
        addColumn("Error", SymbolAgentState::lastError);
        return table;
    }

    private void addColumn(String title, java.util.function.Function<SymbolAgentState, String> value) {
        TableColumn<SymbolAgentState, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        table.getColumns().add(column);
    }

    private java.util.Optional<SymbolAgent> selectedAgent() {
        SymbolAgentState selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || selected.pair() == null) {
            return java.util.Optional.empty();
        }
        return manager.getAgent(selected.exchangeId(), selected.pair());
    }

    private void refresh() {
        rows.setAll(manager.listAgents().stream().map(SymbolAgent::state).toList());
    }
}
