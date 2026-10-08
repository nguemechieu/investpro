package org.investpro.ui.panels;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.market.MarketInstrumentService;
import org.investpro.ui.market.ConnectedMarketWatchModel;
import org.investpro.ui.market.MarketWatchSymbolFormatter;
import java.util.*;
import java.util.function.*;

public final class ConnectedMarketWatchPanel extends VBox implements AutoCloseable {
    private final Supplier<Map<String, Exchange>> connected;
    private final ConnectedMarketWatchModel model = new ConnectedMarketWatchModel(new MarketInstrumentService());
    private final TableView<ConnectedMarketWatchModel.Row> table = new TableView<>();
    private final Label status = new Label();
    private final Timeline refresh;
    private boolean loading;
    private boolean closed;

    public ConnectedMarketWatchPanel(Supplier<Map<String, Exchange>> connected,
                                    BiConsumer<Exchange, TradePair> openChart) {
        this.connected = connected;
        var items = FXCollections.<ConnectedMarketWatchModel.Row>observableArrayList();
        var filtered = new FilteredList<>(items);
        table.setItems(filtered);
        addColumn("Exchange", ConnectedMarketWatchModel.Row::exchangeName);
        addColumn("Symbol", row -> MarketWatchSymbolFormatter.displaySymbol(row.instrument(), row.instrument().tradePair()));
        addColumn("Market", row -> row.instrument().marketBadge());
        addColumn("Status", row -> row.instrument().tradability() == null ? "Unknown"
                : row.instrument().tradability().status().name());
        table.setPlaceholder(new Label("Connect an exchange to load its symbols"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        var search = new TextField();
        search.setPromptText("Search all connected exchanges");
        search.textProperty().addListener((_, _, value) -> {
            String query = value == null ? "" : value.toUpperCase(Locale.ROOT);
            filtered.setPredicate(row -> (row.exchangeName() + " " + row.instrument().nativeSymbol()
                    + " " + row.instrument().displaySymbol()).toUpperCase(Locale.ROOT).contains(query));
        });
        Runnable open = () -> {
            var row = table.getSelectionModel().getSelectedItem();
            if (row != null && connected.get().containsValue(row.exchange()))
                openChart.accept(row.exchange(), row.instrument().tradePair());
        };
        var chart = new Button("Open chart");
        chart.setOnAction(_ -> open.run());
        table.setOnMouseClicked(event -> { if (event.getClickCount() == 2) open.run(); });
        var reload = new Button("Refresh");
        reload.setOnAction(_ -> refresh(items));
        getChildren().setAll(new HBox(6, chart, reload), search, status, table);
        setSpacing(6);
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh = new Timeline(new KeyFrame(Duration.seconds(10), _ -> refresh(items)));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        refresh(items);
    }

    private void addColumn(String name, Function<ConnectedMarketWatchModel.Row, String> text) {
        var column = new TableColumn<ConnectedMarketWatchModel.Row, String>(name);
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        table.getColumns().add(column);
    }

    private void refresh(javafx.collections.ObservableList<ConnectedMarketWatchModel.Row> items) {
        if (closed || loading) return;
        Map<String, Exchange> sources = Map.copyOf(connected.get());
        loading = true;
        model.load(sources).whenComplete((snapshot, error) -> Platform.runLater(() -> {
            loading = false;
            if (closed) return;
            if (error != null) { status.setText("Unable to refresh symbols"); return; }
            Map<String, Exchange> current = connected.get();
            var rows = new ArrayList<>(snapshot.rows().stream()
                    .filter(row -> current.get(row.exchangeName()) == row.exchange()).toList());
            // Preserve the last successful catalog during temporary venue failures.
            items.stream().filter(row -> snapshot.failedExchanges().contains(row.exchangeName())
                    && current.get(row.exchangeName()) == row.exchange()).forEach(rows::add);
            items.setAll(rows);
            status.setText(rows.size() + " symbols / " + current.size() + " exchanges"
                    + (snapshot.failedExchanges().isEmpty() ? "" : " — unavailable: " + String.join(", ", snapshot.failedExchanges())));
        }));
    }

    @Override public void close() { closed = true; refresh.stop(); }
}
