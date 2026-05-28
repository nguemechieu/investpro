package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Compact Order Panel — single-column order ticket.
 * <p> <p>
 * Layout (top → bottom):
 *   ┌─────────────────────────────┐
 *   │ Symbol badge  │ BID ASK SPR │  ← quote bar
 *   ├─────────────────────────────┤
 *   │ [  BUY  ] [  SELL  ]        │  ← side toggle
 *   │ Order Type  ▼               │
 *   │ Volume  [___]               │
 *   │ Price   [___]  (limit only) │
 *   │ Take Profit [___]           │
 *   │ Stop Loss   [___]           │
 *   │ Expiry  [___]  (limit only) │
 *   ├─────────────────────────────┤
 *   │ Summary line                │
 *   ├─────────────────────────────┤
 *   │  [PLACE ORDER]  [CLEAR]     │
 *   └─────────────────────────────┘
 */
@Slf4j
@Getter
@Setter
public class OrderPanel extends VBox {

    // ── Controls ──────────────────────────────────────────────────────────
    private ComboBox<String>          symbolCombo;
    private ComboBox<OpenOrder.OrderType> orderTypeCombo;
    private Spinner<Double>           volumeSpinner;
    private Spinner<Double>           takeProfitSpinner;
    private Spinner<Double>           stopLossSpinner;
    private TextField                 priceField;
    private DatePicker                expirationDatePicker;

    // Quote labels
    private Label bidLabel;
    private Label askLabel;
    private Label spreadLabel;
    private Label symbolBadge;

    // Side toggle
    private Button buyToggle;
    private Button sellToggle;

    // Action
    private Button placeOrderButton;
    private Button clearButton;

    // Summary
    private Label summaryLabel;

    // State
    private Side     selectedSide  = Side.BUY;
    private double   currentPrice  = 0.0;
    private double   bidPrice      = 0.0;
    private double   askPrice      = 0.0;

    private final SystemCore systemCore;

    // ── Constructors ──────────────────────────────────────────────────────

    public OrderPanel(SystemCore systemCore) {
        this(systemCore, null);
    }

    public OrderPanel(@NonNull SystemCore systemCore, TradePair selectedTradePair) {
        this.systemCore = systemCore;

        setSpacing(0);
        setFillWidth(true);
        setStyle("-fx-background-color: #0f172a;");
        setPrefWidth(360);
        setMaxWidth(400);

        List<TradePair> symbols = loadSymbols();

        TradePair initial = selectedTradePair != null
                ? selectedTradePair
                : systemCore.getSelectedTradePair();
        if (initial == null && !symbols.isEmpty()) initial = symbols.getFirst();

        if (initial != null) {
            try {
                updatePricesFromOrderBook(systemCore.getExchange().fetchOrderBook(initial).get());
            } catch (Exception e) {
                log.warn("OrderPanel: could not fetch initial order book", e);
            }
        }

        buildUI(symbols, initial);

        try { LocalizationService.applyTranslations(this); }
        catch (Exception e) { log.debug("Localization skipped: {}", e.getMessage()); }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private @NonNull List<TradePair> loadSymbols() {
        try {

            List<TradePair> all = new ArrayList<>(systemCore.getExchange().getTradePairSymbol());
            try {
                UniversalTradabilityService svc =
                        new UniversalTradabilityService(systemCore.getExchange(), systemCore.getExchange().getMarketDataEngine());
                List<SymbolTradability> statuses = svc.getTradability(all).get();
                List<TradePair> filtered = statuses.stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(SymbolTradability::marketDataAllowed)
                        .map(SymbolTradability::tradePair)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
                if (!filtered.isEmpty()) return filtered;
            } catch (Exception e) {
                log.warn("Tradability filter unavailable in OrderPanel", e);
            }
            return all;
        } catch (SQLException | ClassNotFoundException e) {
            log.error("OrderPanel: failed to load symbols", e);
            return List.of();
        }
    }

    // ── UI construction ───────────────────────────────────────────────────

    private void buildUI(List<TradePair> symbols, TradePair initial) {
        getChildren().setAll(
            buildQuoteBar(initial),
            divider(),
            buildForm(symbols, initial),
            divider(),
            buildSummaryRow(),
            divider(),
            buildActions()
        );
        refreshSideStyles();
        updateUIForOrderType();
        updateSummary();
    }

    /** Top bar: symbol badge + bid / ask / spread chips. */
    private HBox buildQuoteBar(TradePair initial) {
        symbolBadge = new Label(initial != null ? displaySymbol(initial) : "—");
        symbolBadge.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #38bdf8; " +
                "-fx-background-color: rgba(56,189,248,0.1); " +
                "-fx-padding: 4 10; -fx-background-radius: 999;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox chips = new HBox(6,
                quoteChip("BID",  bidPrice,  "#10b981"),
                quoteChip("ASK",  askPrice,  "#ef4444"),
                quoteChip("SPR",  Math.abs(askPrice - bidPrice), "#f59e0b"));
        chips.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(8, symbolBadge, spacer, chips);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.setStyle("-fx-background-color: #111827;");
        return bar;
    }

