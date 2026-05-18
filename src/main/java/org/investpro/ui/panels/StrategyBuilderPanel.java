package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.data.CandleData;
import org.investpro.core.SystemCore;
import org.investpro.enums.StrategyCategory;
import org.investpro.indicators.INDICATORS;
import org.investpro.indicators.INDICATORS.IndicatorCategory;
import org.investpro.i18n.LocalizationService;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.HistoricalDataRepository;
import org.investpro.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.lab.StrategyBacktestRequest;
import org.investpro.strategy.lab.StrategyBacktestRunner;
import org.investpro.strategy.lab.StrategyPerformanceReport;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Strategy Builder Panel — lets users compose custom trading strategies from
 * real indicators with editable parameters, validate, save, and test them.
 */
@Slf4j
@Getter
@Setter
public class StrategyBuilderPanel extends VBox {

    // Session-scoped registry for user-built strategies (accessible by name)
    public static final Map<String, StrategyDefinition> USER_DEFINED_STRATEGIES = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Indicator → default parameter definitions  [paramName, defaultValue]
    // -----------------------------------------------------------------------
    private static final Map<INDICATORS, List<String[]>> INDICATOR_DEFAULTS;

    static {
        Map<INDICATORS, List<String[]>> m = new LinkedHashMap<>();
        m.put(INDICATORS.RSI,               defaults(p("period","14"), p("oversold","35.0"), p("overbought","65.0")));
        m.put(INDICATORS.SMA,               defaults(p("period","20")));
        m.put(INDICATORS.EMA,               defaults(p("period","20")));
        m.put(INDICATORS.EMA_FAST,          defaults(p("period","12")));
        m.put(INDICATORS.EMA_SLOW,          defaults(p("period","26")));
        m.put(INDICATORS.WMA,               defaults(p("period","20")));
        m.put(INDICATORS.HMA,               defaults(p("period","20")));
        m.put(INDICATORS.DEMA,              defaults(p("period","20")));
        m.put(INDICATORS.TEMA,              defaults(p("period","20")));
        m.put(INDICATORS.VWMA,              defaults(p("period","20")));
        m.put(INDICATORS.KAMA,              defaults(p("period","10")));
        m.put(INDICATORS.ZLEMA,             defaults(p("period","20")));
        m.put(INDICATORS.MA_CROSSOVER,      defaults(p("fastPeriod","12"), p("slowPeriod","26")));
        m.put(INDICATORS.MACD,              defaults(p("fastPeriod","12"), p("slowPeriod","26"), p("signalPeriod","9")));
        m.put(INDICATORS.MACD_LINE,         defaults(p("fastPeriod","12"), p("slowPeriod","26")));
        m.put(INDICATORS.MACD_SIGNAL,       defaults(p("signalPeriod","9")));
        m.put(INDICATORS.MACD_HISTOGRAM,    defaults(p("fastPeriod","12"), p("slowPeriod","26"), p("signalPeriod","9")));
        m.put(INDICATORS.ATR,               defaults(p("period","14")));
        m.put(INDICATORS.ATR_PERCENT,       defaults(p("period","14")));
        m.put(INDICATORS.BOLLINGER_BANDS,   defaults(p("period","20"), p("stdDevMult","2.0")));
        m.put(INDICATORS.BOLLINGER_UPPER,   defaults(p("period","20"), p("stdDevMult","2.0")));
        m.put(INDICATORS.BOLLINGER_MIDDLE,  defaults(p("period","20")));
        m.put(INDICATORS.BOLLINGER_LOWER,   defaults(p("period","20"), p("stdDevMult","2.0")));
        m.put(INDICATORS.BOLLINGER_WIDTH,   defaults(p("period","20"), p("stdDevMult","2.0")));
        m.put(INDICATORS.BOLLINGER_PERCENT_B, defaults(p("period","20"), p("stdDevMult","2.0")));
        m.put(INDICATORS.KELTNER_CHANNEL,   defaults(p("emaPeriod","20"), p("atrPeriod","14"), p("atrMult","1.5")));
        m.put(INDICATORS.KELTNER_UPPER,     defaults(p("emaPeriod","20"), p("atrPeriod","14"), p("atrMult","1.5")));
        m.put(INDICATORS.KELTNER_LOWER,     defaults(p("emaPeriod","20"), p("atrPeriod","14"), p("atrMult","1.5")));
        m.put(INDICATORS.DONCHIAN_CHANNEL,  defaults(p("period","20")));
        m.put(INDICATORS.DONCHIAN_HIGH,     defaults(p("period","20")));
        m.put(INDICATORS.DONCHIAN_LOW,      defaults(p("period","20")));
        m.put(INDICATORS.STANDARD_DEVIATION, defaults(p("period","20")));
        m.put(INDICATORS.HISTORICAL_VOLATILITY, defaults(p("period","20")));
        m.put(INDICATORS.STOCHASTIC,        defaults(p("kPeriod","14"), p("dPeriod","3"), p("smooth","3")));
        m.put(INDICATORS.STOCHASTIC_K,      defaults(p("kPeriod","14"), p("smooth","3")));
        m.put(INDICATORS.STOCHASTIC_D,      defaults(p("dPeriod","3")));
        m.put(INDICATORS.STOCH_RSI,         defaults(p("rsiPeriod","14"), p("stochPeriod","14"), p("kSmooth","3"), p("dSmooth","3")));
        m.put(INDICATORS.CCI,               defaults(p("period","20")));
        m.put(INDICATORS.MOMENTUM,          defaults(p("period","10")));
        m.put(INDICATORS.ROC,               defaults(p("period","12")));
        m.put(INDICATORS.WILLIAMS_R,        defaults(p("period","14")));
        m.put(INDICATORS.TRIX,              defaults(p("period","15")));
        m.put(INDICATORS.ULTIMATE_OSCILLATOR, defaults(p("period1","7"), p("period2","14"), p("period3","28")));
        m.put(INDICATORS.ADX,               defaults(p("period","14")));
        m.put(INDICATORS.PSAR,              defaults(p("step","0.02"), p("maxStep","0.2")));
        m.put(INDICATORS.ICHIMOKU,          defaults(p("tenkan","9"), p("kijun","26"), p("senkouB","52")));
        m.put(INDICATORS.ICHIMOKU_TENKAN,   defaults(p("period","9")));
        m.put(INDICATORS.ICHIMOKU_KIJUN,    defaults(p("period","26")));
        m.put(INDICATORS.ICHIMOKU_SENKOU_A, defaults());
        m.put(INDICATORS.ICHIMOKU_SENKOU_B, defaults(p("period","52")));
        m.put(INDICATORS.ICHIMOKU_CHIKOU,   defaults(p("displacement","26")));
        m.put(INDICATORS.VOLUME_SMA,        defaults(p("period","20")));
        m.put(INDICATORS.VOLUME_RATIO,      defaults(p("period","20")));
        m.put(INDICATORS.MFI,               defaults(p("period","14")));
        m.put(INDICATORS.CMF,               defaults(p("period","20")));
        m.put(INDICATORS.VWAP,              defaults());
        m.put(INDICATORS.OBV,               defaults());
        m.put(INDICATORS.ADL,               defaults());
        m.put(INDICATORS.VOLUME_SPIKE,      defaults(p("thresholdMult","2.0"), p("period","20")));
        INDICATOR_DEFAULTS = Collections.unmodifiableMap(m);
    }

