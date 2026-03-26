package org.investpro.investpro.ui.chart;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.FXUtils;
import org.investpro.investpro.LogOnExceptionThreadFactory;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.OrderBookEntry;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.CandleStickChartToolbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChartWorkspacePane extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ChartWorkspacePane.class);
    private static final DateTimeFormatter UPDATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Color DEPTH_BACKGROUND = Color.web("#07131f");
    private static final Color DEPTH_PANEL = Color.web("#0f2236");
    private static final Color DEPTH_GRID = Color.web("#94a3b8", 0.18);
    private static final Color BID_COLOR = Color.web("#22c55e");
    private static final Color ASK_COLOR = Color.web("#ef4444");

    private final Exchange exchange;
    private final TradePair tradePair;
    private final Tab candlestickTab;
    private final StackPane candlestickViewport = new StackPane();
    private final ScrollPane candlestickScrollPane = new ScrollPane();
    private Node currentCandlestickContent;
    private final Canvas depthCanvas = new Canvas(960, 420);
    private final Label depthStatusLabel = new Label("Loading market depth...");
    private final TableView<OrderBookRow> bidTable = createOrderBookTable("Bid");
    private final TableView<OrderBookRow> askTable = createOrderBookTable("Ask");
    private final Label pairValue = createMetricValue();
    private final Label bidValue = createMetricValue();
    private final Label askValue = createMetricValue();
    private final Label midpointValue = createMetricValue();
    private final Label spreadValue = createMetricValue();
    private final Label levelsValue = createMetricValue();
    private final Label updatedValue = createMetricValue();
    private final Label serviceValue = createMetricValue();
    private final Label statusValue = createMetricValue();
    private final ScheduledExecutorService refreshExecutor;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private volatile String lastMarketAvailabilityMessage = "";

    private volatile OrderBook latestOrderBook;
    private volatile Double[] latestBidAsk = new Double[]{Double.NaN, Double.NaN};

    public ChartWorkspacePane(Exchange exchange,
                              TradePair tradePair,
                              CandleStickChartToolbar candlestickToolbar,
                              Node candlestickContent) {
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("CHART-WORKSPACE-" + tradePair.toString('-'))
        );

        getStyleClass().add("chart-workspace");
        setFillWidth(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        candlestickTab = createTab("Candlestick", createCandlestickPane(candlestickToolbar, candlestickContent));
        Tab depthTab = createTab("Depth", createDepthPane());
        Tab marketInfoTab = createTab("Market Info", createMarketInfoPane());
        Tab orderBookTab = createTab("Order Book", createOrderBookPane());

        TabPane workspaceTabs = new TabPane(candlestickTab, depthTab, marketInfoTab, orderBookTab);
        workspaceTabs.getStyleClass().add("chart-detail-tabs");
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(workspaceTabs, Priority.ALWAYS);

        getChildren().setAll(workspaceTabs);
        refreshExecutor.scheduleWithFixedDelay(this::refreshMarketSnapshot, 0, 5, TimeUnit.SECONDS);
    }

    public void setCandlestickContent(Node candlestickContent) {
        updateCandlestickViewport(candlestickContent);
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        refreshExecutor.shutdownNow();
    }

    private Tab createTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private Node createCandlestickPane(CandleStickChartToolbar candlestickToolbar, Node candlestickContent) {
        candlestickToolbar.setManaged(false);
        candlestickToolbar.setVisible(false);

        candlestickViewport.getStyleClass().add("chart-scroll-content");
        candlestickViewport.setAlignment(Pos.TOP_LEFT);
        candlestickViewport.setMinSize(0, 0);
        candlestickViewport.setPrefSize(960, 640);
        candlestickViewport.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        candlestickScrollPane.setContent(candlestickViewport);
        candlestickScrollPane.getStyleClass().add("chart-scroll-pane");
        candlestickScrollPane.setFitToWidth(true);
        candlestickScrollPane.setFitToHeight(true);
        candlestickScrollPane.setPannable(true);
        candlestickScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        candlestickScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        candlestickScrollPane.setFocusTraversable(false);
        candlestickScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            double preferredWidth = Math.max(860, newBounds.getWidth());
            double preferredHeight = Math.max(540, newBounds.getHeight());
            candlestickViewport.setPrefSize(preferredWidth, preferredHeight);
            if (currentCandlestickContent instanceof Region region) {
                region.setMinSize(0, 0);
                region.setPrefSize(preferredWidth, preferredHeight);
                region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
        });

        updateCandlestickViewport(candlestickContent);

        StackPane chartCanvasShell = new StackPane(candlestickScrollPane);
        chartCanvasShell.getStyleClass().add("chart-canvas-shell");
        chartCanvasShell.setPadding(new Insets(12));
        chartCanvasShell.setMinHeight(0);
        VBox.setVgrow(chartCanvasShell, Priority.ALWAYS);

        VBox candlestickPane = new VBox(chartCanvasShell);
        candlestickPane.setFillWidth(true);
        candlestickPane.setMinHeight(0);
        VBox.setVgrow(chartCanvasShell, Priority.ALWAYS);
        return candlestickPane;
    }

    private void updateCandlestickViewport(Node candlestickContent) {
        currentCandlestickContent = candlestickContent;
        if (candlestickContent instanceof Region region) {
            region.setMinSize(0, 0);
            region.setPrefSize(
                    Math.max(860, candlestickViewport.getPrefWidth()),
                    Math.max(540, candlestickViewport.getPrefHeight())
            );
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        candlestickViewport.getChildren().setAll(candlestickContent);
        StackPane.setAlignment(candlestickContent, Pos.TOP_LEFT);
    }

    private Node createDepthPane() {
        depthStatusLabel.getStyleClass().add("chart-surface-empty");
        depthStatusLabel.setMouseTransparent(true);

        StackPane depthPane = new StackPane(depthCanvas, depthStatusLabel);
        depthPane.getStyleClass().add("chart-surface");
        depthPane.setPadding(new Insets(14));
        depthPane.setMinHeight(0);
        VBox.setVgrow(depthPane, Priority.ALWAYS);

        depthPane.widthProperty().addListener((_, _, newWidth) -> {
            depthCanvas.setWidth(Math.max(newWidth.doubleValue() - 28, 320));
            drawDepthChart(latestOrderBook);
        });
        depthPane.heightProperty().addListener((_, _, newHeight) -> {
            depthCanvas.setHeight(Math.max(newHeight.doubleValue() - 28, 260));
            drawDepthChart(latestOrderBook);
        });

        return depthPane;
    }

    private Node createMarketInfoPane() {
        FlowPane metricsPane = new FlowPane(14, 14);
        metricsPane.setPadding(new Insets(4));
        metricsPane.getStyleClass().add("market-info-grid");
        metricsPane.getChildren().addAll(
                createMetricCard("Pair", pairValue),
                createMetricCard("Bid", bidValue),
                createMetricCard("Ask", askValue),
                createMetricCard("Mid", midpointValue),
                createMetricCard("Spread", spreadValue),
                createMetricCard("Levels", levelsValue),
                createMetricCard("Updated", updatedValue),
                createMetricCard("Service", serviceValue),
                createMetricCard("Status", statusValue)
        );

        pairValue.setText(tradePair.toString('/'));
        serviceValue.setText(exchange.getExchangeMessage());
        statusValue.setText("Loading...");
        updatedValue.setText("Waiting for data...");

        VBox marketInfoPane = new VBox(metricsPane);
        marketInfoPane.getStyleClass().add("chart-surface");
        marketInfoPane.setPadding(new Insets(14));
        marketInfoPane.setFillWidth(true);
        VBox.setVgrow(metricsPane, Priority.ALWAYS);
        return marketInfoPane;
    }

    private Node createOrderBookPane() {
        SplitPane splitPane = new SplitPane(
                wrapTable("Bids", bidTable),
                wrapTable("Asks", askTable)
        );
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getStyleClass().add("chart-surface");
        splitPane.setDividerPositions(0.5);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return splitPane;
    }

    private VBox wrapTable(String title, TableView<OrderBookRow> table) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("chart-section-title");

        VBox wrapper = new VBox(10, titleLabel, table);
        wrapper.setPadding(new Insets(14));
        wrapper.getStyleClass().add("chart-orderbook-panel");
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapper;
    }

    private VBox createMetricCard(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("chart-metric-title");

        VBox card = new VBox(6, titleLabel, valueLabel);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("chart-metric-card");
        card.setMinWidth(180);
        card.setPrefWidth(200);
        return card;
    }

    private Label createMetricValue() {
        Label label = new Label("--");
        label.getStyleClass().add("chart-metric-value");
        label.setWrapText(true);
        return label;
    }

    private TableView<OrderBookRow> createOrderBookTable(String side) {
        TableView<OrderBookRow> table = new TableView<>();
        table.getStyleClass().add("chart-orderbook-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Waiting for " + side.toLowerCase() + " data..."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<OrderBookRow, String> priceColumn = new TableColumn<>("Price");
        priceColumn.setCellValueFactory(cell -> cell.getValue().priceProperty());

        TableColumn<OrderBookRow, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(cell -> cell.getValue().sizeProperty());

        TableColumn<OrderBookRow, String> totalColumn = new TableColumn<>("Total");
        totalColumn.setCellValueFactory(cell -> cell.getValue().totalProperty());

        table.getColumns().addAll(priceColumn, sizeColumn, totalColumn);
        return table;
    }

    private void refreshMarketSnapshot() {
        if (disposed.get() || !FxLifecycle.isShowing(this)) {
            return;
        }
        try {
            Double[] latestPriceSnapshot = exchange.getLatestPrice(tradePair);
            OrderBook latestOrderBookSnapshot = fetchLatestOrderBook();
            FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this),
                    () -> applyMarketSnapshot(latestPriceSnapshot, latestOrderBookSnapshot));
        } catch (Exception e) {
            logAvailabilityOnce("Unable to refresh market workspace for " + tradePair + ": " + e.getMessage(), e);
            FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                statusValue.setText("Unavailable");
                updatedValue.setText("Refresh failed");
                depthStatusLabel.setText("Unable to refresh market data.");
                depthStatusLabel.setVisible(true);
            });
        }
    }

    private OrderBook fetchLatestOrderBook()
            throws InterruptedException, ExecutionException, TimeoutException {
        List<OrderBook> books;
        try {
            books = exchange.fetchOrderBook(tradePair).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException executionException) {
            logAvailabilityOnce("Order book is unavailable for " + tradePair + ".", executionException);
            return null;
        }
        if (books == null || books.isEmpty()) {
            return null;
        }
        return books.getFirst();
    }

    private void applyMarketSnapshot(Double[] latestPriceSnapshot, OrderBook latestOrderBookSnapshot) {
        if (disposed.get()) {
            return;
        }
        latestOrderBook = latestOrderBookSnapshot;
        latestBidAsk = latestPriceSnapshot == null ? new Double[]{Double.NaN, Double.NaN} : latestPriceSnapshot;

        double bid = resolveBid(latestPriceSnapshot, latestOrderBookSnapshot);
        double ask = resolveAsk(latestPriceSnapshot, latestOrderBookSnapshot);
        double midpoint = bid > 0 && ask > 0 ? (bid + ask) / 2.0 : 0.0;
        double spread = bid > 0 && ask > 0 ? ask - bid : 0.0;
        boolean hasPrice = Double.isFinite(bid) && bid > 0 && Double.isFinite(ask) && ask > 0;
        boolean hasDepth = latestOrderBookSnapshot != null
                && !latestOrderBookSnapshot.getBidEntries().isEmpty()
                && !latestOrderBookSnapshot.getAskEntries().isEmpty();

        pairValue.setText(tradePair.toString('/'));
        bidValue.setText(formatPrice(bid));
        askValue.setText(formatPrice(ask));
        midpointValue.setText(formatPrice(midpoint));
        spreadValue.setText(spread > 0 ? formatPrice(spread) : "--");
        levelsValue.setText(latestOrderBookSnapshot == null
                ? "No depth data"
                : latestOrderBookSnapshot.getBidEntries().size() + " bids / "
                + latestOrderBookSnapshot.getAskEntries().size() + " asks");
        updatedValue.setText(UPDATED_AT_FORMATTER.format(Instant.now()));
        serviceValue.setText(exchange.getExchangeMessage());
        statusValue.setText(hasPrice && hasDepth ? "Live" : hasPrice || hasDepth ? "Partial" : "Unavailable");

        updateOrderBookTable(bidTable, latestOrderBookSnapshot == null ? List.of() : latestOrderBookSnapshot.getSortedBids(), true);
        updateOrderBookTable(askTable, latestOrderBookSnapshot == null ? List.of() : latestOrderBookSnapshot.getSortedAsks(), false);
        drawDepthChart(latestOrderBookSnapshot);
        if (hasPrice || hasDepth) {
            lastMarketAvailabilityMessage = "";
        }
    }

    private void updateOrderBookTable(TableView<OrderBookRow> table,
                                      List<OrderBookEntry> entries,
                                      boolean descending) {
        if (entries == null || entries.isEmpty()) {
            table.setItems(FXCollections.observableArrayList());
            return;
        }

        List<OrderBookEntry> orderedEntries = new ArrayList<>(entries);
        orderedEntries.sort(descending
                ? Comparator.comparingDouble(OrderBookEntry::getPrice).reversed()
                : Comparator.comparingDouble(OrderBookEntry::getPrice));

        double cumulative = 0.0;
        List<OrderBookRow> rows = new ArrayList<>();
        int count = 0;
        for (OrderBookEntry entry : orderedEntries) {
            cumulative += entry.getSize();
            rows.add(new OrderBookRow(entry.getPrice(), entry.getSize(), cumulative));
            count++;
            if (count == 18) {
                break;
            }
        }

        table.setItems(FXCollections.observableArrayList(rows));
    }

    private void drawDepthChart(OrderBook orderBook) {
        if (disposed.get()) {
            return;
        }
        GraphicsContext graphics = depthCanvas.getGraphicsContext2D();
        double width = depthCanvas.getWidth();
        double height = depthCanvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        graphics.clearRect(0, 0, width, height);
        graphics.setFill(DEPTH_BACKGROUND);
        graphics.fillRoundRect(0, 0, width, height, 20, 20);
        graphics.setFill(DEPTH_PANEL);
        graphics.fillRoundRect(12, 12, width - 24, height - 24, 16, 16);

        graphics.setStroke(DEPTH_GRID);
        graphics.setLineWidth(1);
        for (int i = 1; i <= 4; i++) {
            double y = 20 + (i * (height - 40) / 5.0);
            graphics.strokeLine(20, y, width - 20, y);
        }

        if (orderBook == null || orderBook.getBidEntries().isEmpty() || orderBook.getAskEntries().isEmpty()) {
            depthStatusLabel.setText("Order book data is not available for this market yet.");
            depthStatusLabel.setVisible(true);
            return;
        }

        depthStatusLabel.setVisible(false);

        List<OrderBookEntry> bids = new ArrayList<>(orderBook.getSortedBids());
        List<OrderBookEntry> asks = new ArrayList<>(orderBook.getSortedAsks());
        if (bids.size() > 20) {
            bids = bids.subList(0, 20);
        }
        if (asks.size() > 20) {
            asks = asks.subList(0, 20);
        }

        List<OrderBookEntry> bidsAscending = new ArrayList<>(bids);
        bidsAscending.sort(Comparator.comparingDouble(OrderBookEntry::getPrice));

        double minPrice = Math.min(bidsAscending.getFirst().getPrice(), asks.getFirst().getPrice());
        double maxPrice = Math.max(bidsAscending.getLast().getPrice(), asks.getLast().getPrice());
        if (Double.compare(minPrice, maxPrice) == 0) {
            maxPrice = minPrice + 1;
        }

        double maxCumulative = Math.max(maxCumulativeSize(bidsAscending), maxCumulativeSize(asks));
        if (maxCumulative <= 0) {
            maxCumulative = 1;
        }

        drawDepthSide(graphics, bidsAscending, minPrice, maxPrice, maxCumulative, width, height, BID_COLOR);
        drawDepthSide(graphics, asks, minPrice, maxPrice, maxCumulative, width, height, ASK_COLOR);

        graphics.setFont(Font.font(FXUtils.getMonospacedFont(), FontWeight.SEMI_BOLD, 12));
        graphics.setFill(Color.web("#e2e8f0"));
        graphics.fillText("Market depth for " + tradePair.toString('/'), 26, 30);
    }

    private void drawDepthSide(GraphicsContext graphics,
                               List<OrderBookEntry> entries,
                               double minPrice,
                               double maxPrice,
                               double maxCumulative,
                               double width,
                               double height,
                               Color color) {
        if (entries.isEmpty()) {
            return;
        }

        double left = 28;
        double top = 44;
        double usableWidth = width - 56;
        double usableHeight = height - 72;

        double cumulative = 0.0;
        double[] xPoints = new double[entries.size() + 2];
        double[] yPoints = new double[entries.size() + 2];
        xPoints[0] = left;
        yPoints[0] = top + usableHeight;

        int index = 1;
        for (OrderBookEntry entry : entries) {
            cumulative += entry.getSize();
            double x = left + (((entry.getPrice() - minPrice) / (maxPrice - minPrice)) * usableWidth);
            double y = top + usableHeight - ((cumulative / maxCumulative) * usableHeight);
            xPoints[index] = x;
            yPoints[index] = y;
            index++;
        }

        xPoints[index] = xPoints[index - 1];
        yPoints[index] = top + usableHeight;

        graphics.setFill(color.deriveColor(0, 1, 1, 0.16));
        graphics.fillPolygon(xPoints, yPoints, index + 1);

        graphics.setStroke(color);
        graphics.setLineWidth(2);
        graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.strokePolyline(xPoints, yPoints, index);
    }

    private double maxCumulativeSize(List<OrderBookEntry> entries) {
        double cumulative = 0.0;
        for (OrderBookEntry entry : entries) {
            cumulative += entry.getSize();
        }
        return cumulative;
    }

    private double resolveBid(Double[] latestPriceSnapshot, OrderBook orderBook) {
        if (latestPriceSnapshot != null
                && latestPriceSnapshot.length > 0
                && Double.isFinite(latestPriceSnapshot[0])
                && latestPriceSnapshot[0] > 0) {
            return latestPriceSnapshot[0];
        }
        if (orderBook != null && !orderBook.getBidEntries().isEmpty()) {
            return orderBook.getSortedBids().getFirst().getPrice();
        }
        return Double.NaN;
    }

    private double resolveAsk(Double[] latestPriceSnapshot, OrderBook orderBook) {
        if (latestPriceSnapshot != null
                && latestPriceSnapshot.length > 1
                && Double.isFinite(latestPriceSnapshot[1])
                && latestPriceSnapshot[1] > 0) {
            return latestPriceSnapshot[1];
        }
        if (orderBook != null && !orderBook.getAskEntries().isEmpty()) {
            return orderBook.getSortedAsks().getFirst().getPrice();
        }
        return Double.NaN;
    }

    private String formatPrice(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return "--";
        }
        if (value >= 1000) {
            return String.format("%.2f", value);
        }
        if (value >= 1) {
            return String.format("%.4f", value);
        }
        return String.format("%.6f", value);
    }

    private void logAvailabilityOnce(String message, Exception exception) {
        if (message.equals(lastMarketAvailabilityMessage)) {
            return;
        }
        lastMarketAvailabilityMessage = message;
        logger.warn(message, exception);
    }

    private static final class OrderBookRow {
        private final StringProperty price = new SimpleStringProperty();
        private final StringProperty size = new SimpleStringProperty();
        private final StringProperty total = new SimpleStringProperty();

        private OrderBookRow(double price, double size, double total) {
            this.price.set(format(price));
            this.size.set(format(size));
            this.total.set(format(total));
        }

        public StringProperty priceProperty() {
            return price;
        }

        public StringProperty sizeProperty() {
            return size;
        }

        public StringProperty totalProperty() {
            return total;
        }

        private static String format(double value) {
            if (value >= 1000) {
                return String.format("%.2f", value);
            }
            if (value >= 1) {
                return String.format("%.4f", value);
            }
            return String.format("%.6f", value);
        }
    }
}
