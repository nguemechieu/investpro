package org.investpro.ui.charts;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.data.CandleData;
import org.investpro.data.Db1;
import org.investpro.enums.timeframe.Timeframe;
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
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

@Slf4j
@Getter
public class ChartContainer extends Region {

    private static final int DEFAULT_SECONDS_PER_CANDLE = 3_600;

    private static final double MIN_WIDTH = 360;
    private static final double MIN_HEIGHT = 320;

    private static final Duration CHART_FADE_DURATION = Duration.millis(220);

    private static final Set<Integer> DEFAULT_GRANULARITIES = Set.of(
            60,
            300,
            900,
            3_600,
            14_400,
            21_600,
            86_400,
            604_800
    );

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final String telegramToken;
    private TradingService tradingService;
    private final java.util.concurrent.CompletableFuture<TradingService> tradingServiceReady;
    private java.util.concurrent.CompletableFuture<PreparedChart> chartPreparation;
    private record PreparedChart(CandleDataSupplier supplier, Set<Integer> granularities) {}
    private volatile long chartRequest;

    private final SimpleIntegerProperty secondsPerCandle =
            new SimpleIntegerProperty(DEFAULT_SECONDS_PER_CANDLE);

    private final ComboBox<Integer> timeframeSelector = new ComboBox<>();

    private final VBox candleChartContainer = new VBox();
    private final ChartToolbar toolbar;

    private CandleStickChart candleStickChart;
    private FadeTransition chartFade;
    private volatile boolean disposed;

    @Setter
    private Consumer<CandleStickChart> onChartReady;

    @Setter
    private Consumer<String> onChartError;

    private Consumer<CandleData> candleSelectionCallback;

    public ChartContainer(
            Exchange exchange,
            TradePair tradePair,
            String telegramToken
    ) {
        this(exchange, tradePair, false, telegramToken, null);
    }

    public ChartContainer(
            Exchange exchange,
            TradePair tradePair,
            String telegramToken,
            boolean liveSyncing
    ) {
        this(exchange, tradePair, liveSyncing, telegramToken, null);
    }

    public ChartContainer(
            Exchange exchange,
            TradePair tradePair,
            boolean liveSyncing,
            String telegramToken,
            TradingService tradingService
    ) {
        this.exchange = Objects.requireNonNull(
                exchange,
                "exchange must not be null"
        );

        this.tradePair = Objects.requireNonNull(
                tradePair,
                "tradePair must not be null"
        );

        this.liveSyncing = liveSyncing;
        this.telegramToken = Objects.requireNonNullElse(telegramToken, "");

        this.tradingService = tradingService;
        this.tradingServiceReady = tradingService != null
                ? java.util.concurrent.CompletableFuture.completedFuture(tradingService)
                : org.investpro.ui.utils.UiBackgroundTasks.submit(() -> createFallbackTradingService(exchange, this.telegramToken)).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);

        getStyleClass().add("candle-chart-container");

        setMinSize(MIN_WIDTH, MIN_HEIGHT);
        setPrefSize(1000, 700);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        setStyle("""
                -fx-background-color: #060a12;
                -fx-border-color: rgba(51, 65, 85, 0.85);
                -fx-border-width: 1;
                """);

        Set<Integer> granularities = resolveSupportedGranularities();

        toolbar = new ChartToolbar(
                widthProperty(),
                heightProperty(),
                granularities
        );

        configureTimeframeSelector(granularities);

        VBox toolbarContainer = getVBox();

        AnchorPane.setTopAnchor(toolbarContainer, 0.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 0.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        candleChartContainer.setFillWidth(true);
        candleChartContainer.setPadding(new Insets(8, 12, 10, 12));
        candleChartContainer.setStyle(
                "-fx-background-color: #060a12;"
        );

        AnchorPane.setTopAnchor(candleChartContainer, 52.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 0.0);
        AnchorPane.setRightAnchor(candleChartContainer, 0.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);

        AnchorPane root = new AnchorPane(
                toolbarContainer,
                candleChartContainer
        );

        root.prefHeightProperty().bind(heightProperty());
        root.prefWidthProperty().bind(widthProperty());

        getChildren().setAll(root);

        rebuildChart(secondsPerCandle.get(), false);

        secondsPerCandle.addListener((_, oldValue, newValue) -> {
            if (Objects.equals(oldValue, newValue)) {
                return;
            }

            int newDuration = newValue.intValue();

            if (!Objects.equals(
                    timeframeSelector.getValue(),
                    newDuration
            )) {
                timeframeSelector.setValue(newDuration);
            }

            rebuildChart(newDuration, true);
        });
    }

