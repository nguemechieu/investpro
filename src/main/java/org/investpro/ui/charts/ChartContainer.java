package org.investpro.ui.charts;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.data.CandleData;
import org.investpro.data.Db1;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.CurrencyRepositoryImpl;
import org.investpro.persistence.repository.OrderRepositoryImpl;
import org.investpro.persistence.repository.TradeRepositoryImpl;
import org.investpro.service.CurrencyService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;

import org.investpro.ui.tools.ChartToolbar;
import org.investpro.ui.utils.CurrencyIconLoader;
import org.investpro.utils.CandleDataSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ChartContainer extends Region {
    private static final int DEFAULT_SECONDS_PER_CANDLE = 3_600;
    private static final Duration CHART_FADE_DURATION = Duration.millis(220);

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final String telegramToken;
    private final TradingService tradingService;
    private final SimpleIntegerProperty secondsPerCandle = new SimpleIntegerProperty(DEFAULT_SECONDS_PER_CANDLE);

    private final VBox candleChartContainer = new VBox();
    private final ChartToolbar toolbar;

    private CandleStickChart candleStickChart;
    private Consumer<String> onChartError;
    private Consumer<CandleData> candleSelectionCallback;

    public ChartContainer(Exchange exchange, TradePair tradePair, String telegramToken) {
        this(exchange, tradePair, false, telegramToken, null);
    }

    public ChartContainer(Exchange exchange, TradePair tradePair, String telegramToken, boolean liveSyncing) {
        this(exchange, tradePair, liveSyncing, telegramToken, null);
    }

    public ChartContainer(Exchange exchange,
                          TradePair tradePair,
                          boolean liveSyncing,
                          String telegramToken,
                          TradingService tradingService) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.liveSyncing = liveSyncing;
        this.telegramToken = telegramToken == null ? "" : telegramToken;
        this.tradingService = tradingService == null ? createFallbackTradingService(exchange, this.telegramToken) : tradingService;

        getStyleClass().add("candle-chart-container");
        setMinSize(360, 320);
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setStyle("""
                -fx-background-color: #060a12;
                -fx-border-color: rgba(51, 65, 85, 0.85);
                -fx-border-width: 1;
                """);

        CandleDataSupplier initialSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        Set<Integer> granularities = (initialSupplier != null)
                ? initialSupplier.getSupportedGranularities()
                : Set.of(60, 300, 900, 3600, 14400, 86400);  // sensible defaults when exchange has no candle data
        toolbar = new ChartToolbar(widthProperty(), heightProperty(), granularities);

        VBox toolbarContainer = getVBox();
        AnchorPane.setTopAnchor(toolbarContainer, 0.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 0.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        candleChartContainer.setFillWidth(true);
        candleChartContainer.setPadding(new Insets(8, 12, 10, 12));
        candleChartContainer.setStyle("-fx-background-color: #060a12;");
        AnchorPane.setTopAnchor(candleChartContainer, 52.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 0.0);
        AnchorPane.setRightAnchor(candleChartContainer, 0.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);

        AnchorPane root = new AnchorPane(toolbarContainer, candleChartContainer);
        root.prefHeightProperty().bind(heightProperty());
        root.prefWidthProperty().bind(widthProperty());
        getChildren().setAll(root);

        rebuildChart(secondsPerCandle.get(), false);
        secondsPerCandle.addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                rebuildChart(newValue.intValue(), true);
            }
        });
    }

    private VBox getVBox() {
        HBox actionBar = new HBox(6);
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.getChildren().setAll(
                chartActionButton("Refresh", "/img/refresh-solid.png", () -> withChart(CandleStickChart::refreshChart)));

        HBox header = new HBox(10, toolbar, actionBar);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(toolbar, Priority.ALWAYS);

        VBox toolbarContainer = new VBox(header);
        toolbarContainer.setPadding(new Insets(8, 14, 4, 82));
        toolbarContainer.setMinHeight(48);
        toolbarContainer.setPrefHeight(52);
        toolbarContainer.setMaxHeight(60);
        toolbarContainer.setStyle("""
                -fx-background-color: linear-gradient(to bottom, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.72));
                -fx-border-color: rgba(51, 65, 85, 0.72);
                -fx-border-width: 0 0 1 0;
                """);
        return toolbarContainer;
    }

    private Button chartActionButton(String tooltip, String iconPath, Runnable action) {
        Button button = new Button();
        button.setTooltip(new Tooltip(tooltip));
        ImageView icon = loadIcon(iconPath);
        if (icon == null) {
            button.setText(tooltip);
        } else {
            button.setGraphic(icon);
        }
        button.setMinSize(30, 28);
        button.setPrefSize(30, 28);
        button.getStyleClass().add("chart-action-button");
        button.setStyle("""
                -fx-background-color: transparent;
                -fx-padding: 0;
                """);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private ImageView loadIcon(String path) {
        try {
            var stream = ChartContainer.class.getResourceAsStream(path);
            if (stream == null) {
                return null;
            }
            ImageView imageView = new ImageView(new Image(stream));
            imageView.setFitWidth(15);
            imageView.setFitHeight(15);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception exception) {
            log.debug("Unable to load chart action icon {}: {}", path, exception.getMessage());
            return null;
        }
    }

    private void withChart(Consumer<CandleStickChart> action) {
        if (candleStickChart != null && action != null) {
            action.accept(candleStickChart);
        }
    }

    public CandleStickChart getChart() {
        return candleStickChart;
    }

    public void setSecondsPerCandle(int seconds) {
        if (seconds <= 0) {
            reportError("Invalid candle duration: " + seconds);
            return;
        }
        secondsPerCandle.set(seconds);
    }

    public void setSecondsPerCandle(Integer seconds) {
        if (seconds != null) {
            setSecondsPerCandle(seconds.intValue());
        }
    }

    public SimpleIntegerProperty secondsPerCandleProperty() {
        return secondsPerCandle;
    }

    public void setCandleSelectionCallback(Consumer<CandleData> callback) {
        this.candleSelectionCallback = callback;
        if (candleStickChart != null) {
            candleStickChart.setCandleSelectionCallback(callback);
        }
    }

    public void setOnChartError(Consumer<String> onChartError) {
        this.onChartError = onChartError;
    }

    public void dispose() {
        if (candleStickChart != null) {
            candleStickChart.dispose();
            candleStickChart = null;
        }
        candleChartContainer.getChildren().clear();
    }

    private void rebuildChart(int durationSeconds, boolean animate) {
        try {
            CandleStickChart nextChart = createChart(durationSeconds);
            applyChartBindings(nextChart);
            toolbar.registerEventHandlers(nextChart);
            toolbar.setChartOptions(nextChart.getChartOptions());
            toolbar.setActiveToolbarButton(secondsPerCandle);
            showChart(nextChart, animate);
        } catch (Exception exception) {
            log.warn("Unable to create chart for {} at {} seconds", tradePair, durationSeconds, exception);
            reportError("Unable to create chart for %s: %s".formatted(tradePair, rootMessage(exception)));
        }
    }

    private CandleStickChart createChart(int durationSeconds) {
        CandleDataSupplier supplier = exchange.getCandleDataSupplier(durationSeconds, tradePair);
        if (supplier == null) {
            throw new IllegalStateException(
                    "Exchange '%s' does not support candle data for %s"
                            .formatted(exchange.getName(), tradePair));
        }
        CandleStickChart chart = new CandleStickChart(
                exchange,
                supplier,
                tradePair,
                liveSyncing,
                durationSeconds,
                telegramToken,
                tradingService,
                widthProperty(),
                heightProperty());
        applyDefaultBackgroundImage(chart);
        return chart;
    }

    private void applyDefaultBackgroundImage(CandleStickChart chart) {
        if (chart == null) {
            return;
        }

        String baseCode = tradePair.getBaseCode();
        if (baseCode == null || baseCode.isBlank()) {
            return;
        }

        Image image = CurrencyIconLoader.loadCurrencyIcon(baseCode);
        if (image != null && !image.isError()) {
            chart.setBackgroundImage(image);
        }
    }

    private void applyChartBindings(CandleStickChart chart) {
        VBox.setVgrow(chart, Priority.ALWAYS);
        chart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        chart.setCandleSelectionCallback(candleSelectionCallback);
    }

    private void showChart(CandleStickChart nextChart, boolean animate) {
        CandleStickChart previousChart = candleStickChart;
        candleStickChart = nextChart;

        if (!animate || previousChart == null) {
            if (previousChart != null) {
                previousChart.dispose();
            }
            candleChartContainer.getChildren().setAll(nextChart);
            fadeIn(nextChart);
            return;
        }

        FadeTransition fadeOut = new FadeTransition(CHART_FADE_DURATION, previousChart);
        fadeOut.setFromValue(previousChart.getOpacity());
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            previousChart.dispose();
            candleChartContainer.getChildren().setAll(nextChart);
            fadeIn(nextChart);
        });
        fadeOut.play();
    }

    private void fadeIn(CandleStickChart chart) {
        chart.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(CHART_FADE_DURATION, chart);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    private TradingService createFallbackTradingService(Exchange exchange, String telegramToken) {
        try {
            Properties config = loadConfig();
            if (telegramToken != null && !telegramToken.isBlank()) {
                config.setProperty("telegram_token", telegramToken);
            }
            String openAiApiKey = config.getProperty("open_ai_api_key", config.getProperty("openai.api_key", ""));
            SystemCore systemCore = new SystemCore(exchange, config, openAiApiKey);
            Db1 database = new Db1(config);
            return new TradingService(
                    systemCore,
                    new TradeService(new TradeRepositoryImpl()),
                    new OrderService(new OrderRepositoryImpl(database)),
                    new CurrencyService(new CurrencyRepositoryImpl(database)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize chart trading services", exception);
        }
    }

    private Properties loadConfig() {
        Properties config = new Properties();
        try (InputStream input = ChartContainer.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
            }
        } catch (IOException exception) {
            log.debug("Unable to load chart config.properties: {}", exception.getMessage());
        }
        return config;
    }

    private void reportError(String message) {
        if (onChartError != null) {
            onChartError.accept(message);
        } else {
            log.warn(message);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 360;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 320;
    }
}
