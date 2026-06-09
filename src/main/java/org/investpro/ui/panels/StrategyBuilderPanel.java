package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiCostEstimator;
import org.investpro.ai.AiCreditAccount;
import org.investpro.ai.AiModelCatalog;
import org.investpro.ai.AiModelDefinition;
import org.investpro.ai.AiTradingDisclaimer;
import org.investpro.ai.strategy.AiStrategyGenerationRequest;
import org.investpro.ai.strategy.AiStrategyGenerationResult;
import org.investpro.ai.strategy.SafeAiStrategyGenerator;
import org.investpro.config.AppConfig;
import org.investpro.core.SystemCore;
import org.investpro.data.CandleData;
import org.investpro.enums.StrategyCategory;
import org.investpro.indicators.INDICATORS;
import org.investpro.indicators.INDICATORS.IndicatorCategory;
import org.investpro.indicators.IndicatorCatalog;
import org.investpro.indicators.metadata.IndicatorDefinition;

import org.investpro.i18n.LocalizationService;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.news.CryptoNewsIntelligence;
import org.investpro.news.NewsContext;
import org.investpro.persistence.repository.HistoricalDataRepository;
import org.investpro.persistence.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.auto.AutoStrategyLab;
import org.investpro.strategy.auto.AutoStrategyScheduler;
import org.investpro.strategy.auto.MarketRegime;
import org.investpro.strategy.auto.MarketRegimeDetector;
import org.investpro.strategy.auto.RiskProfile;
import org.investpro.strategy.auto.StrategyAssignmentDecision;
import org.investpro.strategy.auto.StrategyCandidate;
import org.investpro.strategy.auto.StrategyEvaluationResult;
import org.investpro.strategy.auto.StrategyGenerationContext;
import org.investpro.strategy.persistence.UserStrategyDefinitionStore;
import org.investpro.strategy.rules.CandlePattern;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.investpro.strategy.lab.StrategyBacktestRequest;
import org.investpro.strategy.lab.StrategyBacktestRunner;
import org.investpro.strategy.lab.StrategyPerformanceReport;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import org.investpro.utils.CandleDataSupplier;