    private Set<Integer> resolveSupportedGranularities() {

        try {
            Set<Integer> supported = new java.util.HashSet<>();
            for (Timeframe timeframe : exchange.getSupportedTimeframes()) supported.add(timeframe.getSeconds());

            TreeSet<Integer> sanitized = new TreeSet<>();

            supported.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> value > 0)
                    .forEach(sanitized::add);

            if (sanitized.isEmpty()) {
                return DEFAULT_GRANULARITIES;
            }

            return new LinkedHashSet<>(sanitized);

        } catch (Exception exception) {

            log.debug(
                    "Unable to determine candle granularities for {} on {}: {}",
                    tradePair,
                    exchange.getName(),
                    exception.getMessage()
            );

            return DEFAULT_GRANULARITIES;
        }
    }

    private void configureTimeframeSelector(
            Set<Integer> granularities
    ) {
        timeframeSelector.getItems().setAll(granularities);

        if (!timeframeSelector.getItems().contains(
                secondsPerCandle.get()
        )) {
            secondsPerCandle.set(
                    timeframeSelector.getItems().getFirst()
            );
        }

        timeframeSelector.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(Integer seconds) {

                        if (seconds == null) {
                            return "";
                        }

                        try {
                            return Timeframe
                                    .fromSeconds(seconds)
                                    .getDisplayName();
                        } catch (IllegalArgumentException ignored) {
                            return seconds + " seconds";
                        }
                    }

                    @Override
                    public Integer fromString(String value) {
                        throw new UnsupportedOperationException(
                                "Timeframe text input is not supported"
                        );
                    }
                }
        );

        timeframeSelector.setPrefWidth(135);

        timeframeSelector.setTooltip(
                new Tooltip(
                        "Choose the candle timeframe for this chart"
                )
        );

        timeframeSelector.setValue(secondsPerCandle.get());

        timeframeSelector.setOnAction(_ -> {

            Integer selected =
                    timeframeSelector.getValue();

            if (selected != null &&
                    selected != secondsPerCandle.get()) {

                setSecondsPerCandle(selected);
            }
        });
    }

    private VBox getVBox() {

        HBox actionBar = new HBox(6);

        actionBar.setAlignment(Pos.CENTER_RIGHT);

        actionBar.getChildren().setAll(
                timeframeSelector,

                chartActionButton(
                        "Refresh",
                        "/img/refresh-solid.png",
                        () -> withChart(
                                CandleStickChart::refreshChart
                        )
                )
        );

        HBox header = new HBox(
                10,
                toolbar,
                actionBar
        );

        header.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(
                toolbar,
                Priority.ALWAYS
        );

        VBox toolbarContainer =
                new VBox(header);

        toolbarContainer.setPadding(
                new Insets(8, 10, 4, 10)
        );

        toolbarContainer.setMinHeight(48);
        toolbarContainer.setPrefHeight(52);
        toolbarContainer.setMaxHeight(60);

        toolbarContainer.setStyle("""
                -fx-background-color:
                    linear-gradient(
                        to bottom,
                        rgba(15, 23, 42, 0.96),
                        rgba(15, 23, 42, 0.72)
                    );
                -fx-border-color: rgba(51, 65, 85, 0.72);
                -fx-border-width: 0 0 1 0;
                """);

        return toolbarContainer;
    }

    private @NonNull Button chartActionButton(
           String tooltip,
            String iconPath,
            Runnable action
    ) {
        Button button = new Button();

        button.setTooltip(
                new Tooltip(tooltip)
        );

        ImageView icon = loadIcon(iconPath);

        if (icon == null) {
            button.setText(tooltip);
        } else {
            button.setGraphic(icon);
        }

        button.setMinSize(30, 28);
        button.setPrefSize(30, 28);

        button.getStyleClass()
                .add("chart-action-button");

        button.setStyle("""
                -fx-background-color: transparent;
                -fx-padding: 0;
                """);

        button.setOnAction(_ -> {
            if (action != null) {
                action.run();
            }
        });

        return button;
    }

    private ImageView loadIcon(String path) {

        try (InputStream stream =
                     ChartContainer.class
                             .getResourceAsStream(path)) {

            if (stream == null) {
                return null;
            }

            Image image = new Image(stream);

            if (image.isError()) {
                return null;
            }

            ImageView imageView =
                    new ImageView(image);

            imageView.setFitWidth(15);
            imageView.setFitHeight(15);
            imageView.setPreserveRatio(true);

            return imageView;

        } catch (Exception exception) {

            log.debug(
                    "Unable to load chart action icon {}: {}",
                    path,
                    exception.getMessage()
            );

            return null;
        }
    }

    private void withChart(
            Consumer<CandleStickChart> action
    ) {
        CandleStickChart chart =
                candleStickChart;

        if (!disposed &&
                chart != null &&
                action != null) {

            action.accept(chart);
        }
    }

    public CandleStickChart getChart() {
        return candleStickChart;
    }

    public void setSecondsPerCandle(int seconds) {

        if (!Platform.isFxApplicationThread()) {

            Platform.runLater(
                    () -> setSecondsPerCandle(seconds)
            );

            return;
        }

        if (disposed) {
            return;
        }

        if (seconds <= 0) {

            reportError(
                    "Invalid candle duration: " + seconds
            );

            return;
        }

        if (!timeframeSelector
                .getItems()
                .isEmpty()
                &&
                !timeframeSelector
                        .getItems()
                        .contains(seconds)) {

            reportError(
                    "This exchange does not support a "
                            + seconds
                            + " second candle timeframe."
            );

            return;
        }

        if (secondsPerCandle.get() == seconds) {
            return;
        }

        secondsPerCandle.set(seconds);
    }

    public void setSecondsPerCandle(
            Integer seconds
    ) {
        if (seconds != null) {
            setSecondsPerCandle(
                    seconds.intValue()
            );
        }
    }

    public void setCandleSelectionCallback(
            Consumer<CandleData> callback
    ) {
        this.candleSelectionCallback =
                callback;

        CandleStickChart chart =
                candleStickChart;

        if (chart != null) {
            chart.setCandleSelectionCallback(
                    callback
            );
        }
    }

    public void dispose() {

        if (!Platform.isFxApplicationThread()) {

            Platform.runLater(this::dispose);

            return;
        }

        if (disposed) {
            return;
        }

        disposed = true;
        chartRequest++;
        if (chartPreparation != null) chartPreparation.cancel(true);

        stopChartFade();

        CandleStickChart chart =
                candleStickChart;

        candleStickChart = null;

        candleChartContainer
                .getChildren()
                .clear();

        if (chart != null) {
            chart.dispose();
        }
    }

    private void rebuildChart(
            int durationSeconds,
            boolean animate
    ) {
        if (disposed) {
            return;
        }

        if (!exchange
                .getCapability()
                .isSupportsHistoricalCandles()) {

            reportError(
                    "Historical candle data is not available in the "
                            + exchange.getName()
                            + " adapter."
            );

            return;
        }

        long request = ++chartRequest;
        if (chartPreparation != null) chartPreparation.cancel(true);
        if (candleStickChart == null) candleChartContainer.getChildren().setAll(new javafx.scene.control.Label("Loading chart…"));
        var supplierTask = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<PreparedChart>>();
        chartPreparation = tradingServiceReady.thenCompose(service -> {
            var task = org.investpro.ui.utils.UiBackgroundTasks.marketData(() -> {
                if (disposed || request != chartRequest) throw new java.util.concurrent.CancellationException();
                CandleDataSupplier supplier = exchange.getCandleDataSupplier(durationSeconds, tradePair);
                if (supplier == null) throw new IllegalStateException("Candle data is unavailable for " + tradePair);
                TreeSet<Integer> granularities = new TreeSet<>();
                supplier.getSupportedGranularities().stream().filter(Objects::nonNull).filter(value -> value > 0).forEach(granularities::add);
                return new PreparedChart(supplier, granularities.isEmpty() ? DEFAULT_GRANULARITIES : granularities);
            }).orTimeout(20, java.util.concurrent.TimeUnit.SECONDS);
            supplierTask.set(task);
            if (disposed || request != chartRequest) task.cancel(true);
            return task;
        });
        chartPreparation.whenComplete((supplier, error) -> {
            if (error instanceof java.util.concurrent.CancellationException && supplierTask.get() != null) supplierTask.get().cancel(true);
            Platform.runLater(() -> {
                if (disposed || request != chartRequest) return;
                if (error != null) {
                    reportError("Unable to create chart for " + tradePair + ": " + rootMessage(error));
                    return;
                }
                try {
                    timeframeSelector.getItems().setAll(supplier.granularities());
                    if (!supplier.granularities().contains(durationSeconds)) {
                        secondsPerCandle.set(supplier.granularities().iterator().next());
                        return;
                    }
                    timeframeSelector.setValue(durationSeconds);
                    tradingService = tradingServiceReady.getNow(null);
                    CandleStickChart nextChart = new CandleStickChart(exchange, supplier.supplier(), tradePair, liveSyncing,
                            durationSeconds, telegramToken, tradingService, widthProperty(), heightProperty());
                    applyChartBindings(nextChart);
                    toolbar.registerEventHandlers(nextChart);
                    toolbar.setChartOptions(nextChart.getChartOptions());
                    toolbar.setActiveToolbarButton(secondsPerCandle);
                    showChart(nextChart, animate);
                    applyDefaultBackgroundImage(nextChart);
                    if (onChartReady != null) onChartReady.accept(nextChart);
                } catch (Exception exception) {
                    reportError("Unable to create chart for " + tradePair + ": " + rootMessage(exception));
                }
            });
        });
    }

    private void applyDefaultBackgroundImage(
            CandleStickChart chart
    ) {
        if (chart == null) {
            return;
        }

        String baseCode =
                tradePair.getBaseCode();

        if (baseCode == null ||
                baseCode.isBlank()) {

            return;
        }

        CurrencyIconLoader.loadCurrencyIconAsync(baseCode).thenAccept(image -> Platform.runLater(() -> {
            if (!disposed && candleStickChart == chart && image != null && !image.isError()) chart.setBackgroundImage(image);
        }));
    }

    private void applyChartBindings(
            CandleStickChart chart
    ) {
        VBox.setVgrow(
                chart,
                Priority.ALWAYS
        );

        chart.setMaxSize(
                Double.MAX_VALUE,
                Double.MAX_VALUE
        );

        chart.setCandleSelectionCallback(
                candleSelectionCallback
        );
    }

    private void showChart(
            CandleStickChart nextChart,
            boolean animate
    ) {
        stopChartFade();

        CandleStickChart previousChart =
                candleStickChart;

        /*
         * Install the new chart before disposing the previous
         * instance. Some chart cleanup operations mutate the
         * chart's internal scene state.
         */
        candleChartContainer
                .getChildren()
                .setAll(nextChart);

        candleStickChart = nextChart;

        if (previousChart != null &&
                previousChart != nextChart) {

            previousChart.dispose();
        }

        if (animate) {
            fadeIn(nextChart);
        } else {
            nextChart.setOpacity(1.0);
        }
    }

    private void stopChartFade() {

        FadeTransition fade =
                chartFade;

        chartFade = null;

        if (fade != null) {
            fade.stop();
            fade.setOnFinished(null);
        }
    }

    private void fadeIn(
            CandleStickChart chart
    ) {
        chart.setOpacity(0.0);

        FadeTransition fade =
                new FadeTransition(
                        CHART_FADE_DURATION,
                        chart
                );

        chartFade = fade;

        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        fade.setOnFinished(_ -> {

            if (chartFade == fade) {
                chartFade = null;
            }
        });

        fade.play();
    }

    private TradingService createFallbackTradingService(
            Exchange exchange,
            String telegramToken
    ) {
        try {

            Properties config =
                    loadConfig();

            if (telegramToken != null &&
                    !telegramToken.isBlank()) {

                config.setProperty(
                        "telegram_token",
                        telegramToken
                );
            }

            String openAiApiKey =
                    config.getProperty(
                            "open_ai_api_key",
                            config.getProperty(
                                    "openai.api_key",
                                    ""
                            )
                    );

            SystemCore systemCore =
                    new SystemCore(
                            exchange,
                            config,
                            openAiApiKey
                    );

            Db1 database =
                    new Db1(config);

            return new TradingService(
                    systemCore,
                    new TradeService(
                            new TradeRepositoryImpl()
                    ),
                    new OrderService(
                            new OrderRepositoryImpl(database)
                    ),
                    new CurrencyService(
                            new CurrencyRepositoryImpl(database)
                    )
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to initialize chart trading services",
                    exception
            );
        }
    }

    private Properties loadConfig() {

        Properties config =
                new Properties();

        try (InputStream input =
                     ChartContainer.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     "config.properties"
                             )) {

            if (input != null) {
                config.load(input);
            }

        } catch (IOException exception) {

            log.debug(
                    "Unable to load chart config.properties: {}",
                    exception.getMessage()
            );
        }

        return config;
    }

    private void reportError(
            String message
    ) {
        if (candleStickChart == null) {
            javafx.scene.control.Label error = new javafx.scene.control.Label(message);
            error.setWrapText(true);
            candleChartContainer.getChildren().setAll(error);
        }
        if (onChartError != null) {
            onChartError.accept(message);
        } else {
            log.warn("{}", message);
        }
    }

    private String rootMessage(
            Throwable throwable
    ) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current =
                throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message =
                current.getMessage();

        return message == null ||
                message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    @Override
    protected double computeMinWidth(
            double height
    ) {
        return MIN_WIDTH;
    }

    @Override
    protected double computeMinHeight(
            double width
    ) {
        return MIN_HEIGHT;
    }
}
