package org.investpro.investpro.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.models.CoinInfo;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class CoinInfoUI extends VBox {
    private final TableView<CoinInfo> tableView;
    private final ObservableList<CoinInfo> coinInfoList;
    private final ScheduledExecutorService scheduler;
    private final Exchange exchange;
    private final Label statusLabel;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public CoinInfoUI(Exchange exchange) {
        this.exchange = exchange;
        this.tableView = createTableView();
        this.coinInfoList = FXCollections.observableArrayList();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.statusLabel = new Label("Loading market data...");

        setSpacing(12);
        setPadding(new Insets(12));
        getStyleClass().add("desk-table-panel");

        Label titleLabel = new Label("Market Data");
        titleLabel.getStyleClass().add("desk-section-title");

        statusLabel.getStyleClass().add("desk-section-status");
        tableView.setItems(coinInfoList);

        getChildren().addAll(titleLabel, statusLabel, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        fetchCoinData();
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                shutdown();
            }
        });
    }

    private @NotNull TableView<CoinInfo> createTableView() {
        TableView<CoinInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No market data available"));
        table.getColumns().addAll(
                textColumn("Rank", coin -> Integer.toString(coin.getMarket_cap_rank())),
                textColumn("Symbol", coin -> safe(coin.getSymbol()).toUpperCase()),
                textColumn("Name", coin -> safe(coin.getName())),
                textColumn("Price", coin -> String.format("$%,.6f", coin.getCurrent_price())),
                textColumn("24h Change", coin -> String.format("%.2f%%", coin.getPrice_change_percentage_24h())),
                textColumn("Market Cap", coin -> String.format("$%,d", coin.getMarket_cap())),
                textColumn("Volume", coin -> String.format("$%,d", coin.getTotal_volume())),
                textColumn("Updated", coin -> coin.getLast_updated() == null ? "-" : coin.getLast_updated().toString())
        );
        return table;
    }

    private TableColumn<CoinInfo, String> textColumn(String title, java.util.function.Function<CoinInfo, String> mapper) {
        TableColumn<CoinInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }

    private void fetchCoinData() {
        scheduler.scheduleAtFixedRate(() -> {
            if (disposed.get() || !FxLifecycle.isShowing(this)) {
                return;
            }

            try {
                List<CoinInfo> updatedCoinInfo = exchange.getCoinInfoList()
                        .stream()
                        .sorted(Comparator.comparingInt(CoinInfo::getMarket_cap_rank))
                        .toList();

                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    coinInfoList.setAll(updatedCoinInfo);
                    statusLabel.setText(updatedCoinInfo.isEmpty()
                            ? "No market data returned."
                            : "Updated " + LocalTime.now().withNano(0));
                });
            } catch (RuntimeException e) {
                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this),
                        () -> statusLabel.setText("Market data unavailable right now."));
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
    }
}