import java.net.URL;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
    // Indicator → default parameter definitions [paramName, defaultValue]
    // -----------------------------------------------------------------------
    private static final Map<INDICATORS, List<String[]>> INDICATOR_DEFAULTS;

    static {
        Map<INDICATORS, List<String[]>> m = new LinkedHashMap<>();
        m.put(INDICATORS.RSI, defaults(p("period", "14"), p("oversold", "35.0"), p("overbought", "65.0")));
        m.put(INDICATORS.SMA, defaults(p("period", "20")));
        m.put(INDICATORS.EMA, defaults(p("period", "20")));
        m.put(INDICATORS.EMA_FAST, defaults(p("period", "12")));
        m.put(INDICATORS.EMA_SLOW, defaults(p("period", "26")));
        m.put(INDICATORS.WMA, defaults(p("period", "20")));
        m.put(INDICATORS.HMA, defaults(p("period", "20")));
        m.put(INDICATORS.DEMA, defaults(p("period", "20")));
        m.put(INDICATORS.TEMA, defaults(p("period", "20")));
        m.put(INDICATORS.VWMA, defaults(p("period", "20")));
        m.put(INDICATORS.KAMA, defaults(p("period", "10")));
        m.put(INDICATORS.ZLEMA, defaults(p("period", "20")));
        m.put(INDICATORS.MA_CROSSOVER, defaults(p("fastPeriod", "12"), p("slowPeriod", "26")));
        m.put(INDICATORS.MACD, defaults(p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9")));
        m.put(INDICATORS.MACD_LINE, defaults(p("fastPeriod", "12"), p("slowPeriod", "26")));
        m.put(INDICATORS.MACD_SIGNAL, defaults(p("signalPeriod", "9")));
        m.put(INDICATORS.MACD_HISTOGRAM,
                defaults(p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9")));
        m.put(INDICATORS.ATR, defaults(p("period", "14")));
        m.put(INDICATORS.ATR_PERCENT, defaults(p("period", "14")));
        m.put(INDICATORS.BOLLINGER_BANDS, defaults(p("period", "20"), p("stdDevMult", "2.0")));
        m.put(INDICATORS.BOLLINGER_UPPER, defaults(p("period", "20"), p("stdDevMult", "2.0")));
        m.put(INDICATORS.BOLLINGER_MIDDLE, defaults(p("period", "20")));
        m.put(INDICATORS.BOLLINGER_LOWER, defaults(p("period", "20"), p("stdDevMult", "2.0")));
        m.put(INDICATORS.BOLLINGER_WIDTH, defaults(p("period", "20"), p("stdDevMult", "2.0")));
        m.put(INDICATORS.BOLLINGER_PERCENT_B, defaults(p("period", "20"), p("stdDevMult", "2.0")));
        m.put(INDICATORS.KELTNER_CHANNEL, defaults(p("emaPeriod", "20"), p("atrPeriod", "14"), p("atrMult", "1.5")));
        m.put(INDICATORS.KELTNER_UPPER, defaults(p("emaPeriod", "20"), p("atrPeriod", "14"), p("atrMult", "1.5")));
        m.put(INDICATORS.KELTNER_LOWER, defaults(p("emaPeriod", "20"), p("atrPeriod", "14"), p("atrMult", "1.5")));
        m.put(INDICATORS.DONCHIAN_CHANNEL, defaults(p("period", "20")));
        m.put(INDICATORS.DONCHIAN_HIGH, defaults(p("period", "20")));
        m.put(INDICATORS.DONCHIAN_LOW, defaults(p("period", "20")));
        m.put(INDICATORS.STANDARD_DEVIATION, defaults(p("period", "20")));
        m.put(INDICATORS.HISTORICAL_VOLATILITY, defaults(p("period", "20")));
        m.put(INDICATORS.STOCHASTIC, defaults(p("kPeriod", "14"), p("dPeriod", "3"), p("smooth", "3")));
        m.put(INDICATORS.STOCHASTIC_K, defaults(p("kPeriod", "14"), p("smooth", "3")));
        m.put(INDICATORS.STOCHASTIC_D, defaults(p("dPeriod", "3")));
        m.put(INDICATORS.STOCH_RSI,
                defaults(p("rsiPeriod", "14"), p("stochPeriod", "14"), p("kSmooth", "3"), p("dSmooth", "3")));
        m.put(INDICATORS.CCI, defaults(p("period", "20")));
        m.put(INDICATORS.MOMENTUM, defaults(p("period", "10")));
        m.put(INDICATORS.ROC, defaults(p("period", "12")));
        m.put(INDICATORS.WILLIAMS_R, defaults(p("period", "14")));
        m.put(INDICATORS.TRIX, defaults(p("period", "15")));
        m.put(INDICATORS.ULTIMATE_OSCILLATOR, defaults(p("period1", "7"), p("period2", "14"), p("period3", "28")));
        m.put(INDICATORS.ADX, defaults(p("period", "14")));
        m.put(INDICATORS.PSAR, defaults(p("step", "0.02"), p("maxStep", "0.2")));
        m.put(INDICATORS.ICHIMOKU, defaults(p("tenkan", "9"), p("kijun", "26"), p("senkouB", "52")));
        m.put(INDICATORS.ICHIMOKU_TENKAN, defaults(p("period", "9")));
        m.put(INDICATORS.ICHIMOKU_KIJUN, defaults(p("period", "26")));
        m.put(INDICATORS.ICHIMOKU_SENKOU_A, defaults());
        m.put(INDICATORS.ICHIMOKU_SENKOU_B, defaults(p("period", "52")));
        m.put(INDICATORS.ICHIMOKU_CHIKOU, defaults(p("displacement", "26")));
        m.put(INDICATORS.VOLUME_SMA, defaults(p("period", "20")));
        m.put(INDICATORS.VOLUME_RATIO, defaults(p("period", "20")));
        m.put(INDICATORS.MFI, defaults(p("period", "14")));
        m.put(INDICATORS.CMF, defaults(p("period", "20")));
        m.put(INDICATORS.VWAP, defaults());
        m.put(INDICATORS.OBV, defaults());
        m.put(INDICATORS.ADL, defaults());
        m.put(INDICATORS.VOLUME_SPIKE, defaults(p("thresholdMult", "2.0"), p("period", "20")));
        INDICATOR_DEFAULTS = Collections.unmodifiableMap(m);
    }

    @Contract(value = "_, _ -> new", pure = true)
    private static String @NonNull [] p(String name, String def) {
        return new String[] { name, def };
    }

    @Contract(pure = true)
    private static @NonNull List<String[]> defaults(String[]... entries) {
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
    private TableView<StrategyRuleRow> rulesTable;
    private ObservableList<StrategyRuleRow> ruleRows;
    private Label descriptionCounterLabel;
    private Label paramsPreviewLabel;
    private TextField minBuySignalsField;
    private TextField minSellSignalsField;
    private Label minBuyOutOfLabel;
    private Label minSellOutOfLabel;
    private Label autoStrategyStatusLabel;
    private SystemCore systemCore;
    private final AutoStrategyLab autoStrategyLab = new AutoStrategyLab();
    private final AutoStrategyScheduler autoStrategyScheduler = new AutoStrategyScheduler();
    private final MarketRegimeDetector marketRegimeDetector = new MarketRegimeDetector();
    private List<StrategyCandidate> lastAutoCandidates = List.of();
    private List<StrategyEvaluationResult> lastAutoEvaluations = List.of();
    private StrategyGenerationContext lastAutoContext;

    public StrategyBuilderPanel(SystemCore systemCore) {
        setPadding(new Insets(16));
        setSpacing(12);
        getStyleClass().add("strategy-builder-panel");
        this.systemCore = systemCore;
        parameterRows = FXCollections.observableArrayList();
        ruleRows = FXCollections.observableArrayList();
        loadStylesheet();
        setupUI();
        LocalizationService.applyTranslations(this);
    }

    private void loadStylesheet() {
        URL stylesheet = StrategyBuilderPanel.class.getResource("/css/strategy-builder.css");
        if (stylesheet != null) {
            getStylesheets().add(stylesheet.toExternalForm());
        } else {
            log.warn("strategy-builder.css was not found on the classpath");
        }
    }

    // -----------------------------------------------------------------------
    // UI setup
    // -----------------------------------------------------------------------
    private void setupUI() {
        VBox header = createHeader();
        HBox body = createBody();

        getChildren().setAll(header, body);
        VBox.setVgrow(body, Priority.ALWAYS);
    }

    private @NonNull VBox createHeader() {
        Label breadcrumb = new Label("Bots > Strategy Builder > New Strategy");
        breadcrumb.getStyleClass().add("strategy-builder-breadcrumb");

        Label title = new Label("New Strategy");
        title.getStyleClass().add("strategy-builder-title");

        VBox header = new VBox(4, breadcrumb, title);
        header.getStyleClass().add("strategy-builder-header");
        return header;
    }

    private @NonNull HBox createBody() {
        VBox metadataCard = createMetadataCard();
        VBox workspaceCard = createWorkspaceCard();

        HBox body = new HBox(16, metadataCard, workspaceCard);
        body.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(workspaceCard, Priority.ALWAYS);
        return body;
    }

    private VBox createMetadataCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("strategy-meta-card");
        card.setPrefWidth(340);
        card.setMinWidth(320);
        card.setMaxWidth(360);

        strategyNameField = new TextField();
        strategyNameField.setPromptText("e.g., My Mean Reversion Bot");
        styleInput(strategyNameField);

        categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(StrategyCategory.values());
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        categoryCombo.getStyleClass().add("strategy-input");
        categoryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(StrategyCategory c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getDisplayName());
            }
        });
        categoryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(StrategyCategory c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? "Select category..." : c.getDisplayName());
            }
        });

        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().addAll(Timeframe.values());
        timeframeCombo.setMaxWidth(Double.MAX_VALUE);
        timeframeCombo.getStyleClass().add("strategy-input");

        strategyDescriptionArea = new TextArea();
        strategyDescriptionArea.setPromptText("Describe entry, exit, and risk logic.");
        strategyDescriptionArea.setWrapText(true);
        strategyDescriptionArea.setPrefRowCount(7);
        strategyDescriptionArea.getStyleClass().add("strategy-text-area");

        descriptionCounterLabel = mutedLabel("0 / 500");
        strategyDescriptionArea.textProperty().addListener((obs, oldValue, newValue) -> {
            int length = newValue == null ? 0 : newValue.length();
            descriptionCounterLabel.setText(length + " / 500");
        });

        card.getChildren().addAll(sectionHeader("Strategy Details"),
                createStackedInput("Strategy Name *", strategyNameField),
                createStackedInput("Category", categoryCombo),
                createStackedInput("Primary Timeframe", timeframeCombo),
                createStackedInput("Description", strategyDescriptionArea),
                descriptionCounterLabel,
                createStrategyImagePlaceholder());
        return card;
    }

    private @NonNull VBox createStrategyImagePlaceholder() {
        Region imageMark = new Region();
        imageMark.getStyleClass().add("strategy-image-mark");

        Label imageLabel = new Label("Strategy Image");
        imageLabel.getStyleClass().add("strategy-card-subtitle");

        Button editButton = secondaryButton("Edit");
        editButton.setOnAction(e -> showAlert("Strategy Image", "Image selection is not connected yet."));

        VBox placeholder = new VBox(10, imageMark, imageLabel, editButton);
        placeholder.getStyleClass().add("strategy-image-placeholder");
        placeholder.setAlignment(Pos.CENTER);
        return placeholder;
    }

    private VBox createWorkspaceCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("strategy-workspace-card");

        rulesTable = buildRulesTable();
        rulesTable.setItems(ruleRows);
        VBox.setVgrow(rulesTable, Priority.ALWAYS);

        Button addRuleButton = secondaryButton("+ Add Rule");
        addRuleButton.setOnAction(e -> showAddRuleDialog());

        HBox thresholds = new HBox(14,
                createSignalThresholdCard("Minimum Buy Signals", true),
                createSignalThresholdCard("Minimum Sell Signals", false));
        thresholds.setAlignment(Pos.CENTER_LEFT);

        autoStrategyStatusLabel = mutedLabel("Auto Strategy Lab: idle");
        autoStrategyStatusLabel.getStyleClass().add("strategy-auto-status");

        card.getChildren().addAll(createWorkspaceToolbar(), rulesTable, addRuleButton, thresholds,
                autoStrategyStatusLabel, createParamsPreviewSection());
        return card;
    }

    private @NonNull HBox createWorkspaceToolbar() {
        Button saveButton = primaryButton("Save Strategy");
        saveButton.setOnAction(e -> saveStrategy());

        Button indicatorsButton = secondaryButton("Indicators");
        indicatorsButton.setOnAction(e -> showAddRuleDialog());

        Button candlePatternsButton = secondaryButton("Candle Patterns");
        candlePatternsButton.setOnAction(e -> showCandlePatternDialog());

        Button aiAssistantButton = secondaryButton("AI Assistant");
        aiAssistantButton.setOnAction(e -> showAiAssistantDialog());

        Button createAiStrategyButton = secondaryButton("Create Strategy with AI BETA");
        createAiStrategyButton.setOnAction(e -> showAiStrategyGenerationDialog());

        Button testButton = secondaryButton("Test Strategy");
        testButton.setOnAction(e -> testStrategy());

        Button codeButton = secondaryButton("Code");
        codeButton.setOnAction(e -> showAlert("Strategy Code", buildCodePreview()));

        ToggleButton autoImproveToggle = new ToggleButton("Auto Improve");
        autoImproveToggle.getStyleClass().add("strategy-secondary-button");
        autoImproveToggle.setSelected(false);
        autoImproveToggle.setDisable(!AppConfig.getBoolean("autoStrategy.enabled", true));
        autoImproveToggle.setOnAction(e -> {
            if (autoImproveToggle.isSelected()) {
                startAutoImproveScheduler();
            } else {
                autoStrategyScheduler.stop();
                setAutoStrategyStatus("Scheduled auto improvement stopped.");
            }
        });

        Button generateCandidatesButton = secondaryButton("Generate Candidates");
        generateCandidatesButton.setOnAction(e -> generateAutoStrategyCandidates());

        Button evaluateCandidatesButton = secondaryButton("Evaluate Candidates");
        evaluateCandidatesButton.setOnAction(e -> evaluateAutoStrategyCandidates());

        Button assignBestButton = secondaryButton("Assign Best");
        assignBestButton.setOnAction(e -> assignBestAutoStrategy());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button askAiButton = secondaryButton("Ask AI");
        askAiButton.setOnAction(e -> showAiAssistantDialog());

        HBox toolbar = new HBox(8,
                saveButton,
                indicatorsButton,
                candlePatternsButton,
                aiAssistantButton,
                createAiStrategyButton,
                testButton,
                codeButton,
                autoImproveToggle,
                generateCandidatesButton,
                evaluateCandidatesButton,
                assignBestButton,
                spacer,
                askAiButton);
        toolbar.getStyleClass().add("strategy-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private TableView<StrategyRuleRow> buildRulesTable() {
        TableView<StrategyRuleRow> table = new TableView<>();
        table.setEditable(true);
        table.getStyleClass().add("strategy-rules-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Add an indicator rule to begin."));

        TableColumn<StrategyRuleRow, Boolean> selectedCol = new TableColumn<>("");
        selectedCol.setCellValueFactory(c -> c.getValue().selectedProperty());
        selectedCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedCol));
        selectedCol.setEditable(true);
        selectedCol.setPrefWidth(52);
        selectedCol.setResizable(false);

        TableColumn<StrategyRuleRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> c.getValue().signalTypeProperty());
        typeCol.setPrefWidth(120);
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(item);
                if ("BUY".equalsIgnoreCase(item)) {
                    pill.getStyleClass().add("buy-pill");
                } else if ("SELL".equalsIgnoreCase(item)) {
                    pill.getStyleClass().add("sell-pill");
                } else {
                    pill.getStyleClass().add("neutral-pill");
                }
                setGraphic(pill);
            }
        });

        TableColumn<StrategyRuleRow, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(c -> c.getValue().sourceNameProperty());
        sourceCol.setPrefWidth(150);
        sourceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label source = new Label(item);
                source.getStyleClass().add("strategy-rule-source");
                setGraphic(source);
            }
        });

        TableColumn<StrategyRuleRow, String> nameCol = new TableColumn<>("Indicator");
        nameCol.setCellValueFactory(c -> c.getValue().displayNameProperty());
        nameCol.setPrefWidth(280);

        TableColumn<StrategyRuleRow, String> candleCol = new TableColumn<>("Candle Size");
        candleCol.setCellValueFactory(c -> c.getValue().candleSizeProperty());
        candleCol.setPrefWidth(150);

        TableColumn<StrategyRuleRow, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(150);
        actionCol.setResizable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button settingsBtn = new Button("Settings");
            private final Button removeBtn = new Button("Remove");
            private final HBox actions = new HBox(6, settingsBtn, removeBtn);
            {
                actions.setAlignment(Pos.CENTER_LEFT);
                settingsBtn.getStyleClass().add("strategy-rule-action-button");
                removeBtn.getStyleClass().add("strategy-rule-action-button");
                settingsBtn.setTooltip(new Tooltip("Edit rule parameters"));
                settingsBtn.setOnAction(e -> {
                    StrategyRuleRow row = getTableView().getItems().get(getIndex());
                    showParameterEditor(row);
                });
                removeBtn.setOnAction(e -> {
                    StrategyRuleRow row = getTableView().getItems().get(getIndex());
                    ruleRows.remove(row);
                    syncParameterRowsFromRules();
                    updateSignalThresholdLabels();
                    updatePreview();
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : actions);
            }
        });

        table.getColumns().addAll(selectedCol, typeCol, sourceCol, nameCol, candleCol, actionCol);
        return table;
    }

    private VBox createSignalThresholdCard(String title, boolean buy) {
        Label titleLabel = sectionHeader(title);
        TextField valueField = new TextField("1");
        valueField.getStyleClass().add("strategy-signal-input");
        valueField.setPrefColumnCount(2);

        Label outOfLabel = mutedLabel("out of 0");
        Button minus = new Button("-");
        Button plus = new Button("+");
        minus.getStyleClass().add("strategy-stepper-button");
        plus.getStyleClass().add("strategy-stepper-button");
        minus.setOnAction(e -> adjustSignalThreshold(valueField, -1));
        plus.setOnAction(e -> adjustSignalThreshold(valueField, 1));

        HBox controls = new HBox(8, minus, valueField, plus, outOfLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, titleLabel, controls);
        card.getStyleClass().add("strategy-signal-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        if (buy) {
            minBuySignalsField = valueField;
            minBuyOutOfLabel = outOfLabel;
        } else {
            minSellSignalsField = valueField;
            minSellOutOfLabel = outOfLabel;
        }

        valueField.textProperty().addListener((obs, oldValue, newValue) -> updateSignalThresholdLabels());
        return card;
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
            @Override
            protected void updateItem(StrategyCategory c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getDisplayName());
            }
        });
        categoryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(StrategyCategory c, boolean empty) {
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
            @Override
            protected void updateItem(IndicatorCategory ic, boolean empty) {
                super.updateItem(ic, empty);
                setText(empty || ic == null ? "All categories" : ic.name());
            }
        });
        categoryFilterCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(IndicatorCategory ic, boolean empty) {
                super.updateItem(ic, empty);
                setText(empty || ic == null ? "All categories" : ic.name());
            }
        });

        // Indicator picker — populated dynamically based on filter
        indicatorCombo = new ComboBox<>();
        indicatorCombo.setPrefHeight(32);
        indicatorCombo.setMaxWidth(Double.MAX_VALUE);
        Callback<INDICATORS> indicatorCellFactory = () -> new ListCell<>() {
            @Override
            protected void updateItem(INDICATORS ind, boolean empty) {
                super.updateItem(ind, empty);
                if (empty || ind == null) {
                    setText(null);
                    return;
                }
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
            if (sel != null)
                indicatorCombo.setTooltip(new Tooltip(sel.getDescription()));
        });

        Button addBtn = new Button("+ Add");
        addBtn.setStyle(
                "-fx-padding: 6px 18px; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold;");
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
                styledLabel(),
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
        if (prev != null && filtered.contains(prev))
            indicatorCombo.setValue(prev);
        else if (!filtered.isEmpty())
            indicatorCombo.getSelectionModel().selectFirst();
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
                removeBtn.setStyle(
                        "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 2px 8px; -fx-font-size: 11px;");
                removeBtn.setOnAction(e -> {
                    StrategyParameterRow row = getTableView().getItems().get(getIndex());
                    parameterRows.remove(row);
                    updatePreview();
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });

        table.getColumns().addAll(indCol, paramCol, valueCol, actionCol);
        return table;
    }

    private @NonNull VBox createStrategyLogicSection() {
        VBox section = styledSection("#f59e0b");
        section.getChildren().add(sectionHeader("Strategy Description & Rules"));

        strategyDescriptionArea = new TextArea();
        strategyDescriptionArea.setPromptText(
                """
                        Describe your strategy's entry/exit logic here.
                        e.g.:
                          Entry: RSI < oversold AND price above EMA(200)
                          Exit:  RSI > 60 OR ATR stop hit
                          Risk:  1% account risk per trade""");
        strategyDescriptionArea.setWrapText(true);
        strategyDescriptionArea.setPrefHeight(130);
        strategyDescriptionArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        section.getChildren().add(strategyDescriptionArea);
        VBox.setVgrow(strategyDescriptionArea, Priority.ALWAYS);
        return section;
    }

    private VBox createParamsPreviewSection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("strategy-preview-card");
        section.getChildren().add(sectionHeader("Assembled StrategyParameters Preview"));
        paramsPreviewLabel = new Label("(no indicators added yet)");
        paramsPreviewLabel.getStyleClass().add("strategy-preview-label");
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
        showAddRuleDialog();
    }

    private void showAddRuleDialog() {
        Dialog<StrategyRuleRow> dialog = new Dialog<>();
        dialog.setTitle("Add Strategy Rule");
        dialog.setHeaderText("Choose an indicator signal rule");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField searchField = new TextField();
        searchField.setPromptText("Search indicators...");
        searchField.getStyleClass().add("strategy-input");

        ComboBox<SignalType> signalCombo = new ComboBox<>(FXCollections.observableArrayList(
                SignalType.BUY,
                SignalType.SELL,
                SignalType.NEUTRAL));
        signalCombo.getSelectionModel().selectFirst();
        signalCombo.setMaxWidth(Double.MAX_VALUE);
        signalCombo.getStyleClass().add("strategy-input");

        ComboBox<IndicatorCategory> ruleCategoryCombo = new ComboBox<>();
        ruleCategoryCombo.getItems().add(null);
        ruleCategoryCombo.getItems().addAll(IndicatorCategory.values());
        ruleCategoryCombo.setMaxWidth(Double.MAX_VALUE);
        ruleCategoryCombo.getStyleClass().add("strategy-input");
        ruleCategoryCombo.setButtonCell(indicatorCategoryCell("All categories"));
        ruleCategoryCombo.setCellFactory(lv -> indicatorCategoryCell("All categories"));

        ObservableList<INDICATORS> indicators = FXCollections.observableArrayList(
                IndicatorDefinition.all().stream()
                        .map(IndicatorDefinition::indicator)
                        .toList());
        FilteredList<INDICATORS> filteredIndicators = new FilteredList<>(indicators, indicator -> true);

        Runnable applyFilter = () -> {
            IndicatorCategory selectedCategory = ruleCategoryCombo.getValue();
            String query = searchField.getText() == null
                    ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            filteredIndicators.setPredicate(indicator -> {
                IndicatorDefinition definition = IndicatorDefinition.get(indicator);
                boolean categoryMatches = selectedCategory == null || definition.category() == selectedCategory;
                boolean queryMatches = query.isBlank()
                        || definition.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || indicator.name().toLowerCase(Locale.ROOT).contains(query)
                        || definition.description().toLowerCase(Locale.ROOT).contains(query);
                return categoryMatches && queryMatches;
            });
        };

        ListView<INDICATORS> indicatorList = new ListView<>(filteredIndicators);
        indicatorList.getStyleClass().add("candle-pattern-list");
        indicatorList.setPrefHeight(300);
        indicatorList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(INDICATORS indicator, boolean empty) {
                super.updateItem(indicator, empty);

                if (empty || indicator == null) {
                    setGraphic(null);
                    return;
                }

                IndicatorDefinition definition = IndicatorDefinition.get(indicator);

                Label name = new Label(definition.displayName());
                HBox.setHgrow(name, Priority.ALWAYS);

                Label category = new Label(definition.category().name());
                category.getStyleClass().add("strategy-rule-source");

                HBox row = new HBox(10, name, category);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        if (!filteredIndicators.isEmpty()) {
            indicatorList.getSelectionModel().selectFirst();
        }

        ComboBox<Timeframe> candleCombo = new ComboBox<>();
        candleCombo.getItems().addAll(Timeframe.values());
        candleCombo.setMaxWidth(Double.MAX_VALUE);
        candleCombo.getStyleClass().add("strategy-input");
        if (timeframeCombo != null && timeframeCombo.getValue() != null) {
            candleCombo.setValue(timeframeCombo.getValue());
        } else if (!candleCombo.getItems().isEmpty()) {
            candleCombo.getSelectionModel().selectFirst();
        }

        VBox parameterBox = new VBox(8);
        parameterBox.getStyleClass().add("strategy-preview-card");
        Map<String, TextField> parameterEditors = new LinkedHashMap<>();

        Runnable refreshParameters = () -> {
            parameterBox.getChildren().clear();
            parameterEditors.clear();

            INDICATORS selectedIndicator = indicatorList.getSelectionModel().getSelectedItem();

            if (selectedIndicator == null) {
                parameterBox.getChildren().add(mutedLabel("Select an indicator to edit parameters."));
                return;
            }

            IndicatorDefinition definition = IndicatorCatalog.get(selectedIndicator);

            Label description = mutedLabel(definition.description());
            description.setWrapText(true);
            parameterBox.getChildren().add(description);

            if (definition.parameters().isEmpty()) {
                parameterBox.getChildren().add(mutedLabel("No configurable parameters."));
                return;
            }

            for (var parameter : definition.parameters()) {
                TextField field = new TextField(parameter.defaultValue());
                field.getStyleClass().add("strategy-input");

                parameterEditors.put(parameter.name(), field);
                parameterBox.getChildren().add(createStackedInput(parameter.displayName(), field));
            }
        };
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());
        ruleCategoryCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> applyFilter.run());
        indicatorList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> refreshParameters.run());
        indicatorList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && indicatorList.getSelectionModel().getSelectedItem() != null) {
                Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
                addButton.fire();
            }
        });
        applyFilter.run();
        refreshParameters.run();

        HBox chooserRow = new HBox(12,
                createStackedInput("Signal", signalCombo),
                createStackedInput("Category", ruleCategoryCombo),
                createStackedInput("Candle Size", candleCombo));
        chooserRow.setAlignment(Pos.CENTER_LEFT);
        chooserRow.getChildren().forEach(child -> HBox.setHgrow(child, Priority.ALWAYS));

        VBox content = new VBox(12,
                searchField,
                chooserRow,
                indicatorList,
                parameterBox);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            INDICATORS selectedIndicator = indicatorList.getSelectionModel().getSelectedItem();
            Timeframe selectedTimeframe = candleCombo.getValue();
            if (selectedIndicator == null || selectedTimeframe == null) {
                return null;
            }
            StrategyRuleRow row = new StrategyRuleRow(
                    StrategyRuleSource.INDICATOR,
                    signalCombo.getValue(),
                    selectedIndicator,
                    null,
                    selectedTimeframe);
            row.getParameters().clear();
            parameterEditors.forEach((name, field) -> row.getParameters().put(name, field.getText()));
            return row;
        });

        dialog.showAndWait().ifPresent(row -> {
            ruleRows.add(row);
            syncParameterRowsFromRules();
            updateSignalThresholdLabels();
            updatePreview();
            log.info("Added {} rule for {} on {}", row.getSignalType(), row.getIndicatorName(), row.getCandleSize());
        });
    }

    private void showCandlePatternDialog() {
        Dialog<StrategyRuleRow> dialog = new Dialog<>();
        dialog.setTitle("Select a Candle Pattern");
        dialog.setHeaderText("Select a Candle Pattern");
        dialog.getDialogPane().getStyleClass().add("candle-pattern-dialog");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField searchField = new TextField();
        searchField.setPromptText("Search for candle patterns...");
        searchField.getStyleClass().add("strategy-input");

        ObservableList<CandlePattern> patterns = FXCollections.observableArrayList(CandlePattern.values());
        FilteredList<CandlePattern> filteredPatterns = new FilteredList<>(patterns, pattern -> true);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filteredPatterns.setPredicate(
                    pattern -> query.isEmpty() || pattern.getDisplayName().toLowerCase(Locale.ROOT).contains(query));
        });

        ListView<CandlePattern> patternList = new ListView<>(filteredPatterns);
        patternList.getStyleClass().add("candle-pattern-list");
        patternList.setPrefHeight(420);
        patternList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CandlePattern pattern, boolean empty) {
                super.updateItem(pattern, empty);
                if (empty || pattern == null) {
                    setGraphic(null);
                    return;
                }
                Label name = new Label(pattern.getDisplayName());
                HBox.setHgrow(name, Priority.ALWAYS);
                Label signal = new Label(pattern.getDefaultSignal().name());
                signal.getStyleClass().add(pattern.getDefaultSignal() == SignalType.BUY ? "buy-pill" : "sell-pill");
                HBox row = new HBox(10, name, signal);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        if (!filteredPatterns.isEmpty()) {
            patternList.getSelectionModel().selectFirst();
        }
        patternList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && patternList.getSelectionModel().getSelectedItem() != null) {
                Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
                addButton.fire();
            }
        });

        ComboBox<Timeframe> candleCombo = new ComboBox<>();
        candleCombo.getItems().addAll(Timeframe.values());
        candleCombo.setMaxWidth(Double.MAX_VALUE);
        candleCombo.getStyleClass().add("strategy-input");
        if (timeframeCombo != null && timeframeCombo.getValue() != null) {
            candleCombo.setValue(timeframeCombo.getValue());
        } else if (!candleCombo.getItems().isEmpty()) {
            candleCombo.getSelectionModel().selectFirst();
        }

        VBox content = new VBox(12,
                searchField,
                patternList,
                createStackedInput("Timeframe", candleCombo));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            CandlePattern selectedPattern = patternList.getSelectionModel().getSelectedItem();
            Timeframe selectedTimeframe = candleCombo.getValue();
            if (selectedPattern == null || selectedTimeframe == null) {
                return null;
            }
            return new StrategyRuleRow(selectedPattern, selectedTimeframe);
        });

        dialog.showAndWait().ifPresent(row -> {
            ruleRows.add(row);
            syncParameterRowsFromRules();
            updateSignalThresholdLabels();
            updatePreview();
            log.info("Added candle pattern rule {} on {}", row.getDisplayName(), row.getCandleSize());
        });
    }

    private void showParameterEditor(StrategyRuleRow row) {
        if (row == null) {
            return;
        }

        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Parameters");
        dialog.setHeaderText(row.getIndicatorName() + " | " + row.getSignalType() + " | " + row.getCandleSize());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        Map<String, TextField> editors = new LinkedHashMap<>();
        int line = 0;
        if (row.getParameters().isEmpty()) {
            grid.add(new Label("This indicator has no editable default parameters."), 0, line);
        } else {
            for (Map.Entry<String, String> entry : row.getParameters().entrySet()) {
                TextField editor = new TextField(entry.getValue());
                editor.getStyleClass().add("strategy-input");
                editors.put(entry.getKey(), editor);
                grid.add(new Label(entry.getKey()), 0, line);
                grid.add(editor, 1, line);
                GridPane.setHgrow(editor, Priority.ALWAYS);
                line++;
            }
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Map<String, String> updated = new LinkedHashMap<>();
            editors.forEach((name, field) -> updated.put(name, field.getText() == null ? "" : field.getText().trim()));
            return updated;
        });

        dialog.showAndWait().ifPresent(updated -> {
            row.getParameters().clear();
            row.getParameters().putAll(updated);
            syncParameterRowsFromRules();
            updatePreview();
        });
    }

    private void showAiAssistantDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("InvestPro AI Assistant");
        dialog.setHeaderText("Ask about this strategy or request a rule change");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea conversation = new TextArea();
        conversation.setEditable(false);
        conversation.setWrapText(true);
        conversation.setPrefRowCount(14);
        conversation.getStyleClass().add("strategy-text-area");
        conversation.setText("AI: " + buildAiWelcomeMessage() + "\n");

        TextArea prompt = new TextArea();
        prompt.setPromptText(
                "Ask AI to review, improve, explain, add RSI, add Hammer, balance signals, or test the strategy...");
        prompt.setWrapText(true);
        prompt.setPrefRowCount(3);
        prompt.getStyleClass().add("strategy-text-area");

        Button askButton = primaryButton("Ask");
        Button addRsiButton = secondaryButton("Add RSI");
        Button addHammerButton = secondaryButton("Add Hammer");
        Button testButton = secondaryButton("Test Now");

        askButton.setOnAction(event -> {
            String question = prompt.getText() == null ? "" : prompt.getText().trim();
            if (question.isBlank()) {
                return;
            }
            conversation.appendText("\nUser: " + question + "\n");
            prompt.clear();
            askButton.setDisable(true);
            CompletableFuture
                    .supplyAsync(() -> answerAiAssistantQuestion(question))
                    .whenComplete((answer, throwable) -> Platform.runLater(() -> {
                        if (throwable != null) {
                            conversation.appendText("AI: I could not process that request: "
                                    + throwable.getMessage() + "\n");
                        } else {
                            conversation.appendText("AI: " + answer + "\n");
                        }
                        askButton.setDisable(false);
                    }));
        });

        addRsiButton.setOnAction(event -> {
            addIndicatorRule(SignalType.BUY, INDICATORS.RSI, selectedOrDefaultTimeframe());
            conversation.appendText("\nAI: Added a BUY RSI rule using the current timeframe.\n");
        });
        addHammerButton.setOnAction(event -> {
            addCandlePatternRule(CandlePattern.HAMMER, selectedOrDefaultTimeframe());
            conversation.appendText("\nAI: Added a Hammer candle-pattern BUY rule using the current timeframe.\n");
        });
        testButton.setOnAction(event -> testStrategy());

        HBox actions = new HBox(8, askButton, addRsiButton, addHammerButton, testButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, conversation, prompt, actions);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }

    private void showAiStrategyGenerationDialog() {
        Dialog<AiStrategyGenerationRequest> dialog = new Dialog<>();
        dialog.setTitle("Create Strategy with AI BETA");
        dialog.setHeaderText("AI drafts are for review, validation, and backtesting only");
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        boolean aiEnabled = AppConfig.getBoolean("ai.enabled", false);
        AiCreditAccount creditAccount = new AiCreditAccount(BigDecimal.ZERO, new BigDecimal("25.0"));
        ComboBox<AiModelDefinition> modelCombo = new ComboBox<>();
        modelCombo.getItems().addAll(AiModelCatalog.modelsForFeature(org.investpro.ai.AiFeature.STRATEGY_DESIGNER));
        modelCombo.setValue(AiModelCatalog.defaultModel());
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        modelCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(AiModelDefinition item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName() + (item.free() ? " (Free)" : ""));
            }
        });
        modelCombo.setButtonCell(modelCombo.getCellFactory().call(null));

        Label disabledLabel = mutedLabel(aiEnabled
                ? "AI generation is enabled. Generated strategies must still be reviewed and tested."
                : "AI strategy generation is disabled. InvestPro can still generate rule-based strategies.");
        disabledLabel.setWrapText(true);

        Label modelDescription = mutedLabel("");
        modelDescription.setWrapText(true);
        Label creditLabel = mutedLabel("");
        Label costLabel = mutedLabel("");

        TextArea prompt = new TextArea();
        prompt.setPromptText("Describe the strategy idea, market, risk style, entries, exits, and indicators...");
        prompt.setWrapText(true);
        prompt.setPrefRowCount(8);
        prompt.getStyleClass().add("strategy-text-area");

        CheckBox disclaimer = new CheckBox(AiTradingDisclaimer.DISCLAIMER_TEXT);
        disclaimer.setWrapText(true);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        Runnable refresh = () -> {
            AiModelDefinition model = modelCombo.getValue();
            BigDecimal estimatedCost = AiCostEstimator.estimateTotalCredits(model, prompt.getText());
            modelDescription.setText(model == null ? "No model selected." : model.description());
            creditLabel
                    .setText("Credits: free " + creditAccount.freeCredits() + " | paid " + creditAccount.paidCredits());
            costLabel.setText("Estimated cost: " + estimatedCost + " credits");
            createButton.setDisable(!aiEnabled
                    || model == null
                    || prompt.getText() == null
                    || prompt.getText().trim().isBlank()
                    || !disclaimer.isSelected()
                    || !creditAccount.hasEnoughCredits(estimatedCost));
        };
        modelCombo.valueProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        prompt.textProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        disclaimer.selectedProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();

        VBox content = new VBox(12,
                disabledLabel,
                createStackedInput("Model", modelCombo),
                modelDescription,
                new HBox(12, creditLabel, costLabel),
                createStackedInput("Strategy Prompt", prompt),
                disclaimer);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> {
            if (button != createButtonType) {
                return null;
            }
            return new AiStrategyGenerationRequest(
                    modelCombo.getValue(),
                    prompt.getText(),
                    resolveTestPairPair(),
                    Optional.ofNullable(timeframeCombo.getValue()),
                    false,
                    disclaimer.isSelected());
        });

        dialog.showAndWait().ifPresent(request -> {
            setAutoStrategyStatus("AI strategy generation started...");
            CompletableFuture
                    .supplyAsync(() -> new SafeAiStrategyGenerator().generate(request))
                    .whenComplete(
                            (result, throwable) -> Platform.runLater(() -> handleAiStrategyResult(result, throwable)));
        });
    }

    private void handleAiStrategyResult(AiStrategyGenerationResult result, Throwable throwable) {
        if (throwable != null) {
            setAutoStrategyStatus("AI generation failed: " + throwable.getMessage());
            showAlert("AI Strategy", throwable.getMessage());
            return;
        }
        if (result == null || !result.success() || result.strategyDefinition() == null) {
            String message = result == null ? "No AI result was produced." : String.join("\n", result.errors());
            setAutoStrategyStatus("AI generation rejected.");
            showAlert("AI Strategy", message);
            return;
        }
        loadStrategyDefinitionIntoBuilder(result.strategyDefinition());
        setAutoStrategyStatus("AI draft loaded for review: " + result.strategyDefinition().getName());
    }

    private void generateAutoStrategyCandidates() {
        Timeframe timeframe = selectedOrDefaultTimeframe();
        setAutoStrategyStatus("Generating Auto Strategy Lab candidates...");
        CompletableFuture
                .supplyAsync(() -> {
                    StrategyGenerationContext context = createAutoStrategyContext(timeframe, false);
                    return new AbstractMap.SimpleEntry<>(context, autoStrategyLab.generateCandidates(context).join());
                })
                .whenComplete((entry, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        setAutoStrategyStatus("Candidate generation failed: " + throwable.getMessage());
                        return;
                    }
                    lastAutoContext = entry.getKey();
                    lastAutoCandidates = entry.getValue();
                    lastAutoEvaluations = List.of();
                    setAutoStrategyStatus("Generated " + lastAutoCandidates.size()
                            + " candidate(s). Best template score="
                            + lastAutoCandidates.stream().mapToDouble(StrategyCandidate::generationScore).max()
                                    .orElse(0.0));
                }));
    }

    private void evaluateAutoStrategyCandidates() {
        if (lastAutoCandidates == null || lastAutoCandidates.isEmpty()) {
            generateAutoStrategyCandidates();
            setAutoStrategyStatus("Generate candidates first, then press Evaluate Candidates.");
            return;
        }
        Timeframe timeframe = selectedOrDefaultTimeframe();
        setAutoStrategyStatus("Fetching candles and evaluating candidates...");
        CompletableFuture
                .supplyAsync(() -> {
                    StrategyGenerationContext context = createAutoStrategyContext(timeframe, true);
                    return new AbstractMap.SimpleEntry<>(context,
                            autoStrategyLab.evaluateCandidates(lastAutoCandidates, context).join());
                })
                .whenComplete((entry, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        setAutoStrategyStatus("Evaluation failed: " + throwable.getMessage());
                        return;
                    }
                    lastAutoContext = entry.getKey();
                    lastAutoEvaluations = entry.getValue();
                    Optional<StrategyEvaluationResult> best = lastAutoEvaluations.stream()
                            .max(Comparator.comparingDouble(StrategyEvaluationResult::score));
                    setAutoStrategyStatus("Evaluated " + lastAutoEvaluations.size()
                            + " candidate(s). Best="
                            + best.map(result -> result.candidate().strategyDefinition().getName()).orElse("none")
                            + " score="
                            + best.map(result -> String.format(Locale.ROOT, "%.2f", result.score())).orElse("0.00"));
                }));
    }

    private void assignBestAutoStrategy() {
        if (lastAutoEvaluations == null || lastAutoEvaluations.isEmpty()) {
            setAutoStrategyStatus("No evaluated candidates to assign.");
            return;
        }
        StrategyAssignmentDecision decision = autoStrategyLab.assignBest(
                lastAutoEvaluations,
                lastAutoContext == null ? null
                        : org.investpro.strategy.StrategySelectionService.getInstance()
                                .getCurrentAssignment(lastAutoContext.symbol(), lastAutoContext.timeframe()),
                lastAutoContext,
                true);
        setAutoStrategyStatus("Decision: " + decision.reason());
        if (decision.assigned()) {
            showAlert("Auto Strategy Lab", "Assigned " + decision.strategyName() + "\n" + decision.reason());
        } else {
            showAlert("Auto Strategy Lab", decision.reason() + "\n" + String.join("\n", decision.warnings()));
        }
    }

    private void startAutoImproveScheduler() {
        setAutoStrategyStatus("Scheduled auto improvement enabled.");
        autoStrategyScheduler.startAutoImprovement(
                autoStrategyLab,
                () -> List.of(createAutoStrategyContext(selectedOrDefaultTimeframe(), true)),
                context -> org.investpro.strategy.StrategySelectionService.getInstance()
                        .getCurrentAssignment(context.symbol(), context.timeframe()),
                context -> {
                    if (strategyNameField == null
                            || strategyNameField.getText() == null
                            || strategyNameField.getText().trim().isBlank()
                            || ruleRows == null
                            || ruleRows.isEmpty()) {
                        return null;
                    }
                    return currentStrategyDefinition();
                });
    }

    private StrategyGenerationContext createAutoStrategyContext(Timeframe timeframe, boolean includeCandles) {
        Optional<TradePair> pair = resolveTestPairPair();
        String symbol = pair.map(value -> value.toString('/')).orElse("UNKNOWN");
        List<CandleData> candles = includeCandles && pair.isPresent()
                ? loadCandlesForAutoLab(pair.get(), timeframe)
                : List.of();
        MarketRegime regime = candles.isEmpty() ? MarketRegime.UNKNOWN : marketRegimeDetector.detect(candles);
        NewsContext newsContext = CryptoNewsIntelligence.contextService()
                .getContextForSymbol(symbol.contains("/") ? symbol.substring(0, symbol.indexOf('/')) : symbol,
                        Duration.ofHours(24));
        return new StrategyGenerationContext(
                symbol,
                timeframe,
                candles,
                regime,
                RiskProfile.conservative(),
                strategyDescriptionArea == null ? "" : strategyDescriptionArea.getText(),
                newsContext);
    }

    private List<CandleData> loadCandlesForAutoLab(TradePair pair, Timeframe timeframe) {
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
        if (candles.size() < 60 && systemCore != null && systemCore.getExchange() != null) {
            candles = fetchCandlesFromExchange(pair, timeframe, startTime, endTime, repository);
        }
        return candles;
    }

    private String buildAiWelcomeMessage() {
        String service = systemCore == null || systemCore.getAiReasoningService() == null
                ? "local strategy assistant"
                : systemCore.getAiReasoningService().getServiceName();
        return "Connected to " + service + ". Current strategy has "
                + (ruleRows == null ? 0 : ruleRows.size())
                + " rule(s). Ask for a review or a concrete rule change.";
    }

    private String answerAiAssistantQuestion(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        StringBuilder response = new StringBuilder();

        if (normalized.contains("add") && normalized.contains("rsi")) {
            Platform.runLater(() -> addIndicatorRule(SignalType.BUY, INDICATORS.RSI, selectedOrDefaultTimeframe()));
            return "I added a BUY RSI rule. Tune oversold/overbought from the row Settings button.";
        }
        if (normalized.contains("add") && normalized.contains("hammer")) {
            Platform.runLater(() -> addCandlePatternRule(CandlePattern.HAMMER, selectedOrDefaultTimeframe()));
            return "I added a Hammer candle-pattern rule. It will participate in backtests immediately.";
        }
        if (normalized.contains("test") || normalized.contains("backtest")) {
            Platform.runLater(this::testStrategy);
            return "I opened Backtesting and started the strategy test.";
        }
        if (normalized.contains("balance") || normalized.contains("minimum")) {
            Platform.runLater(() -> {
                if (minBuySignalsField != null) {
                    minBuySignalsField.setText(Integer.toString(Math.max(1, Math.min(2, countRules("BUY")))));
                }
                if (minSellSignalsField != null) {
                    minSellSignalsField.setText(Integer.toString(Math.max(1, Math.min(2, countRules("SELL")))));
                }
                updateSignalThresholdLabels();
            });
            return "I balanced the minimum signal thresholds against the current BUY/SELL rule counts.";
        }

        response.append("Review: ");
        if (ruleRows == null || ruleRows.isEmpty()) {
            response.append("No rules are configured yet. Start with one indicator rule and one confirmation rule.");
        } else {
            long indicatorCount = ruleRows.stream().filter(r -> r.getRuleSource() == StrategyRuleSource.INDICATOR)
                    .count();
            long candleCount = ruleRows.stream().filter(r -> r.getRuleSource() == StrategyRuleSource.CANDLE_PATTERN)
                    .count();
            response.append("You have ").append(indicatorCount).append(" indicator rule(s) and ")
                    .append(candleCount).append(" candle-pattern rule(s). ");
            if (indicatorCount == 0) {
                response.append("Add an indicator filter for trend or momentum confirmation. ");
            }
            if (candleCount == 0) {
                response.append("Add a candle pattern if you want price-action confirmation. ");
            }
            response.append(
                    "Run Backtesting after each change and compare trade count, return, drawdown, and profit factor.");
        }
        return response.toString();
    }

    private void syncParameterRowsFromRules() {
        parameterRows.clear();
        if (ruleRows == null) {
            return;
        }
        for (StrategyRuleRow rule : ruleRows) {
            if (rule.getParameters().isEmpty()) {
                parameterRows.add(new StrategyParameterRow(rule.getIndicatorName(), "(no parameters)", ""));
                continue;
            }
            rule.getParameters().forEach(
                    (name, value) -> parameterRows.add(new StrategyParameterRow(rule.getIndicatorName(), name, value)));
        }
    }

    private void addIndicatorRule(SignalType signalType, INDICATORS indicator, Timeframe timeframe) {
        if (indicator == null) {
            return;
        }
        StrategyRuleRow row = new StrategyRuleRow(
                StrategyRuleSource.INDICATOR,
                signalType == null ? SignalType.BUY : signalType,
                indicator,
                null,
                timeframe == null ? selectedOrDefaultTimeframe() : timeframe);
        ruleRows.add(row);
        syncParameterRowsFromRules();
        updateSignalThresholdLabels();
        updatePreview();
    }

    private void addCandlePatternRule(CandlePattern pattern, Timeframe timeframe) {
        if (pattern == null) {
            return;
        }
        StrategyRuleRow row = new StrategyRuleRow(pattern,
                timeframe == null ? selectedOrDefaultTimeframe() : timeframe);
        ruleRows.add(row);
        syncParameterRowsFromRules();
        updateSignalThresholdLabels();
        updatePreview();
    }

    private Timeframe selectedOrDefaultTimeframe() {
        if (timeframeCombo != null && timeframeCombo.getValue() != null) {
            return timeframeCombo.getValue();
        }
        return Timeframe.H1;
    }

    /** Assembles StrategyParameters from the current table rows. */
    private StrategyParameters buildParameters() {
        StrategyParameters.StrategyParametersBuilder builder = StrategyParameters.builder();
        if (ruleRows != null && !ruleRows.isEmpty()) {
            for (StrategyRuleRow rule : ruleRows) {
                for (Map.Entry<String, String> entry : rule.getParameters().entrySet()) {
                    applyParameter(builder, rule.getIndicatorName(), entry.getKey(), entry.getValue());
                }
            }
            return builder.build();
        }
        for (StrategyParameterRow row : effectiveParameterRows()) {
            String param = row.getParameterName().toLowerCase(Locale.ROOT).replace(" ", "");
            String val = row.getValue().trim();
            if (val.isEmpty() || val.equals("(no parameters)"))
                continue;
            try {
                switch (param) {
                    case "period" -> {
                        // Route "period" to the correct field based on indicator name
                        String ind = row.getIndicatorName().toLowerCase(Locale.ROOT);
                        if (ind.contains("rsi"))
                            builder.rsiPeriod(Integer.parseInt(val));
                        else if (ind.contains("atr"))
                            builder.atrPeriod(Integer.parseInt(val));
                        else if (ind.contains("fast"))
                            builder.emaFast(Integer.parseInt(val));
                        else if (ind.contains("slow"))
                            builder.emaSlow(Integer.parseInt(val));
                        else if (ind.contains("breakout") || ind.contains("donchian"))
                            builder.breakoutLookback(Integer.parseInt(val));
                    }
                    case "rsiperiod" -> builder.rsiPeriod(Integer.parseInt(val));
                    case "fastperiod" -> builder.emaFast(Integer.parseInt(val));
                    case "slowperiod" -> builder.emaSlow(Integer.parseInt(val));
                    case "atrperiod" -> builder.atrPeriod(Integer.parseInt(val));
                    case "breakoutlookback" -> builder.breakoutLookback(Integer.parseInt(val));
                    case "oversold" -> builder.oversoldThreshold(Double.parseDouble(val));
                    case "overbought" -> builder.overboughtThreshold(Double.parseDouble(val));
                    case "minconfidence" -> builder.minConfidence(Double.parseDouble(val));
                    case "signalamount" -> builder.signalAmount(Double.parseDouble(val));
                    default -> {
                        /* indicator-specific param — stored in description/metadata */ }
                }
            } catch (NumberFormatException ex) {
                log.warn("Non-numeric value '{}' for param '{}' — skipped", val, param);
            }
        }
        return builder.build();
    }

    private void applyParameter(
            StrategyParameters.StrategyParametersBuilder builder,
            String indicatorName,
            String parameterName,
            String value) {
        String param = parameterName == null ? "" : parameterName.toLowerCase(Locale.ROOT).replace(" ", "");
        String val = value == null ? "" : value.trim();
        if (val.isEmpty() || val.equals("(no parameters)")) {
            return;
        }
        try {
            switch (param) {
                case "period" -> {
                    String ind = indicatorName == null ? "" : indicatorName.toLowerCase(Locale.ROOT);
                    if (ind.contains("rsi"))
                        builder.rsiPeriod(Integer.parseInt(val));
                    else if (ind.contains("atr"))
                        builder.atrPeriod(Integer.parseInt(val));
                    else if (ind.contains("fast"))
                        builder.emaFast(Integer.parseInt(val));
                    else if (ind.contains("slow"))
                        builder.emaSlow(Integer.parseInt(val));
                    else if (ind.contains("breakout") || ind.contains("donchian"))
                        builder.breakoutLookback(Integer.parseInt(val));
                }
                case "rsiperiod" -> builder.rsiPeriod(Integer.parseInt(val));
                case "fastperiod" -> builder.emaFast(Integer.parseInt(val));
                case "slowperiod" -> builder.emaSlow(Integer.parseInt(val));
                case "atrperiod" -> builder.atrPeriod(Integer.parseInt(val));
                case "breakoutlookback" -> builder.breakoutLookback(Integer.parseInt(val));
                case "oversold" -> builder.oversoldThreshold(Double.parseDouble(val));
                case "overbought" -> builder.overboughtThreshold(Double.parseDouble(val));
                case "minconfidence" -> builder.minConfidence(Double.parseDouble(val));
                case "signalamount" -> builder.signalAmount(Double.parseDouble(val));
                default -> {
                    /* indicator-specific param stored in rule metadata */ }
            }
        } catch (NumberFormatException ex) {
            log.warn("Non-numeric value '{}' for param '{}' skipped", val, param);
        }
    }

    private void updatePreview() {
        if (paramsPreviewLabel == null)
            return;
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
        if ((ruleRows == null || ruleRows.isEmpty()) && parameterRows.isEmpty()) {
            showAlert("Validation Error", "Please add at least one rule.");
            return false;
        }
        // Check all editable values are numeric where applicable
        for (StrategyParameterRow row : effectiveParameterRows()) {
            String val = row.getValue().trim();
            if (val.isEmpty() || val.equals("(no parameters)"))
                continue;
            try {
                Double.parseDouble(val);
            } catch (NumberFormatException e) {
                if (val.matches("[A-Za-z_\\- ]+")) {
                    continue;
                }
                showAlert("Validation Error",
                        "Parameter '" + row.getParameterName() + "' of " + row.getIndicatorName() +
                                " has non-numeric value: " + val);
                return false;
            }
        }
        if (!validateSignalThreshold(minBuySignalsField, countRules("BUY"), "Minimum Buy Signals")
                || !validateSignalThreshold(minSellSignalsField, countRules("SELL"), "Minimum Sell Signals")) {
            return false;
        }
        showAlert("Validation Passed",
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

        String indicatorSummary = (ruleRows != null && !ruleRows.isEmpty() ? ruleRows.stream()
                .map(StrategyRuleRow::getIndicatorName)
                : parameterRows.stream()
                        .map(StrategyParameterRow::getIndicatorName))
                .distinct()
                .collect(Collectors.joining(", "));

        showAlert("Strategy Saved ✓",
                "'" + definition.getName() + "' is now registered in the live strategy registry.\n"
                        + "Indicators: " + indicatorSummary
                        + "\n\nIt can now be assigned from the Strategy Lab, Backtesting, or decision engines.");
        log.info("User strategy '{}' saved and registered with params: {}", definition.getName(),
                definition.getParameters());
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
        openBacktestingAndRun(definition, pair, selectedTimeframe);
    }

    private void openBacktestingAndRun(StrategyDefinition definition, TradePair pair, Timeframe timeframe) {
        try {
            BacktestingPanel backtestingPanel = new BacktestingPanel(systemCore);
            Stage stage = new Stage();
            stage.setTitle("Backtesting - " + definition.getName());
            stage.setScene(new Scene(backtestingPanel, 1200, 820));
            stage.setResizable(true);
            stage.show();
            backtestingPanel.configureAndRun(definition.getName(), pair, timeframe);
            log.info("Opened Backtesting panel and started '{}' on {}/{}",
                    definition.getName(), pair.toString('/'), timeframe.getCode());
        } catch (Exception exception) {
            log.error("Unable to open Backtesting panel for {}", definition.getName(), exception);
            CompletableFuture
                    .supplyAsync(() -> runRealBacktest(definition, pair, timeframe))
                    .whenComplete((report, throwable) -> Platform.runLater(() -> {
                        if (throwable != null) {
                            showAlert("Backtest Failed", throwable.getMessage());
                        } else if (report == null) {
                            showAlert("Backtest Failed", "No backtest result was produced.");
                        } else {
                            showAlert("Backtest Complete", summarizeBacktest(report));
                        }
                    }));
        }
    }

    private void resetForm() {
        strategyNameField.clear();
        categoryCombo.setValue(null);
        timeframeCombo.setValue(null);
        if (categoryFilterCombo != null) {
            categoryFilterCombo.getSelectionModel().selectFirst();
        }
        strategyDescriptionArea.clear();
        if (ruleRows != null) {
            ruleRows.clear();
        }
        parameterRows.clear();
        if (minBuySignalsField != null) {
            minBuySignalsField.setText("1");
        }
        if (minSellSignalsField != null) {
            minSellSignalsField.setText("1");
        }
        updateSignalThresholdLabels();
        updatePreview();
        log.info("Strategy builder form reset");
    }

    private StrategyDefinition currentStrategyDefinition() {
        String name = strategyNameField.getText().trim();
        return StrategyDefinition.builder()
                .name(name)
                .baseName(name)
                .parameters(buildParameters())
                .rules(buildRuleDefinitions())
                .build();
    }

    private void loadStrategyDefinitionIntoBuilder(StrategyDefinition definition) {
        if (definition == null) {
            return;
        }
        if (strategyNameField != null) {
            strategyNameField.setText(definition.getName() == null ? "" : definition.getName());
        }
        if (categoryCombo != null && categoryCombo.getValue() == null && !categoryCombo.getItems().isEmpty()) {
            categoryCombo.getSelectionModel().selectFirst();
        }
        if (definition.getRules() != null && !definition.getRules().isEmpty()) {
            Timeframe primaryTimeframe = definition.getRules().stream()
                    .map(StrategyRuleDefinition::timeframe)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(selectedOrDefaultTimeframe());
            if (timeframeCombo != null) {
                timeframeCombo.setValue(primaryTimeframe);
            }
            ruleRows.clear();
            for (StrategyRuleDefinition rule : definition.getRules()) {
                StrategyRuleRow row = new StrategyRuleRow(
                        rule.ruleSource(),
                        rule.signalType(),
                        rule.indicator(),
                        rule.candlePattern(),
                        rule.timeframe() == null ? primaryTimeframe : rule.timeframe());
                row.getParameters().clear();
                if (rule.parameters() != null) {
                    row.getParameters().putAll(rule.parameters());
                }
                ruleRows.add(row);
            }
        }
        syncParameterRowsFromRules();
        updateSignalThresholdLabels();
        updatePreview();
    }

    private List<StrategyRuleDefinition> buildRuleDefinitions() {
        if (ruleRows == null || ruleRows.isEmpty()) {
            return List.of();
        }
        return ruleRows.stream()
                .map(row -> new StrategyRuleDefinition(
                        row.getRuleSource(),
                        row.getSignalType(),
                        row.getIndicator(),
                        row.getCandlePattern(),
                        row.getTimeframe(),
                        Map.copyOf(row.getParameters())))
                .toList();
    }

    private void persistStrategyDefinition(StrategyDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return;
        }

        USER_DEFINED_STRATEGIES.put(definition.getName().toLowerCase(Locale.ROOT), definition);
        StrategyCatalog.registerRuntimeDefinition(definition);
        StrategyRegistry.getInstance().registerDefinition(definition);
        UserStrategyDefinitionStore.getDefault().save(definition);
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

        try {
            UniversalTradabilityService tradabilityService = new UniversalTradabilityService(systemCore.getExchange(),
                    null);
            List<SymbolTradability> statuses = tradabilityService.getTradability(tradePairs).get();
            Set<String> marketDataAllowed = statuses.stream()
                    .filter(Objects::nonNull)
                    .filter(SymbolTradability::marketDataAllowed)
                    .map(SymbolTradability::tradePair)
                    .filter(Objects::nonNull)
                    .map(pair -> pair.toString('/').toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            tradePairs = tradePairs.stream()
                    .filter(Objects::nonNull)
                    .filter(pair -> marketDataAllowed.contains(pair.toString('/').toUpperCase(Locale.ROOT)))
                    .toList();
        } catch (Exception exception) {
            log.warn("Unable to apply tradability filter in StrategyBuilderPanel", exception);
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

        // If repository is empty, fall back to live fetch from exchange supplier
        if (candles.size() < 50 && systemCore != null && systemCore.getExchange() != null) {
            log.info("Repository has {} candles for {}/{}, fetching from exchange...",
                    candles.size(), pair.toString('/'), timeframe.getCode());
            candles = fetchCandlesFromExchange(pair, timeframe, startTime, endTime, repository);
        }

        if (candles.size() < 50) {
            throw new IllegalStateException(
                    "Not enough historical candles for backtest: " + candles.size() +
                            ". Try a different timeframe or ensure data is available for " + pair.toString('/') + ".");
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

    private List<CandleData> fetchCandlesFromExchange(
            TradePair pair,
            Timeframe timeframe,
            LocalDateTime startTime,
            LocalDateTime endTime,
            HistoricalDataRepository repository) {
        try {
            CandleDataSupplier supplier = systemCore.getExchange()
                    .getCandleDataSupplier(timeframe.getSeconds(), pair);
            if (supplier == null) {
                log.warn("Exchange returned null CandleDataSupplier for {}/{}", pair.toString('/'),
                        timeframe.getCode());
                return List.of();
            }

            Future<List<CandleData>> future = supplier.get();
            List<CandleData> fetched = future.get();

            if (fetched != null && !fetched.isEmpty()) {
                log.info("Fetched {} candles from exchange for {}/{}", fetched.size(), pair.toString('/'),
                        timeframe.getCode());
                // Persist for future backtest runs
                try {
                    repository.saveHistoricalData(pair, startTime, endTime, timeframe.getCode(), fetched);
                } catch (Exception saveEx) {
                    log.warn("Could not persist fetched candles: {}", saveEx.getMessage());
                }
                return fetched;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Candle fetch interrupted for {}/{}", pair.toString('/'), timeframe.getCode());
        } catch (Exception ex) {
            log.warn("Failed to fetch candles from exchange for {}/{}: {}", pair.toString('/'), timeframe.getCode(),
                    ex.getMessage());
        }
        return List.of();
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
    private List<StrategyParameterRow> effectiveParameterRows() {
        if (ruleRows != null && !ruleRows.isEmpty()) {
            syncParameterRowsFromRules();
        }
        return parameterRows;
    }

    private int countRules(String signalType) {
        if (ruleRows == null) {
            return 0;
        }
        return (int) ruleRows.stream()
                .filter(row -> row.getSignalType().name().equalsIgnoreCase(signalType))
                .count();
    }

    private boolean validateSignalThreshold(TextField field, int max, String label) {
        if (field == null) {
            return true;
        }
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 1 || value > Math.max(max, 1)) {
                showAlert("Validation Error", label + " must be between 1 and " + Math.max(max, 1) + ".");
                return false;
            }
            return true;
        } catch (NumberFormatException exception) {
            showAlert("Validation Error", label + " must be numeric.");
            return false;
        }
    }

    private void adjustSignalThreshold(TextField field, int delta) {
        if (field == null) {
            return;
        }
        int current;
        try {
            current = Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            current = 1;
        }
        field.setText(Integer.toString(Math.max(1, current + delta)));
    }

    private void updateSignalThresholdLabels() {
        if (minBuyOutOfLabel != null) {
            minBuyOutOfLabel.setText("out of " + countRules("BUY"));
        }
        if (minSellOutOfLabel != null) {
            minSellOutOfLabel.setText("out of " + countRules("SELL"));
        }
    }

    private String buildCodePreview() {
        if (ruleRows == null || ruleRows.isEmpty()) {
            return "No rules configured.";
        }
        return ruleRows.stream()
                .map(row -> row.getSignalType() + " " + row.getIndicatorName() + " on " + row.getCandleSize()
                        + " " + row.getParameters())
                .collect(Collectors.joining("\n"));
    }

    private boolean hasCandlePatternRules() {
        return ruleRows != null && ruleRows.stream()
                .anyMatch(row -> row.getRuleSource() == StrategyRuleSource.CANDLE_PATTERN);
    }

    private @NonNull Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("strategy-primary-button");
        return button;
    }

    private @NonNull Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("strategy-secondary-button");
        return button;
    }

    private @NonNull VBox createStackedInput(String label, Control input) {
        Label lbl = mutedLabel(label);
        VBox box = new VBox(6, lbl, input);
        VBox.setVgrow(input, Priority.NEVER);
        return box;
    }

    private @NonNull Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("strategy-muted-label");
        return label;
    }

    private @NonNull ListCell<IndicatorCategory> indicatorCategoryCell(String emptyText) {
        return new ListCell<>() {
            @Override
            protected void updateItem(IndicatorCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? emptyText : item.name());
            }
        };
    }

    private @NonNull ListCell<INDICATORS> indicatorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(INDICATORS item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "[" + item.getCategory().name() + "] " + item.getDisplayName());
            }
        };
    }

    private VBox styledSection(String borderColor) {
        VBox v = new VBox(10);
        v.setPadding(new Insets(12));
        v.setStyle("-fx-background-color: #16213e; -fx-border-color: " + borderColor +
                "; -fx-border-width: 1; -fx-border-radius: 6;");
        return v;
    }

    private @NonNull Label sectionHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        return l;
    }

    private @NonNull Label styledLabel() {
        Label l = new Label("Configured Indicators / Parameters (double-click Value to edit):");
        l.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px;");
        return l;
    }

    private @NonNull HBox createLabeledInput(String label, Control input) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #a0aec0; -fx-min-width: 130px;");
        HBox box = new HBox(8, lbl, input);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        return box;
    }

    private void styleInput(@NonNull TextField tf) {
        tf.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #6b7280;");
    }

    private @NonNull Button styledBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-padding: 10px 22px; -fx-font-size: 13px; -fx-background-color: " + color
                + "; -fx-text-fill: white;");
        return b;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setAutoStrategyStatus(String message) {
        String text = message == null || message.isBlank() ? "Auto Strategy Lab: idle"
                : "Auto Strategy Lab: " + message;
        if (autoStrategyStatusLabel != null) {
            autoStrategyStatusLabel.setText(text);
        }
        log.info(text);
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
    public static class StrategyRuleRow {
        private final BooleanProperty selected = new SimpleBooleanProperty(true);
        private final StrategyRuleSource ruleSource;
        private final SignalType signalType;
        private final INDICATORS indicator;
        private final CandlePattern candlePattern;
        private final SimpleStringProperty sourceName;
        private final SimpleStringProperty displayName;
        private final SimpleStringProperty signalTypeName;
        private final Timeframe timeframe;
        private final SimpleStringProperty candleSize;
        private final Map<String, String> parameters = new LinkedHashMap<>();

        public StrategyRuleRow(String signalType, INDICATORS indicator, Timeframe timeframe) {
            this(StrategyRuleSource.INDICATOR,
                    parseSignalType(signalType),
                    indicator,
                    null,
                    timeframe);
        }

        public StrategyRuleRow(CandlePattern candlePattern, Timeframe timeframe) {
            this(StrategyRuleSource.CANDLE_PATTERN,
                    candlePattern == null ? SignalType.NEUTRAL : candlePattern.getDefaultSignal(),
                    null,
                    candlePattern,
                    timeframe);
        }

        public StrategyRuleRow(
                StrategyRuleSource ruleSource,
                SignalType signalType,
                INDICATORS indicator,
                CandlePattern candlePattern,
                Timeframe timeframe) {
            this.ruleSource = ruleSource == null ? StrategyRuleSource.INDICATOR : ruleSource;
            this.signalType = signalType == null ? SignalType.NEUTRAL : signalType;
            this.indicator = indicator;
            this.candlePattern = candlePattern;
            this.sourceName = new SimpleStringProperty(displaySource(this.ruleSource));
            this.displayName = new SimpleStringProperty(resolveDisplayName(this.ruleSource, indicator, candlePattern));
            this.signalTypeName = new SimpleStringProperty(this.signalType.name());
            this.timeframe = timeframe;
            this.candleSize = new SimpleStringProperty(timeframe == null ? "" : timeframe.getCode());
            if (indicator != null) {
                IndicatorDefinition definition = IndicatorCatalog.get(indicator);
                definition.parameters()
                        .forEach(parameter -> parameters.put(parameter.name(), parameter.defaultValue()));
            }
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public SimpleStringProperty signalTypeProperty() {
            return signalTypeName;
        }

        public SimpleStringProperty sourceNameProperty() {
            return sourceName;
        }

        public SimpleStringProperty displayNameProperty() {
            return displayName;
        }

        public SimpleStringProperty indicatorNameProperty() {
            return displayName;
        }

        public SimpleStringProperty candleSizeProperty() {
            return candleSize;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        public StrategyRuleSource getRuleSource() {
            return ruleSource;
        }

        public SignalType getSignalType() {
            return signalType;
        }

        public INDICATORS getIndicator() {
            return indicator;
        }

        public CandlePattern getCandlePattern() {
            return candlePattern;
        }

        public String getDisplayName() {
            return displayName.get();
        }

        public String getIndicatorName() {
            return displayName.get();
        }

        public Timeframe getTimeframe() {
            return timeframe;
        }

        public String getCandleSize() {
            return candleSize.get();
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        private static SignalType parseSignalType(String value) {
            if (value == null || value.isBlank()) {
                return SignalType.BUY;
            }
            try {
                return SignalType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return SignalType.NEUTRAL;
            }
        }

        private static String resolveDisplayName(
                StrategyRuleSource ruleSource,
                INDICATORS indicator,
                CandlePattern candlePattern) {
            if (ruleSource == StrategyRuleSource.CANDLE_PATTERN) {
                return candlePattern == null ? "Unknown Candle Pattern" : candlePattern.getDisplayName();
            }
            return indicator == null ? "Unknown Indicator" : indicator.getDisplayName();
        }

        private static String displaySource(StrategyRuleSource source) {
            return switch (source == null ? StrategyRuleSource.INDICATOR : source) {
                case INDICATOR -> "Indicator";
                case CANDLE_PATTERN -> "Candle Pattern";
                case PRICE_ACTION -> "Price Action";
                case AI_FILTER -> "AI Filter";
            };
        }
    }

    public static class StrategyParameterRow {
        private final SimpleStringProperty indicatorName;
        private final SimpleStringProperty parameterName;
        private final SimpleStringProperty value;

        public StrategyParameterRow(String indicatorName, String parameterName, String value) {
            this.indicatorName = new SimpleStringProperty(indicatorName);
            this.parameterName = new SimpleStringProperty(parameterName);
            this.value = new SimpleStringProperty(value);
        }

        public SimpleStringProperty indicatorNameProperty() {
            return indicatorName;
        }

        public SimpleStringProperty parameterNameProperty() {
            return parameterName;
        }

        public SimpleStringProperty valueProperty() {
            return value;
        }

        public String getIndicatorName() {
            return indicatorName.get();
        }

        public String getParameterName() {
            return parameterName.get();
        }

        public String getValue() {
            return value.get();
        }

        public void setValue(String v) {
            value.set(v);
        }
    }

    /**
     * @deprecated Use {@link StrategyParameterRow} instead.
     *             Kept for binary compatibility if anything still references the
     *             old record.
     */
    @Deprecated
    public record StrategyParameter(String name, String parameter, String value) {
    }
}