    private static String[] p(String name, String def) {
        return new String[]{name, def};
    }

    @SafeVarargs
    private static List<String[]> defaults(String[]... entries) {
        return List.of(entries);
    }

    // -----------------------------------------------------------------------
    // UI fields
    // -----------------------------------------------------------------------
    private TextField strategyNameField;
    private ComboBox<StrategyCategory> categoryCombo;
    private ComboBox<Timeframe> timeframeCombo;
    private ComboBox<INDICATORS.IndicatorCategory> categoryFilterCombo;
    private ComboBox<INDICATORS> indicatorCombo;
    private TextArea strategyDescriptionArea;
    private TableView<StrategyParameterRow> parametersTable;
    private ObservableList<StrategyParameterRow> parameterRows;
    private Label paramsPreviewLabel;
    private SystemCore systemCore;

    public StrategyBuilderPanel(SystemCore systemCore) {
        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("strategy-builder-panel");
        this.systemCore = systemCore;
        parameterRows = FXCollections.observableArrayList();
        setupUI();
        LocalizationService.applyTranslations(this);
    }

    // -----------------------------------------------------------------------
    // UI setup
    // -----------------------------------------------------------------------
    private void setupUI() {
        Label titleLabel = new Label("Strategy Builder");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        VBox basicInfoBox    = createBasicInfoSection();
        VBox indicatorsBox   = createIndicatorsSection();
        VBox logicBox        = createStrategyLogicSection();
        VBox previewBox      = createParamsPreviewSection();
        HBox actionBox       = createActionButtons();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(basicInfoBox, new Separator(), indicatorsBox, new Separator(), logicBox, new Separator(), previewBox);
        scrollPane.setContent(content);

        getChildren().addAll(titleLabel, scrollPane, actionBox);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createBasicInfoSection() {
        VBox section = styledSection("#3b82f6");

        Label sectionTitle = sectionHeader("Basic Information");

        strategyNameField = new TextField();
        strategyNameField.setPromptText("e.g., My Mean Reversion Bot");
        strategyNameField.setPrefHeight(35);
        styleInput(strategyNameField);

        categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(StrategyCategory.values());
        categoryCombo.setPrefHeight(35);
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        categoryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(StrategyCategory c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getDisplayName());
            }
        });
        categoryCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(StrategyCategory c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? "Select category…" : c.getDisplayName());
            }
        });

        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().addAll(Timeframe.values());
        timeframeCombo.setPrefHeight(35);
        timeframeCombo.setMaxWidth(Double.MAX_VALUE);

        section.getChildren().addAll(sectionTitle,
                createLabeledInput("Strategy Name:", strategyNameField),
                createLabeledInput("Category:", categoryCombo),
                createLabeledInput("Primary Timeframe:", timeframeCombo));
        return section;
    }

    private VBox createIndicatorsSection() {
        VBox section = styledSection("#10b981");

        Label sectionTitle = sectionHeader("Indicators & Parameters");

        // Category filter
        categoryFilterCombo = new ComboBox<>();
        categoryFilterCombo.getItems().add(null); // "All"
        categoryFilterCombo.getItems().addAll(IndicatorCategory.values());
        categoryFilterCombo.setPrefHeight(32);
        categoryFilterCombo.setMaxWidth(Double.MAX_VALUE);
        categoryFilterCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(IndicatorCategory ic, boolean empty) {
                super.updateItem(ic, empty);
                setText(empty || ic == null ? "All categories" : ic.name());
            }
        });
        categoryFilterCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(IndicatorCategory ic, boolean empty) {
                super.updateItem(ic, empty);
                setText(empty || ic == null ? "All categories" : ic.name());
            }
        });

        // Indicator picker — populated dynamically based on filter
        indicatorCombo = new ComboBox<>();
        indicatorCombo.setPrefHeight(32);
        indicatorCombo.setMaxWidth(Double.MAX_VALUE);
        Callback<INDICATORS> indicatorCellFactory = () -> new ListCell<>() {
            @Override protected void updateItem(INDICATORS ind, boolean empty) {
                super.updateItem(ind, empty);
                if (empty || ind == null) { setText(null); return; }
                setText("[" + ind.getCategory().name() + "] " + ind.getDisplayName());
            }
        };
        indicatorCombo.setCellFactory(lv -> indicatorCellFactory.create());
        indicatorCombo.setButtonCell(indicatorCellFactory.create());

        // Wire category filter → indicator list
        refreshIndicatorCombo(null);
        categoryFilterCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, selected) -> refreshIndicatorCombo(selected));

        // Tooltip shows description of selected indicator
        indicatorCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) indicatorCombo.setTooltip(new Tooltip(sel.getDescription()));
        });

        Button addBtn = new Button("+ Add");
        addBtn.setStyle("-fx-padding: 6px 18px; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> addIndicatorToTable());

        HBox pickerRow = new HBox(8,
                createLabeledInput("Filter:", categoryFilterCombo),
                createLabeledInput("Indicator:", indicatorCombo),
                addBtn);
        pickerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(indicatorCombo, Priority.ALWAYS);

        // Editable parameters table
        parametersTable = buildParametersTable();
        parametersTable.setItems(parameterRows);
        parametersTable.setPrefHeight(200);

        section.getChildren().addAll(sectionTitle, pickerRow,
                styledLabel("Configured Indicators / Parameters (double-click Value to edit):"),
                parametersTable);
        VBox.setVgrow(parametersTable, Priority.ALWAYS);
        return section;
    }

    private void refreshIndicatorCombo(IndicatorCategory filter) {
        INDICATORS prev = indicatorCombo.getValue();
        List<INDICATORS> filtered = Arrays.stream(INDICATORS.values())
                .filter(i -> filter == null || i.getCategory() == filter)
                .collect(Collectors.toList());
        indicatorCombo.getItems().setAll(filtered);
        if (prev != null && filtered.contains(prev)) indicatorCombo.setValue(prev);
        else if (!filtered.isEmpty()) indicatorCombo.getSelectionModel().selectFirst();
    }

    private TableView<StrategyParameterRow> buildParametersTable() {
        TableView<StrategyParameterRow> table = new TableView<>();
        table.setEditable(true);
        table.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<StrategyParameterRow, String> indCol = new TableColumn<>("Indicator");
        indCol.setCellValueFactory(c -> c.getValue().indicatorNameProperty());
        indCol.setEditable(false);
        indCol.setPrefWidth(200);

        TableColumn<StrategyParameterRow, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> c.getValue().parameterNameProperty());
        paramCol.setEditable(false);
        paramCol.setPrefWidth(160);

        TableColumn<StrategyParameterRow, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> c.getValue().valueProperty());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(evt -> {
            evt.getRowValue().setValue(evt.getNewValue());
            updatePreview();
        });
        valueCol.setEditable(true);
        valueCol.setPrefWidth(120);

        TableColumn<StrategyParameterRow, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(70);
        actionCol.setResizable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("✕");
            {
                removeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 2px 8px; -fx-font-size: 11px;");
                removeBtn.setOnAction(e -> {
                    StrategyParameterRow row = getTableView().getItems().get(getIndex());
                    parameterRows.remove(row);
                    updatePreview();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });

        table.getColumns().addAll(indCol, paramCol, valueCol, actionCol);
        return table;
    }

    private VBox createStrategyLogicSection() {
        VBox section = styledSection("#f59e0b");
        section.getChildren().add(sectionHeader("Strategy Description & Rules"));

        strategyDescriptionArea = new TextArea();
        strategyDescriptionArea.setPromptText(
                "Describe your strategy's entry/exit logic here.\n" +
                "e.g.:\n" +
                "  Entry: RSI < oversold AND price above EMA(200)\n" +
                "  Exit:  RSI > 60 OR ATR stop hit\n" +
                "  Risk:  1% account risk per trade");
        strategyDescriptionArea.setWrapText(true);
        strategyDescriptionArea.setPrefHeight(130);
        strategyDescriptionArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        section.getChildren().add(strategyDescriptionArea);
        VBox.setVgrow(strategyDescriptionArea, Priority.ALWAYS);
        return section;
    }

    private VBox createParamsPreviewSection() {
        VBox section = styledSection("#6366f1");
        section.getChildren().add(sectionHeader("Assembled StrategyParameters Preview"));
        paramsPreviewLabel = new Label("(no indicators added yet)");
        paramsPreviewLabel.setStyle("-fx-text-fill: #c7d2fe; -fx-font-family: monospace; -fx-font-size: 11px;");
        paramsPreviewLabel.setWrapText(true);
        section.getChildren().add(paramsPreviewLabel);
        return section;
    }

    private HBox createActionButtons() {
        Button validateBtn = styledBtn("✓ Validate", "#3b82f6");
        validateBtn.setOnAction(e -> validateStrategy());

        Button saveBtn = styledBtn("💾 Save", "#10b981");
        saveBtn.setOnAction(e -> saveStrategy());

        Button testBtn = styledBtn("🧪 Test / Backtest", "#f59e0b");
        testBtn.setOnAction(e -> testStrategy());

        Button resetBtn = styledBtn("↻ Reset", "#ef4444");
        resetBtn.setOnAction(e -> resetForm());

        HBox box = new HBox(12, validateBtn, saveBtn, testBtn, resetBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(12));
        return box;
    }

    // -----------------------------------------------------------------------
    // Business logic
    // -----------------------------------------------------------------------
    private void addIndicatorToTable() {
        INDICATORS selected = indicatorCombo.getValue();
        if (selected == null) {
            showAlert("No indicator selected", "Please choose an indicator from the list.");
            return;
        }
        List<String[]> defaults = INDICATOR_DEFAULTS.getOrDefault(selected, defaults(p("period", "14")));
        if (defaults.isEmpty()) {
            // Add a single no-param row so the indicator appears in the table
            parameterRows.add(new StrategyParameterRow(selected.getDisplayName(), "(no parameters)", ""));
        } else {
            for (String[] kv : defaults) {
                parameterRows.add(new StrategyParameterRow(selected.getDisplayName(), kv[0], kv[1]));
            }
        }
        log.info("Added indicator {} with {} parameter(s)", selected.getDisplayName(), defaults.size());
        updatePreview();
    }

    /** Assembles StrategyParameters from the current table rows. */
    private StrategyParameters buildParameters() {
        StrategyParameters.StrategyParametersBuilder builder = StrategyParameters.builder();
        for (StrategyParameterRow row : parameterRows) {
            String param = row.getParameterName().toLowerCase(Locale.ROOT).replace(" ", "");
            String val   = row.getValue().trim();
            if (val.isEmpty() || val.equals("(no parameters)")) continue;
            try {
                switch (param) {
                    case "period" -> {
                        // Route "period" to the correct field based on indicator name
                        String ind = row.getIndicatorName().toLowerCase(Locale.ROOT);
                        if (ind.contains("rsi"))        builder.rsiPeriod(Integer.parseInt(val));
                        else if (ind.contains("atr"))   builder.atrPeriod(Integer.parseInt(val));
                        else if (ind.contains("fast"))  builder.emaFast(Integer.parseInt(val));
                        else if (ind.contains("slow"))  builder.emaSlow(Integer.parseInt(val));
                        else if (ind.contains("breakout") || ind.contains("donchian")) builder.breakoutLookback(Integer.parseInt(val));
                    }
                    case "rsiperiod"      -> builder.rsiPeriod(Integer.parseInt(val));
                    case "fastperiod"     -> builder.emaFast(Integer.parseInt(val));
                    case "slowperiod"     -> builder.emaSlow(Integer.parseInt(val));
                    case "atrperiod"      -> builder.atrPeriod(Integer.parseInt(val));
                    case "breakoutlookback" -> builder.breakoutLookback(Integer.parseInt(val));
                    case "oversold"       -> builder.oversoldThreshold(Double.parseDouble(val));
                    case "overbought"     -> builder.overboughtThreshold(Double.parseDouble(val));
                    case "minconfidence"  -> builder.minConfidence(Double.parseDouble(val));
                    case "signalamount"   -> builder.signalAmount(Double.parseDouble(val));
                    default -> { /* indicator-specific param — stored in description/metadata */ }
                }
            } catch (NumberFormatException ex) {
                log.warn("Non-numeric value '{}' for param '{}' — skipped", val, param);
            }
        }
        return builder.build();
    }

    private void updatePreview() {
        if (paramsPreviewLabel == null) return;
        if (parameterRows.isEmpty()) {
            paramsPreviewLabel.setText("(no indicators added yet)");
            return;
        }
        StrategyParameters sp = buildParameters();
        String preview = String.format(
                "rsiPeriod=%d  emaFast=%d  emaSlow=%d  atrPeriod=%d  breakoutLookback=%d%n" +
                "oversold=%.1f  overbought=%.1f  minConfidence=%.2f  signalAmount=%.2f",
                sp.getRsiPeriod(), sp.getEmaFast(), sp.getEmaSlow(),
                sp.getAtrPeriod(), sp.getBreakoutLookback(),
                sp.getOversoldThreshold(), sp.getOverboughtThreshold(),
                sp.getMinConfidence(), sp.getSignalAmount());
        paramsPreviewLabel.setText(preview);
    }

    private boolean validateStrategy() {
        String name = strategyNameField.getText().trim();
        if (name.isEmpty()) {
            showAlert("Validation Error", "Please enter a strategy name.");
            return false;
        }
        if (categoryCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a category.");
            return false;
        }
        if (timeframeCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a primary timeframe.");
            return false;
        }
        if (parameterRows.isEmpty()) {
            showAlert("Validation Error", "Please add at least one indicator.");
            return false;
        }
        // Check all editable values are numeric where applicable
        for (StrategyParameterRow row : parameterRows) {
            String val = row.getValue().trim();
            if (val.isEmpty() || val.equals("(no parameters)")) continue;
            try { Double.parseDouble(val); } catch (NumberFormatException e) {
                showAlert("Validation Error",
                        "Parameter '" + row.getParameterName() + "' of " + row.getIndicatorName() +
                        " has non-numeric value: " + val);
                return false;
            }
        }
        showAlert("Validation Passed ✓",
                "Strategy '" + name + "' is valid and ready to save.");
        log.info("Strategy '{}' validated OK", name);
        return true;
    }

    private void saveStrategy() {
        if (!validateStrategy()) {
            return;
        }

        StrategyDefinition definition = currentStrategyDefinition();
        persistStrategyDefinition(definition);

        String indicatorSummary = parameterRows.stream()
                .map(StrategyParameterRow::getIndicatorName)
                .distinct()
                .collect(Collectors.joining(", "));

        showAlert("Strategy Saved ✓",
                "'" + definition.getName() + "' is now registered in the live strategy registry.\n"
                        + "Indicators: " + indicatorSummary
                        + "\n\nIt can now be assigned from the Strategy Lab, Backtesting, or decision engines.");
        log.info("User strategy '{}' saved and registered with params: {}", definition.getName(), definition.getParameters());
    }

    private void testStrategy() {
        if (!validateStrategy()) {
            return;
        }

        StrategyDefinition definition = currentStrategyDefinition();
        persistStrategyDefinition(definition);

        Optional<TradePair> testPair = resolveTestPairPair();
        if (testPair.isEmpty()) {
            showAlert("Backtest unavailable", "No exchange symbol is available for the built-in test run.");
            return;
        }

        Timeframe selectedTimeframe = timeframeCombo.getValue();
        if (selectedTimeframe == null) {
            showAlert("Backtest unavailable", "Please select a timeframe before testing.");
            return;
        }

        TradePair pair = testPair.get();
        CompletableFuture
                .supplyAsync(() -> runRealBacktest(definition, pair, selectedTimeframe))
                .whenComplete((report, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        log.error("Strategy backtest failed for {}", definition.getName(), throwable);
                        showAlert("Backtest Failed", throwable.getMessage());
                        return;
                    }

                    if (report == null) {
                        showAlert("Backtest Failed", "No backtest result was produced.");
                        return;
                    }

                    showAlert("Backtest Complete",
                            summarizeBacktest(report));
                    log.info("Backtest completed for '{}' on {}/{} score={} return={}%% trades={}",
                            report.getStrategyName(),
                            report.getSymbol(),
                            report.getTimeframe().getCode(),
                            report.getScore(),
                            report.getTotalReturn(),
                            report.getTotalTrades());
                }));
    }

    private void resetForm() {
        strategyNameField.clear();
        categoryCombo.setValue(null);
        timeframeCombo.setValue(null);
        categoryFilterCombo.getSelectionModel().selectFirst();
        strategyDescriptionArea.clear();
        parameterRows.clear();
        updatePreview();
        log.info("Strategy builder form reset");
    }

    private StrategyDefinition currentStrategyDefinition() {
        String name = strategyNameField.getText().trim();
        return StrategyDefinition.builder()
                .name(name)
                .baseName(name)
                .parameters(buildParameters())
                .build();
    }

    private void persistStrategyDefinition(StrategyDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return;
        }

        USER_DEFINED_STRATEGIES.put(definition.getName().toLowerCase(Locale.ROOT), definition);
        StrategyRegistry.getInstance().registerDefinition(definition);
    }

    private Optional<TradePair> resolveTestPairPair() {
        if (systemCore == null || systemCore.getExchange() == null) {
            return Optional.empty();
        }

        List<TradePair> tradePairs;
        try {
            tradePairs = systemCore.getExchange().getTradePairSymbol();
        } catch (Exception exception) {
            log.warn("Unable to resolve test symbol from exchange: {}", exception.getMessage());
            return Optional.empty();
        }

        if (tradePairs == null) {
            return Optional.empty();
        }

        return tradePairs.stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private StrategyPerformanceReport runRealBacktest(
            StrategyDefinition definition,
            TradePair pair,
            Timeframe timeframe) {
        HistoricalDataRepository repository = systemCore != null && systemCore.getHistoricalDataRepository() != null
                ? systemCore.getHistoricalDataRepository()
                : HistoricalDataRepositoryImpl.getInstance();

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = switch (timeframe) {
            case D1, W1, MN -> endTime.minusYears(2);
            case H4, H1 -> endTime.minusMonths(12);
            default -> endTime.minusMonths(6);
        };

        List<CandleData> candles = fetchHistoricalCandles(repository, pair, startTime, endTime, timeframe.getCode());
        if (candles.size() < 50) {
            throw new IllegalStateException("Not enough historical candles for backtest: " + candles.size());
        }

        StrategyBacktestRequest request = StrategyBacktestRequest.builder()
                .symbol(pair.toString('/'))
                .timeframe(timeframe)
                .strategyName(definition.getName())
                .strategyDefinition(definition)
                .candles(candles)
                .build();

        return new StrategyBacktestRunner().run(request);
    }

    private List<CandleData> fetchHistoricalCandles(
            HistoricalDataRepository repository,
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframeCode) {
        try {
            Optional<List<CandleData>> result = repository.getHistoricalData(pair, startTime, endTime, timeframeCode);
            return result.orElseGet(List::of);
        } catch (java.sql.SQLException exception) {
            log.warn("Failed to load historical candles for {}/{}: {}",
                pair == null ? "UNKNOWN" : pair.toString('/'),
                timeframeCode,
                exception.getMessage());
            return List.of();
        } catch (Exception exception) {
            log.warn("Failed to load historical candles for {}/{}: {}",
                    pair == null ? "UNKNOWN" : pair.toString('/'),
                    timeframeCode,
                    exception.getMessage());
            return List.of();
        }
    }

    private String summarizeBacktest(StrategyPerformanceReport report) {
        StringBuilder summary = new StringBuilder();
        summary.append("Strategy: ").append(report.getStrategyName()).append('\n');
        summary.append("Symbol: ").append(report.getSymbol()).append('\n');
        summary.append("Timeframe: ").append(report.getTimeframe().getCode()).append('\n');
        summary.append("Trades: ").append(report.getTotalTrades()).append('\n');
        summary.append(String.format(Locale.ROOT, "Return: %.2f%%%n", report.getTotalReturn()));
        summary.append(String.format(Locale.ROOT, "Win rate: %.2f%%%n", report.getWinRate() * 100.0));
        summary.append(String.format(Locale.ROOT, "Profit factor: %.2f%n", report.getProfitFactor()));
        summary.append(String.format(Locale.ROOT, "Drawdown: %.2f%%%n", report.getMaxDrawdown() * 100.0));
        summary.append(String.format(Locale.ROOT, "Score: %.2f%n", report.getScore()));
        summary.append("Tradable: ").append(report.isTradable() ? "Yes" : "No");

        if (!report.getWarnings().isEmpty()) {
            summary.append("\n\nWarnings:\n");
            report.getWarnings().stream().limit(5).forEach(w -> summary.append("- ").append(w).append('\n'));
        }

        return summary.toString().trim();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private VBox styledSection(String borderColor) {
        VBox v = new VBox(10);
        v.setPadding(new Insets(12));
        v.setStyle("-fx-background-color: #16213e; -fx-border-color: " + borderColor +
                "; -fx-border-width: 1; -fx-border-radius: 6;");
        return v;
    }

    private Label sectionHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        return l;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px;");
        return l;
    }

    private HBox createLabeledInput(String label, Control input) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #a0aec0; -fx-min-width: 130px;");
        HBox box = new HBox(8, lbl, input);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        return box;
    }

    private void styleInput(TextField tf) {
        tf.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #6b7280;");
    }

    private Button styledBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-padding: 10px 22px; -fx-font-size: 13px; -fx-background-color: " + color + "; -fx-text-fill: white;");
        return b;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // -----------------------------------------------------------------------
    // Functional interface for cell factory (avoids unchecked cast warning)
    // -----------------------------------------------------------------------
    @FunctionalInterface
    private interface Callback<T> {
        ListCell<T> create();
    }

    // -----------------------------------------------------------------------
    // Observable row model for the parameters table
    // -----------------------------------------------------------------------
    public static class StrategyParameterRow {
        private final SimpleStringProperty indicatorName;
        private final SimpleStringProperty parameterName;
        private final SimpleStringProperty value;

        public StrategyParameterRow(String indicatorName, String parameterName, String value) {
            this.indicatorName = new SimpleStringProperty(indicatorName);
            this.parameterName = new SimpleStringProperty(parameterName);
            this.value         = new SimpleStringProperty(value);
        }

        public SimpleStringProperty indicatorNameProperty() { return indicatorName; }
        public SimpleStringProperty parameterNameProperty() { return parameterName; }
        public SimpleStringProperty valueProperty()         { return value; }

        public String getIndicatorName() { return indicatorName.get(); }
        public String getParameterName() { return parameterName.get(); }
        public String getValue()         { return value.get(); }
        public void   setValue(String v) { value.set(v); }
    }

    /**
     * @deprecated Use {@link StrategyParameterRow} instead.
     * Kept for binary compatibility if anything still references the old record.
     */
    @Deprecated
    public record StrategyParameter(String name, String parameter, String value) {}
}
