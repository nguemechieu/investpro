package org.investpro.investpro.ui;

import javafx.application.Platform;
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
import org.investpro.investpro.model.Account;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class AccountSummaryUI extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(AccountSummaryUI.class);

    private final Exchange exchange;
    private final TableView<Account> tableView;
    private final Label statusLabel;
    private final ScheduledExecutorService scheduler;
    private final ObservableList<Account> accountSummaryList;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public AccountSummaryUI(Exchange exchange) {
        this.exchange = exchange;
        this.tableView = createTableView();
        this.statusLabel = new Label("Loading account summary...");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.accountSummaryList = FXCollections.observableArrayList();

        setSpacing(12);
        setPadding(new Insets(12));
        getStyleClass().add("desk-table-panel");

        Label titleLabel = new Label("Account Summary");
        titleLabel.getStyleClass().add("desk-section-title");

        statusLabel.getStyleClass().add("desk-section-status");
        tableView.setItems(accountSummaryList);

        getChildren().addAll(titleLabel, statusLabel, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        Platform.runLater(this::startUpdating);
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                shutdown();
            }
        });
        logger.info("Account summary initialized.");
    }

    private @NotNull TableView<Account> createTableView() {
        TableView<Account> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No account data available"));

        table.getColumns().addAll(
                textColumn("ID", account -> safe(account.getId())),
                textColumn("Alias", account -> safe(account.getAlias())),
                textColumn("Currency", account -> safe(account.getCurrency())),
                textColumn("Balance", account -> money(account.getBalance())),
                textColumn("Equity", account -> money(account.getEquity())),
                textColumn("Available Margin", account -> money(account.getMarginAvailable())),
                textColumn("Used Margin", account -> money(account.getMarginUsed())),
                textColumn("NAV", account -> money(account.getNAV())),
                textColumn("P/L", account -> money(account.getPl())),
                textColumn("Unrealized P/L", account -> money(account.getUnrealizedPL())),
                textColumn("Leverage", account -> account.getLeverage() + "x"),
                textColumn("Open Positions", account -> Integer.toString(account.getOpenPositionCount())),
                textColumn("Pending Orders", account -> Integer.toString(account.getPendingOrderCount()))
        );

        return table;
    }

    private TableColumn<Account, String> textColumn(String title, java.util.function.Function<Account, String> mapper) {
        TableColumn<Account, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }

    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            if (disposed.get() || !FxLifecycle.isShowing(this)) {
                return;
            }
            try {
                List<Account> updatedAccounts = exchange.getAccountSummary();

                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    accountSummaryList.setAll(updatedAccounts);
                    statusLabel.setText(updatedAccounts.isEmpty()
                            ? "No account data returned by " + exchange.getClass().getSimpleName() + "."
                            : "Updated " + java.time.LocalTime.now().withNano(0));
                });
            } catch (RuntimeException e) {
                logger.warn("Unable to refresh account summary for {}", exchange.getClass().getSimpleName(), e);
                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this),
                        () -> statusLabel.setText("Account summary unavailable right now."));
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private String money(double value) {
        return String.format("$%,.2f", value);
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
