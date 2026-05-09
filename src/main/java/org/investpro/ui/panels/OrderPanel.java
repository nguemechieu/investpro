package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.service.NewsDataProvider;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Modern Order Panel.
 * <p>
 * Allows users to prepare BUY/SELL orders with:
 * - Symbol
 * - Order type
 * - Volume
 * - Optional limit/stop price
 * - Take profit
 * - Stop loss
 * - Expiration
 * - Comment
 * <p>
 * Important:
 * This panel currently validates and logs the order.
 * Wire onExecuteOrder(...) to your TradeExecutionCoordinator to place real
 * orders safely.
 */
@Slf4j
@Getter
@Setter
public class OrderPanel extends BorderPane {

    private ComboBox<String> symbolCombo;
    private ComboBox<OpenOrder.OrderType> orderTypeCombo;
    private ComboBox<Side> sideCombo;

    private Spinner<Double> volumeSpinner;
    private Spinner<Double> takeProfitSpinner;
    private Spinner<Double> stopLossSpinner;

    private TextArea commentArea;
    private TextField priceField;
    private DatePicker expirationDatePicker;

    private Label symbolTitleLabel;
    private Label currentPriceLabel;
    private Label bidLabel;
    private Label askLabel;
    private Label spreadLabel;
    private Label summaryLabel;

    private Button buyButton;
    private Button sellButton;
    private Button placeOrderButton;
    private Button cancelButton;

    private Canvas priceChart;
    private javafx.scene.canvas.GraphicsContext chartContext;

    private double currentPrice = 0.0;
    private double bidPrice = 0.0;
    private double askPrice = 0.0;
    private java.util.Deque<Double> priceHistory = new ConcurrentLinkedDeque<>();
    private SystemCore systemCore;

    public OrderPanel(SystemCore systemCore) {
        setPrefSize(800, 580);
        setPadding(new Insets(0));
        setStyle("-fx-background-color: #0f172a;");

        this.systemCore = systemCore;

        try {
            setupUI(systemCore.getExchange().getTradePairSymbol(), systemCore.getSelectedTradePair());
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            updatePricesFromOrderBook(systemCore.getExchange().fetchOrderBook(systemCore.getSelectedTradePair()).get());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        try {
            LocalizationService.applyTranslations(this);
        } catch (Exception exception) {
            log.debug("Localization skipped for OrderPanel: {}", exception.getMessage());
        }
    }

    private void setupUI(List<TradePair> availableSymbols, TradePair selectedSymbol) {
        setTop(createHeader(selectedSymbol));

        HBox mainContent = new HBox(18);
        mainContent.setPadding(new Insets(18));
        mainContent.setStyle("-fx-background-color: #0f172a;");

        VBox leftPanel = createLeftPanel(selectedSymbol);
        leftPanel.setPrefWidth(430);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        VBox rightPanel = createRightPanel(availableSymbols, selectedSymbol);
        rightPanel.setPrefWidth(430);

        mainContent.getChildren().addAll(leftPanel, createDivider(), rightPanel);

        setCenter(mainContent);
        setBottom(createBottomPanel());

        updateUIForOrderType();
        updateSummary();

    }

    private @NotNull HBox createHeader(TradePair selectedSymbol) {
        Label titleLabel = new Label("Place Order");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label subtitleLabel = new Label("Prepare buy/sell orders with risk controls.");
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        VBox titleBox = new VBox(4, titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        symbolTitleLabel = new Label(selectedSymbol != null ? displaySymbol(selectedSymbol) : "No Symbol");
        symbolTitleLabel.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #38bdf8; " +
                        "-fx-background-color: rgba(56, 189, 248, 0.12); " +
                        "-fx-padding: 8 14; " +
                        "-fx-background-radius: 999;");

        HBox headerBox = new HBox(12, titleBox, spacer, symbolTitleLabel);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(18));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #111827, #16213e); " +
                        "-fx-border-color: #263244; " +
                        "-fx-border-width: 0 0 1 0;");

