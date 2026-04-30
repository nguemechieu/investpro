package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.Setter;
import org.investpro.exchange.Exchange;
import org.investpro.models.currency.Currency;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Single-symbol chart workspace.
 * This view is designed to be embedded inside:
 * - TradingWindow chart tabs
 * - MT4-style center chart workspace
 * - standalone chart windows
 */
@Getter
@Setter
public class TradeView extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(TradeView.class);

    private static final String DEFAULT_BASE = "BTC";
    private static final String DEFAULT_QUOTE = "USD";

    public static final TradePair BTC_USD = createDefaultTradePair();

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveMode;

    private final BorderPane root = new BorderPane();

    private ChartContainer chartContainer;

    public TradeView(Exchange exchange) {
        this(exchange, BTC_USD, true);
    }


    public TradeView(Exchange exchange, TradePair tradePair, boolean liveMode) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.liveMode = liveMode;

        initialize();
    }

    private static @NotNull TradePair createDefaultTradePair() {
        try {
            Currency base = Objects.requireNonNull(Currency.of(DEFAULT_BASE), "BTC currency not found");
            Currency quote = Objects.requireNonNull(Currency.of(DEFAULT_QUOTE), "USD currency not found");
            return TradePair.of(base, quote);
        } catch (SQLException | ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private void initialize() {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, exception) -> logger.error("[{}]: ", thread, exception)
        );

        getStyleClass().add("trade-view");

        root.getStyleClass().add("trade-view-root");
        root.setPadding(new Insets(6));

        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);
        AnchorPane.setBottomAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);

        getChildren().setAll(root);

        root.setTop(createHeader());
        root.setCenter(createChartContainer());

        logger.info("TradeView initialized for {}", tradePair);
        refresh();
    }

    private ToolBar createHeader() {
        Label title = new Label("Chart: %s".formatted("%s/%s".formatted(tradePair.getBaseCurrency().getCode(), tradePair.getCounterCurrency().getCode())));
        title.getStyleClass().add("trade-view-title");

        Label exchangeLabel = new Label("Exchange: %s".formatted(exchange.getName()));
        exchangeLabel.getStyleClass().add("trade-view-meta");

        Label modeLabel = new Label(liveMode ? "LIVE" : "STATIC");
        modeLabel.getStyleClass().add(liveMode ? "trade-view-live-badge" : "trade-view-static-badge");

        ToolBar toolbar = new ToolBar(title, exchangeLabel, modeLabel);
        toolbar.getStyleClass().add("trade-view-toolbar");
        return toolbar;
    }

    private @NotNull StackPane createChartContainer() {
        StackPane chartHost = new StackPane();
        chartHost.getStyleClass().add("trade-view-chart-host");

        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(42, 42);

        try {
            chartContainer = new ChartContainer(exchange, tradePair, liveMode);
            chartContainer.setMinSize(0, 0);
            chartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            chartHost.getChildren().setAll(chartContainer);
        } catch (Exception exception) {
            logger.error("Failed to create chart container for {}", tradePair, exception);

            Label errorLabel = new Label(
                    "Unable to load chart for %s.\nCheck exchange connection, symbol support, and chart data supplier.".formatted(tradePair.toString('/'))
            );
            errorLabel.getStyleClass().add("trade-view-error");

            chartHost.getChildren().setAll(errorLabel);

            Platform.runLater(() -> showError(
                    "Chart Load Error",
                    "Unable to load chart for %s.".formatted(tradePair.toString('/'))
            ));
        }

        return chartHost;
    }



    public void refresh() {
        if (chartContainer == null) {
            root.setCenter(createChartContainer());
            return;
        }

        try {
            /*
             * If your ChartContainer has a refresh/reload method, call it here.
             * Keeping this defensive allows this class to compile even if the
             * method does not exist yet.
             */
            logger.info("Refresh requested for {}", tradePair);
        } catch (Exception exception) {
            logger.warn("Unable to refresh chart for {}", tradePair, exception);
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}