    private StackPane quoteChip(String label, double value, String accent) {
        VBox chip = new VBox(1);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(4, 8, 4, 8));
        chip.setStyle(
                "-fx-background-color: #0b1120; " +
                "-fx-border-color: " + accent + "; -fx-border-width: 1; " +
                "-fx-border-radius: 6; -fx-background-radius: 6;");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");

        Label val = new Label(fmt(value));
        val.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");

        if ("BID".equals(label))  bidLabel    = val;
        else if ("ASK".equals(label))  askLabel = val;
        else if ("SPR".equals(label))  spreadLabel = val;

        chip.getChildren().addAll(lbl, val);
        return new StackPane(chip);
    }

    /** Side toggle + form fields. */
    private ScrollPane buildForm(List<TradePair> symbols, TradePair initial) {
        // ── Side toggle ──
        buyToggle  = sideBtn("▲ BUY",  "#10b981");
        sellToggle = sideBtn("▼ SELL", "#ef4444");
        buyToggle.setOnAction(e  -> selectSide(Side.BUY));
        sellToggle.setOnAction(e -> selectSide(Side.SELL));
        HBox.setHgrow(buyToggle, Priority.ALWAYS);
        HBox.setHgrow(sellToggle, Priority.ALWAYS);
        buyToggle.setMaxWidth(Double.MAX_VALUE);
        sellToggle.setMaxWidth(Double.MAX_VALUE);

        HBox sideRow = new HBox(6, buyToggle, sellToggle);
        sideRow.setFillHeight(true);

        // ── Symbol combo ──
        symbolCombo = new ComboBox<>();
        if (symbols != null) {
            symbolCombo.getItems().addAll(
                    symbols.stream().map(p -> p.toString('/')).toList());
        }
        symbolCombo.setValue(initial != null ? displaySymbol(initial) : null);
        if (symbolCombo.getValue() == null && !symbolCombo.getItems().isEmpty())
            symbolCombo.setValue(symbolCombo.getItems().getFirst());
        symbolCombo.setMaxWidth(Double.MAX_VALUE);
        applyInputStyle(symbolCombo);
        symbolCombo.setOnAction(e -> {
            if (symbolBadge != null)
                symbolBadge.setText(symbolCombo.getValue() == null ? "—" : symbolCombo.getValue());
            updateSummary();
        });

        // ── Order type ──
        orderTypeCombo = new ComboBox<>();
        orderTypeCombo.getItems().addAll(OpenOrder.OrderType.values());
        orderTypeCombo.setValue(OpenOrder.OrderType.MARKET);
        orderTypeCombo.setMaxWidth(Double.MAX_VALUE);
        applyInputStyle(orderTypeCombo);
        orderTypeCombo.setOnAction(e -> { updateUIForOrderType(); updateSummary(); });

        // ── Volume ──
        volumeSpinner = dblSpinner(1.0, 0.1);
        volumeSpinner.valueProperty().addListener((o, ov, nv) -> updateSummary());

        // ── Price (limit/stop only) ──
        priceField = new TextField(fmt(currentPrice));
        priceField.setMaxWidth(Double.MAX_VALUE);
        applyInputStyle(priceField);
        priceField.textProperty().addListener((o, ov, nv) -> updateSummary());

        // ── TP / SL ──
        takeProfitSpinner = dblSpinner(0.0, 10.0);
        stopLossSpinner   = dblSpinner(0.0, 10.0);
        takeProfitSpinner.valueProperty().addListener((o, ov, nv) -> updateSummary());
        stopLossSpinner.valueProperty().addListener((o, ov, nv) -> updateSummary());

        // ── Expiry (limit/stop only) ──
        expirationDatePicker = new DatePicker(LocalDate.now().plusDays(1));
        expirationDatePicker.setMaxWidth(Double.MAX_VALUE);
        applyInputStyle(expirationDatePicker);

        // ── Grid layout ──
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(8);
        ColumnConstraints labelCol = new ColumnConstraints(80);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        addRow(grid, 0, "Symbol",     symbolCombo);
        addRow(grid, 1, "Type",       orderTypeCombo);
        addRow(grid, 2, "Volume",     volumeSpinner);
        addRow(grid, 3, "Price",      priceField);
        addRow(grid, 4, "Take Profit",takeProfitSpinner);
        addRow(grid, 5, "Stop Loss",  stopLossSpinner);
        addRow(grid, 6, "Expiry",     expirationDatePicker);

        VBox content = new VBox(10, sideRow, grid);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #0f172a;");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0f172a;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private HBox buildSummaryRow() {
        summaryLabel = new Label("Configure order above");
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        HBox row = new HBox(summaryLabel);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #111827;");
        return row;
    }

    private HBox buildActions() {
        placeOrderButton = new Button("PLACE ORDER");
        placeOrderButton.setPrefHeight(38);
        placeOrderButton.setStyle(btnStyle("#3b82f6"));
        placeOrderButton.setOnAction(e -> onExecuteOrder(selectedSide));
        HBox.setHgrow(placeOrderButton, Priority.ALWAYS);
        placeOrderButton.setMaxWidth(Double.MAX_VALUE);

        clearButton = new Button("CLEAR");
        clearButton.setPrefHeight(38);
        clearButton.setPrefWidth(80);
        clearButton.setStyle(btnStyle("#374151"));
        clearButton.setOnAction(e -> onClear());

        HBox row = new HBox(8, placeOrderButton, clearButton);
        row.setPadding(new Insets(10, 12, 12, 12));
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setStyle("-fx-background-color: #111827;");
        return row;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addRow(GridPane grid, int row, String labelText, Control control) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        lbl.setWrapText(false);
        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(control, true);
        grid.add(lbl, 0, row);
        grid.add(control, 1, row);
    }

    private Button sideBtn(String text, String accent) {
        Button b = new Button(text);
        b.setPrefHeight(36);
        b.setStyle(sideBtnOutlineStyle(accent));
        return b;
    }

    private Region divider() {
        Region d = new Region();
        d.setPrefHeight(1);
        d.setStyle("-fx-background-color: #1e293b;");
        return d;
    }

    private @NotNull Spinner<Double> dblSpinner(double initial, double step) {
        Spinner<Double> s = new Spinner<>(0.0, 1_000_000.0, initial, step);
        s.setEditable(true);
        s.setMaxWidth(Double.MAX_VALUE);
        s.getEditor().setStyle(
                "-fx-background-color: #0b1120; -fx-text-fill: #ffffff; " +
                "-fx-font-size: 13px; -fx-padding: 4;");
        applyInputStyle(s);
        return s;
    }

    private void applyInputStyle(Control c) {
        c.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-background-color: #0b1120; " +
                "-fx-control-inner-background: #0b1120; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-prompt-text-fill: #475569; " +
                "-fx-background-radius: 6; " +
                "-fx-border-color: #1e293b; " +
                "-fx-border-radius: 6; " +
                "-fx-padding: 5;");
    }

    private String btnStyle(String bg) {
        return "-fx-background-color: " + bg + "; " +
               "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; " +
               "-fx-background-radius: 8; -fx-cursor: hand;";
    }

    private String sideBtnOutlineStyle(String accent) {
        return "-fx-background-color: transparent; " +
               "-fx-text-fill: " + accent + "; -fx-font-size: 13px; -fx-font-weight: bold; " +
               "-fx-border-color: " + accent + "; -fx-border-width: 1.5; " +
               "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    private String sideBtnFilledStyle(String accent) {
        return "-fx-background-color: " + accent + "; " +
               "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; " +
               "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    private void selectSide(Side side) {
        this.selectedSide = side;
        refreshSideStyles();
        updateSummary();
    }

    private void refreshSideStyles() {
        if (buyToggle == null || sellToggle == null) return;
        if (selectedSide == Side.BUY) {
            buyToggle.setStyle(sideBtnFilledStyle("#10b981"));
            sellToggle.setStyle(sideBtnOutlineStyle("#ef4444"));
        } else {
            buyToggle.setStyle(sideBtnOutlineStyle("#10b981"));
            sellToggle.setStyle(sideBtnFilledStyle("#ef4444"));
        }
    }

    private void updateUIForOrderType() {
        if (orderTypeCombo == null) return;
        boolean isMarket = orderTypeCombo.getValue() == OpenOrder.OrderType.MARKET;

        if (priceField != null) {
            priceField.setDisable(isMarket);
            priceField.setStyle(isMarket
                    ? applyInputStyleStr() + "-fx-opacity: 0.45;"
                    : applyInputStyleStr());
            if (isMarket) priceField.setText(fmt(currentPrice));
        }
        if (expirationDatePicker != null) {
            expirationDatePicker.setDisable(isMarket);
            expirationDatePicker.setStyle(isMarket
                    ? applyInputStyleStr() + "-fx-opacity: 0.45;"
                    : applyInputStyleStr());
        }
        if (placeOrderButton != null) {
            placeOrderButton.setText(isMarket ? "PLACE MARKET" : "PLACE ORDER");
        }

        boolean needsRisk = orderTypeCombo.getValue() == OpenOrder.OrderType.LIMIT
                || orderTypeCombo.getValue() == OpenOrder.OrderType.STOP_LIMIT;
        if (stopLossSpinner != null) {
            stopLossSpinner.setStyle(needsRisk
                    ? applyInputStyleStr() + "-fx-border-color: #ef4444; -fx-border-width: 2;"
                    : applyInputStyleStr());
        }
        if (takeProfitSpinner != null) {
            takeProfitSpinner.setStyle(needsRisk
                    ? applyInputStyleStr() + "-fx-border-color: #10b981; -fx-border-width: 2;"
                    : applyInputStyleStr());
        }
    }

    private String applyInputStyleStr() {
        return "-fx-font-size: 13px; " +
               "-fx-background-color: #0b1120; " +
               "-fx-control-inner-background: #0b1120; " +
               "-fx-text-fill: #ffffff; " +
               "-fx-prompt-text-fill: #475569; " +
               "-fx-background-radius: 6; " +
               "-fx-border-color: #1e293b; " +
               "-fx-border-radius: 6; " +
               "-fx-padding: 5;";
    }

    private void updateSummary() {
        if (summaryLabel == null) return;
        String sym  = symbolCombo != null ? symbolCombo.getValue() : "N/A";
        double vol  = volumeSpinner  != null ? safeD(volumeSpinner.getValue())  : 0;
        double tp   = takeProfitSpinner != null ? safeD(takeProfitSpinner.getValue()) : 0;
        double sl   = stopLossSpinner   != null ? safeD(stopLossSpinner.getValue())   : 0;
        OpenOrder.OrderType type = orderTypeCombo != null
                ? orderTypeCombo.getValue() : OpenOrder.OrderType.MARKET;

        summaryLabel.setText("%s %s  %s  vol=%.2f  TP=%s  SL=%s".formatted(
                selectedSide, type,
                sym == null ? "N/A" : sym,
                vol,
                tp <= 0 ? "—" : fmt(tp),
                sl <= 0 ? "—" : fmt(sl)));
    }

    // ── Order execution ───────────────────────────────────────────────────

    private void onExecuteOrder(Side side) {
        try {
            String sym = symbolCombo.getValue();
            double vol  = safeD(volumeSpinner.getValue());
            double tp   = safeD(takeProfitSpinner.getValue());
            double sl   = safeD(stopLossSpinner.getValue());
            OpenOrder.OrderType type = orderTypeCombo.getValue();

            if (sym == null || sym.isBlank()) { showError(t("order.selectSymbol")); return; }
            if (vol <= 0)                     { showError(t("order.volumePositive")); return; }
            if (type == null)                 { showError("Select an order type."); return; }

            double price = type == OpenOrder.OrderType.MARKET
                    ? currentPrice
                    : parsePrice();

            if (type != OpenOrder.OrderType.MARKET && price <= 0) {
                showError(t("order.pricePositive")); return;
            }
            if ((type == OpenOrder.OrderType.LIMIT || type == OpenOrder.OrderType.STOP_LIMIT)
                    && (sl <= 0 || tp <= 0)) {
                showError("Stop Loss and Take Profit are required for " + type + " orders.");
                return;
            }

            log.info("Order → sym={} type={} side={} vol={} price={} tp={} sl={}",
                    sym, type, side, vol, price, tp, sl);
            showSuccess("%s %s  %s × %.4f @ %s".formatted(side, type, sym, vol,
                    type == OpenOrder.OrderType.MARKET ? "market" : fmt(price)));

        } catch (NumberFormatException e) {
            showError(t("order.invalidPrice"));
        } catch (Exception e) {
            log.error("Order preparation failed", e);
            showError("Order failed: " + e.getMessage());
        }
    }

    private double parsePrice() {
        if (priceField == null) return 0;
        try { return Double.parseDouble(priceField.getText().trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private void onClear() {
        volumeSpinner.getValueFactory().setValue(1.0);
        takeProfitSpinner.getValueFactory().setValue(0.0);
        stopLossSpinner.getValueFactory().setValue(0.0);
        priceField.setText(fmt(currentPrice));
        expirationDatePicker.setValue(LocalDate.now().plusDays(1));
        orderTypeCombo.setValue(OpenOrder.OrderType.MARKET);
        selectedSide = Side.BUY;
        refreshSideStyles();
        updateUIForOrderType();
        updateSummary();
    }

    // ── Price update ──────────────────────────────────────────────────────

    private void updatePricesFromOrderBook(OrderBook orderBook) {
        if (orderBook == null
                || orderBook.getBids() == null || orderBook.getBids().isEmpty()
                || orderBook.getAsks() == null || orderBook.getAsks().isEmpty()) return;

        this.bidPrice    = orderBook.getBids().getFirst().getPrice();
        this.askPrice    = orderBook.getAsks().getFirst().getPrice();
        this.currentPrice = (bidPrice + askPrice) / 2.0;

        if (bidLabel    != null) bidLabel.setText(fmt(bidPrice));
        if (askLabel    != null) askLabel.setText(fmt(askPrice));
        if (spreadLabel != null) spreadLabel.setText(fmt(Math.abs(askPrice - bidPrice)));
        if (priceField  != null && !priceField.isDisable()) priceField.setText(fmt(currentPrice));
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(t("order.errorTitle"));
        a.setHeaderText(t("order.failedHeader"));
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(t("order.placedTitle"));
        a.setHeaderText(t("common.success"));
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Formatting ────────────────────────────────────────────────────────

    private String fmt(double v) {
        return Double.isFinite(v) ? String.format("%.5f", v) : "0.00000";
    }

    private double safeD(Double v) {
        return v == null || !Double.isFinite(v) ? 0.0 : v;
    }

    private String displaySymbol(TradePair tp) {
        if (tp == null) return "";
        try { return tp.toString('/'); }
        catch (Exception e) {
            try { return tp.getSymbol(); }
            catch (Exception ex) { return tp.toString(); }
        }
    }
}