        return headerBox;
    }

    private VBox createLeftPanel(TradePair selectedSymbol) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(14));
        panel.setStyle(cardStyle());

        Label titleLabel = new Label("Market Snapshot");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label symbolLabel = new Label(selectedSymbol != null ? displaySymbol(selectedSymbol) : "N/A");
        symbolLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #38bdf8;");

        currentPriceLabel = new Label(formatPrice(currentPrice));
        currentPriceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #cbd5e1; -fx-font-weight: bold;");

        HBox quoteCards = new HBox(8);
        quoteCards.getChildren().addAll(
                createQuoteCard("BID", bidPrice, "#10b981"),
                createQuoteCard("ASK", askPrice, "#ef4444"),
                createQuoteCard("SPREAD", Math.abs(askPrice - bidPrice), "#f59e0b"));

        priceChart = new Canvas(350, 160);
        chartContext = priceChart.getGraphicsContext2D();
        priceChart.setStyle("-fx-border-color: #263244; -fx-border-radius: 8;");
        drawPriceChart();

        VBox chartArea = new VBox(8);
        chartArea.setAlignment(Pos.TOP_CENTER);
        chartArea.setPrefHeight(180);
        chartArea.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #0b1120, #111827); " +
                        "-fx-border-color: #263244; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-radius: 10;");

        Label chartLabel = new Label("Price Activity");
        chartLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #cbd5e1;");

        chartArea.getChildren().addAll(chartLabel, priceChart);
        VBox.setVgrow(chartArea, Priority.ALWAYS);

        summaryLabel = new Label("Order summary will appear here.");
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: #cbd5e1; " +
                        "-fx-background-color: #0b1120; " +
                        "-fx-padding: 12; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: #263244; " +
                        "-fx-border-radius: 10;");

        panel.getChildren().addAll(
                titleLabel,
                symbolLabel,
                currentPriceLabel,
                quoteCards,
                chartArea,
                summaryLabel);

        return panel;
    }

    private VBox createQuoteCard(String title, double value, String accentColor) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(10));
        card.setMinWidth(100);
        card.setStyle(
                "-fx-background-color: #0b1120; " +
                        "-fx-border-color: " + accentColor + "; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-radius: 10;");

        Label titleNode = new Label(title);
        titleNode.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");

        Label valueNode = new Label(formatDouble(value));
        valueNode.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        if ("BID".equalsIgnoreCase(title)) {
            bidLabel = valueNode;
        } else if ("ASK".equalsIgnoreCase(title)) {
            askLabel = valueNode;
        } else if ("SPREAD".equalsIgnoreCase(title)) {
            spreadLabel = valueNode;
        }

        card.getChildren().addAll(titleNode, valueNode);
        HBox.setHgrow(card, Priority.ALWAYS);

        return card;
    }

    private VBox createRightPanel(List<TradePair> availableSymbols, TradePair selectedSymbol) {
        VBox panel = new VBox(11);
        panel.setPadding(new Insets(14));
        panel.setStyle(cardStyle());

        Label titleLabel = new Label("Order Ticket");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        symbolCombo = new ComboBox<>();
        if (availableSymbols != null) {
            symbolCombo.getItems().addAll(availableSymbols.stream().map(c -> c.toString('/')).toList());
        }
        if (selectedSymbol != null) {
            symbolCombo.setValue(displaySymbol(selectedSymbol));
        } else if (!symbolCombo.getItems().isEmpty()) {
            symbolCombo.setValue(symbolCombo.getItems().getFirst());
        }
        symbolCombo.setPrefWidth(Double.MAX_VALUE);
        styleInput(symbolCombo);
        symbolCombo.setOnAction(event -> {
            if (symbolTitleLabel != null) {
                symbolTitleLabel.setText(symbolCombo.getValue() == null ? "No Symbol" : symbolCombo.getValue());
            }
            updateSummary();
        });

        volumeSpinner = doubleSpinner(1.0, 0.1);
        volumeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateSummary());

        takeProfitSpinner = doubleSpinner(0.0, 10.0);
        takeProfitSpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateSummary());

        stopLossSpinner = doubleSpinner(0.0, 10.0);
        stopLossSpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateSummary());

        orderTypeCombo = new ComboBox<>();
        orderTypeCombo.getItems().addAll(OpenOrder.OrderType.values());
        orderTypeCombo.setValue(OpenOrder.OrderType.MARKET);
        orderTypeCombo.setPrefWidth(Double.MAX_VALUE);
        styleInput(orderTypeCombo);
        orderTypeCombo.setOnAction(event -> {
            updateUIForOrderType();
            updateSummary();
        });

        sideCombo = new ComboBox<>();
        sideCombo.getItems().addAll(Side.BUY, Side.SELL);
        sideCombo.setValue(Side.BUY);
        sideCombo.setPrefWidth(Double.MAX_VALUE);
        styleInput(sideCombo);
        sideCombo.setOnAction(event -> {
            updateActionButtonStyles();
            updateSummary();
        });

        priceField = new TextField(formatDouble(currentPrice));
        priceField.setPrefWidth(Double.MAX_VALUE);
        styleInput(priceField);
        priceField.textProperty().addListener((obs, oldValue, newValue) -> updateSummary());

        expirationDatePicker = new DatePicker(LocalDate.now().plusDays(1));
        expirationDatePicker.setPrefWidth(Double.MAX_VALUE);
        styleInput(expirationDatePicker);

        commentArea = new TextArea();
        commentArea.setWrapText(true);
        commentArea.setPrefHeight(60);
        commentArea.setPromptText("Optional comment...");
        commentArea.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-control-inner-background: #0b1120; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-prompt-text-fill: #64748b;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        addFormRow(form, 0, "Symbol", symbolCombo);
        addFormRow(form, 1, "Order Type", orderTypeCombo);
        addFormRow(form, 2, "Side", sideCombo);
        addFormRow(form, 3, "Volume", volumeSpinner);
        addFormRow(form, 4, "Price", priceField);
        addFormRow(form, 5, "Take Profit", takeProfitSpinner);
        addFormRow(form, 6, "Stop Loss", stopLossSpinner);
        addFormRow(form, 7, "Expiration", expirationDatePicker);

        Label commentLabel = fieldLabel("Comment");

        panel.getChildren().addAll(
                titleLabel,
                form,
                commentLabel,
                commentArea);

        return panel;
    }

    private HBox createBottomPanel() {
        HBox bottomBox = new HBox(12);
        bottomBox.setPadding(new Insets(16, 18, 18, 18));
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.setStyle(
                "-fx-background-color: #111827; " +
                        "-fx-border-color: #263244; " +
                        "-fx-border-width: 1 0 0 0;");

        buyButton = new Button("BUY");
        buyButton.setPrefWidth(140);
        buyButton.setPrefHeight(46);
        buyButton.setStyle(actionButtonStyle("#10b981"));
        buyButton.setOnAction(event -> {
            sideCombo.setValue(Side.BUY);
            onExecuteOrder(Side.BUY);
        });

        sellButton = new Button("SELL");
        sellButton.setPrefWidth(140);
        sellButton.setPrefHeight(46);
        sellButton.setStyle(actionButtonStyle("#ef4444"));
        sellButton.setOnAction(event -> {
            sideCombo.setValue(Side.SELL);
            onExecuteOrder(Side.SELL);
        });

        placeOrderButton = new Button("PLACE ORDER");
        placeOrderButton.setPrefWidth(160);
        placeOrderButton.setPrefHeight(46);
        placeOrderButton.setStyle(actionButtonStyle("#3b82f6"));
        placeOrderButton.setOnAction(event -> onExecuteOrder(sideCombo.getValue()));

        cancelButton = new Button("CLEAR");
        cancelButton.setPrefWidth(120);
        cancelButton.setPrefHeight(46);
        cancelButton.setStyle(
                "-fx-background-color: #374151; " +
                        "-fx-text-fill: #e5e7eb; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 10; " +
                        "-fx-cursor: hand;");
        cancelButton.setOnAction(event -> onCancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBox.getChildren().addAll(spacer, buyButton, sellButton, placeOrderButton, cancelButton);

        return bottomBox;
    }

    private void addFormRow(GridPane grid, int row, String label, Control control) {
        Label labelNode = fieldLabel(label);
        labelNode.setPrefWidth(85);

        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(control, Priority.ALWAYS);

        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
    }

    private Label fieldLabel(String labelText) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #cbd5e1; -fx-font-weight: bold;");
        return label;
    }

    private Separator createDivider() {
        Separator separator = new Separator();
        separator.setOrientation(Orientation.VERTICAL);
        separator.setStyle("-fx-opacity: 0.25;");
        return separator;
    }

    private void updateUIForOrderType() {
        OpenOrder.OrderType selectedType = orderTypeCombo.getValue();
        boolean isMarket = selectedType == OpenOrder.OrderType.MARKET;
        boolean isLimitOrStopLimit = selectedType == OpenOrder.OrderType.LIMIT ||
                selectedType == OpenOrder.OrderType.STOP_LIMIT;

        priceField.setDisable(isMarket);
        expirationDatePicker.setDisable(isMarket);

        if (isMarket) {
            priceField.setText(formatDouble(currentPrice));
            priceField.setStyle(disabledInputStyle());
            expirationDatePicker.setStyle(disabledInputStyle());
        } else {
            styleInput(priceField);
            styleInput(expirationDatePicker);
        }

        // For LIMIT and STOP_LIMIT orders, highlight stopLoss and takeProfit as
        // required
        if (isLimitOrStopLimit) {
            // Highlight fields with red/green borders to show they are required
            stopLossSpinner.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-background-color: #0b1120; " +
                            "-fx-control-inner-background: #0b1120; " +
                            "-fx-text-fill: #ffffff; " +
                            "-fx-background-radius: 8; " +
                            "-fx-border-color: #ef4444; " +
                            "-fx-border-width: 2; " +
                            "-fx-border-radius: 8; " +
                            "-fx-padding: 6;");
            takeProfitSpinner.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-background-color: #0b1120; " +
                            "-fx-control-inner-background: #0b1120; " +
                            "-fx-text-fill: #ffffff; " +
                            "-fx-background-radius: 8; " +
                            "-fx-border-color: #10b981; " +
                            "-fx-border-width: 2; " +
                            "-fx-border-radius: 8; " +
                            "-fx-padding: 6;");
        } else {
            styleInput(stopLossSpinner);
            styleInput(takeProfitSpinner);
        }

        placeOrderButton.setText(isMarket ? "PLACE MARKET" : "PLACE ORDER");

        updateActionButtonStyles();
    }

    private void updateActionButtonStyles() {
        if (buyButton == null || sellButton == null || sideCombo == null) {
            return;
        }

        Side side = sideCombo.getValue();

        if (side == Side.BUY) {
            buyButton.setStyle(actionButtonStyle("#10b981"));
            sellButton.setStyle(secondaryActionButtonStyle("#ef4444"));
        } else if (side == Side.SELL) {
            buyButton.setStyle(secondaryActionButtonStyle("#10b981"));
            sellButton.setStyle(actionButtonStyle("#ef4444"));
        }
    }

    private void onExecuteOrder(Side requestedSide) {
        try {
            String symbol = symbolCombo.getValue();
            double volume = safeDouble(volumeSpinner.getValue());
            double takeProfit = safeDouble(takeProfitSpinner.getValue());
            double stopLoss = safeDouble(stopLossSpinner.getValue());
            String comment = commentArea.getText() == null ? "" : commentArea.getText().trim();
            OpenOrder.OrderType orderType = orderTypeCombo.getValue();
            Side side = requestedSide == null ? sideCombo.getValue() : requestedSide;

            if (side == null) {
                showError("Please select BUY or SELL.");
                return;
            }

            sideCombo.setValue(side);

            if (symbol == null || symbol.isBlank()) {
                showError(t("order.selectSymbol"));
                return;
            }

            if (volume <= 0.0) {
                showError(t("order.volumePositive"));
                return;
            }

            if (orderType == null) {
                showError("Please select an order type.");
                return;
            }

            double price = resolveOrderPrice(orderType);

            if (orderType != OpenOrder.OrderType.MARKET && price <= 0.0) {
                showError(t("order.pricePositive"));
                return;
            }

            // For LIMIT and STOP_LIMIT orders, always require stoploss and take profit
            // prices
            if (orderType == OpenOrder.OrderType.LIMIT || orderType == OpenOrder.OrderType.STOP_LIMIT) {
                if (stopLoss <= 0.0) {
                    showError("Stop Loss price is required for " + orderType + " orders");
                    return;
                }
                if (takeProfit <= 0.0) {
                    showError("Take Profit price is required for " + orderType + " orders");
                    return;
                }
            }

            LocalDate expirationDate = expirationDatePicker.getValue();

            String newsWarning = checkNewsEventConflict(symbol);
            if (newsWarning != null && !newsWarning.isBlank()) {
                Alert newsAlert = new Alert(Alert.AlertType.WARNING);
                newsAlert.setTitle(t("news.eventWarning"));
                newsAlert.setHeaderText(t("news.upcomingDetected"));
                newsAlert.setContentText(newsWarning + "\n\n" + t("news.proceedQuestion"));

                ButtonType proceedButton = new ButtonType(t("news.proceedAnyway"));
                ButtonType cancelButton = new ButtonType(t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
                newsAlert.getButtonTypes().setAll(proceedButton, cancelButton);

                Optional<ButtonType> result = newsAlert.showAndWait();
                if (result.isEmpty() || result.get() == cancelButton) {
                    log.info("Order cancelled due to news event conflict");
                    return;
                }
            }

            log.info(
                    "Order prepared - Symbol: {}, Type: {}, Side: {}, Volume: {}, Price: {}, TP: {}, SL: {}, Expiration: {}, Comment: {}",
                    symbol,
                    orderType,
                    side,
                    volume,
                    price,
                    takeProfit,
                    stopLoss,
                    expirationDate,
                    comment);

            showSuccess("%s %s order prepared for %s x %.4f at %s".formatted(
                    side,
                    orderType,
                    symbol,
                    volume,
                    orderType == OpenOrder.OrderType.MARKET ? "market" : formatDouble(price)));

        } catch (NumberFormatException exception) {
            showError(t("order.invalidPrice"));
        } catch (Exception exception) {
            log.error("Order preparation failed", exception);
            showError("Order failed: " + exception.getMessage());
        }
    }

    private double resolveOrderPrice(OpenOrder.OrderType orderType) {
        if (orderType == OpenOrder.OrderType.MARKET) {
            return currentPrice;
        }

        String text = priceField.getText();

        if (text == null || text.isBlank()) {
            return 0.0;
        }

        return Double.parseDouble(text.trim());
    }

    private void onCancel() {
        volumeSpinner.getValueFactory().setValue(1.0);
        takeProfitSpinner.getValueFactory().setValue(0.0);
        stopLossSpinner.getValueFactory().setValue(0.0);
        commentArea.clear();
        priceField.setText(formatDouble(currentPrice));
        expirationDatePicker.setValue(LocalDate.now().plusDays(1));
        orderTypeCombo.setValue(OpenOrder.OrderType.MARKET);
        sideCombo.setValue(Side.BUY);

        updateUIForOrderType();
        updateSummary();

        log.info("Order form cleared");
    }

    private void updatePricesFromOrderBook(OrderBook orderBook) {
        if (orderBook == null || orderBook.getBids().isEmpty() || orderBook.getAsks().isEmpty()) {
            return;
        }

        this.bidPrice = orderBook.getBids().getFirst().getPrice();
        this.askPrice = orderBook.getAsks().getFirst().getPrice();
        this.currentPrice = (bidPrice + askPrice) / 2.0;

        // Track price history for chart (keep last 50 prices)
        priceHistory.addLast(currentPrice);
        if (priceHistory.size() > 50) {
            priceHistory.removeFirst();
        }

    }

    private void drawPriceChart() {
        if (priceChart == null || chartContext == null || priceHistory.isEmpty()) {
            return;
        }

        double width = priceChart.getWidth();
        double height = priceChart.getHeight();

        // Clear chart
        chartContext.setFill(Color.web("#0b1120"));
        chartContext.fillRect(0, 0, width, height);

        // Find min/max prices for scaling
        double minPrice = priceHistory.stream().mapToDouble(Double::doubleValue).min().orElse(currentPrice * 0.99);
        double maxPrice = priceHistory.stream().mapToDouble(Double::doubleValue).max().orElse(currentPrice * 1.01);
        double priceRange = maxPrice - minPrice;
        if (priceRange < 0.00001) {
            priceRange = currentPrice * 0.01;
        }

        // Draw grid lines
        chartContext.setStroke(Color.web("#263244"));
        chartContext.setLineWidth(0.5);
        for (int i = 0; i <= 4; i++) {
            double y = (height / 4.0) * i;
            chartContext.strokeLine(0, y, width, y);
        }

        // Draw price area
        java.util.List<Double> prices = new java.util.ArrayList<>(priceHistory);
        int priceCount = prices.size();
        double candleWidth = width / (double) (priceCount + 1);

        for (int i = 0; i < priceCount; i++) {
            double price = prices.get(i);
            double x = (i + 1) * candleWidth;
            double normalizedPrice = (price - minPrice) / priceRange;
            double y = height - (normalizedPrice * height);

            // Draw candlestick
            Color color = price >= currentPrice ? Color.web("#10b981") : Color.web("#ef4444");
            chartContext.setFill(color);

            double candleHeight = Math.max(2, candleWidth * 0.4);
            chartContext.fillRect(x - candleWidth / 3, y - candleHeight / 2, candleWidth / 1.5, candleHeight);
        }

        // Draw current price line
        double normalizedCurrent = (currentPrice - minPrice) / priceRange;
        double currentY = height - (normalizedCurrent * height);
        chartContext.setStroke(Color.web("#38bdf8"));
        chartContext.setLineWidth(2);
        chartContext.strokeLine(0, currentY, width, currentY);

        // Draw bid/ask zone
        double normalizedBid = (bidPrice - minPrice) / priceRange;
        double normalizedAsk = (askPrice - minPrice) / priceRange;
        double bidY = height - (normalizedBid * height);
        double askY = height - (normalizedAsk * height);

        chartContext.setStroke(Color.web("#10b981", 0.4));
        chartContext.setLineWidth(1.5);
        chartContext.strokeLine(0, bidY, width, bidY);

        chartContext.setStroke(Color.web("#ef4444", 0.4));
        chartContext.setLineWidth(1.5);
        chartContext.strokeLine(0, askY, width, askY);
    }

    private void updateSummary() {
        if (summaryLabel == null) {
            return;
        }

        String symbol = symbolCombo == null ? "" : symbolCombo.getValue();
        Side side = sideCombo == null ? Side.BUY : sideCombo.getValue();
        OpenOrder.OrderType type = orderTypeCombo == null ? OpenOrder.OrderType.MARKET : orderTypeCombo.getValue();

        double volume = volumeSpinner == null ? 0.0 : safeDouble(volumeSpinner.getValue());
        double tp = takeProfitSpinner == null ? 0.0 : safeDouble(takeProfitSpinner.getValue());
        double sl = stopLossSpinner == null ? 0.0 : safeDouble(stopLossSpinner.getValue());

        summaryLabel.setText(
                "Summary: %s %s %s units of %s | price=%s | TP=%s | SL=%s".formatted(
                        side == null ? "BUY" : side,
                        type == null ? "MARKET" : type,
                        formatDouble(volume),
                        symbol == null || symbol.isBlank() ? "N/A" : symbol,
                        type == OpenOrder.OrderType.MARKET ? "market"
                                : safeText(priceField == null ? "" : priceField.getText()),
                        tp <= 0.0 ? "none" : formatDouble(tp),
                        sl <= 0.0 ? "none" : formatDouble(sl)));
    }

    private String checkNewsEventConflict(String symbol) {
        String currency = symbol != null && symbol.contains("/") ? symbol.split("/")[1] : symbol;

        Instant now = Instant.now();
        Instant nextHour = now.plusSeconds(3600);

        // Keeping variables to make future integration obvious.
        NewsDataProvider newsDataProvider = new NewsDataProvider();

        return newsDataProvider.fetchAndSummarizeNews(currency, "N/A", 100, nextHour.getNano()).toString();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(t("order.errorTitle"));
        alert.setHeaderText(t("order.failedHeader"));
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(t("order.placedTitle"));
        alert.setHeaderText(t("common.success"));
        alert.setContentText(message);
        alert.showAndWait();
    }

    private @NotNull Spinner<Double> doubleSpinner(double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(0.0, 1000000.0, initial, step);
        spinner.setEditable(true);
        spinner.setPrefWidth(Double.MAX_VALUE);
        spinner.getEditor().setStyle(inputEditorStyle());
        return spinner;
    }

    private void styleInput(Control control) {
        control.setStyle(inputStyle());
    }

    private String inputStyle() {
        return "-fx-font-size: 14px; " +
                "-fx-background-color: #0b1120; " +
                "-fx-control-inner-background: #0b1120; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-prompt-text-fill: #64748b; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #263244; " +
                "-fx-border-radius: 8; " +
                "-fx-padding: 6;";
    }

    private String inputEditorStyle() {
        return "-fx-font-size: 14px; " +
                "-fx-control-inner-background: #0b1120; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-padding: 4;";
    }

    private String disabledInputStyle() {
        return inputStyle() + "-fx-opacity: 0.55;";
    }

    private String actionButtonStyle(String color) {
        return "-fx-background-color: " + color + "; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 10; " +
                "-fx-cursor: hand;";
    }

    private String secondaryActionButtonStyle(String color) {
        return "-fx-background-color: transparent; " +
                "-fx-text-fill: " + color + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-border-color: " + color + "; " +
                "-fx-border-width: 1.5; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-cursor: hand;";
    }

    private String cardStyle() {
        return "-fx-background-color: " + "#111827" + "; " +
                "-fx-border-color: " + "#263244" + "; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: " + 12 + "; " +
                "-fx-background-radius: " + 12 + ";";
    }

    private String displaySymbol(TradePair tradePair) {
        if (tradePair == null) {
            return "";
        }

        try {
            return tradePair.toString('/');
        } catch (Exception ignored) {
            try {
                return tradePair.getSymbol();
            } catch (Exception ignoredAgain) {
                return tradePair.toString();
            }
        }
    }

    private String formatPrice(double value) {
        return "%s: %s".formatted("Mid", formatDouble(value));
    }

    private String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return "0.00000";
        }

        return String.format("%.5f", value);
    }

    private double safeDouble(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0.0;
        }

        return value;
    }

    private @NotNull String safeText(String value) {
        return value == null || value.isBlank() ? "N/A" : value.trim();
    }
}