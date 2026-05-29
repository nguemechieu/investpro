package org.investpro.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.nocode.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaFX panel for creating, editing, validating, and persisting
 * no-code trading strategies.
 *
 * <p>Normal users can build strategies by selecting indicators,
 * operators, and values through a visual interface without writing code.
 * The resulting {@link NoCodeStrategyDefinition} is compiled, validated,
 * and persisted via {@link NoCodeStrategyService}.</p>
 *
 * <p><strong>CRITICAL:</strong> This panel only creates strategy definitions.
 * It never submits orders. All strategies flow through the InvestPro
 * risk and execution pipeline before any trade is placed.</p>
 *
 * <p>Layout sections:
 * <ol>
 *   <li>Strategy Info — name, description, symbol, timeframe</li>
 *   <li>Entry Rules — condition builder</li>
 *   <li>Exit Rules — condition builder</li>
 *   <li>Risk Settings — stop-loss, take-profit, limits</li>
 *   <li>Preview — generated readable logic</li>
 *   <li>Validation Results — errors and warnings</li>
 *   <li>Action bar — Save, Validate, Clone, Delete</li>
 * </ol>
 * </p>
 */
@Slf4j
public class StrategyBuilderPanel extends VBox {

    private final NoCodeStrategyService service;
    private final NoCodeStrategyValidator validator;

    // ── Strategy Info
    private final TextField nameField       = new TextField();
    private final TextArea  descField       = new TextArea();
    private final TextField symbolField     = new TextField();
    private final TextField timeframeField  = new TextField();
    private final TextField authorField     = new TextField();

    // ── Rules
    private final ListView<NoCodeRule> entryRuleList = new ListView<>();
    private final ListView<NoCodeRule> exitRuleList  = new ListView<>();

    // ── Risk settings fields
    private final Spinner<Double> slPctSpinner  = new Spinner<>(0.0, 50.0, 1.5, 0.1);
    private final Spinner<Double> tpPctSpinner  = new Spinner<>(0.0, 50.0, 3.0, 0.1);
    private final Spinner<Integer> maxTradesSpin = new Spinner<>(0, 100, 5, 1);
    private final Spinner<Double> maxDdSpin     = new Spinner<>(0.0, 100.0, 10.0, 0.5);
    private final Spinner<Double> maxPosSpin    = new Spinner<>(0.0, 100.0, 5.0, 0.5);

    // ── Preview / validation
    private final TextArea previewArea     = new TextArea();
    private final TextArea validationArea  = new TextArea();

    // ── Strategy list (left panel)
    private final ListView<String> strategyList = new ListView<>();
    private final ObservableList<NoCodeStrategyDefinition> loadedStrategies =
            FXCollections.observableArrayList();

    private NoCodeStrategyDefinition currentStrategy = null;

    // =========================================================================
    // Construction
    // =========================================================================

    /** Creates the panel using the default service. */
    public StrategyBuilderPanel() {
        this(new NoCodeStrategyService());
    }

    /** Creates the panel with a custom service (useful for testing). */
    public StrategyBuilderPanel(NoCodeStrategyService service) {
        this.service = service;
        this.validator = new NoCodeStrategyValidator();
        buildLayout();
        reloadStrategyList();
    }

    // =========================================================================
    // Layout
    // =========================================================================

    private void buildLayout() {
        setSpacing(0);
        setStyle("-fx-background-color: #1e1e2e;");

        Label title = new Label("🛠 No-Code Strategy Builder");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4; -fx-padding: 12 16 8 16;");

        SplitPane split = new SplitPane();
        split.getItems().addAll(buildLeftPanel(), buildCenterPanel());
        split.setDividerPositions(0.22);
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(title, split);
    }

    // ── Left panel: strategy list + action buttons ──────────────────────

    private VBox buildLeftPanel() {
        strategyList.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        strategyList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, newVal) -> { if (newVal != null) loadStrategyByName(newVal); });
        VBox.setVgrow(strategyList, Priority.ALWAYS);

        Button newBtn    = actionBtn("➕ New",    e -> newStrategy());
        Button delBtn    = actionBtn("🗑 Delete", e -> deleteSelected());
        Button cloneBtn  = actionBtn("📄 Clone",  e -> cloneSelected());

        HBox listBtns = new HBox(6, newBtn, delBtn, cloneBtn);
        listBtns.setPadding(new Insets(6));

        VBox left = new VBox(6, new Label(styled("Strategies", "#cdd6f4")), strategyList, listBtns);
        left.setStyle("-fx-background-color: #181825; -fx-padding: 10;");
        return left;
    }

    // ── Center panel: tabs ────────────────────────────────────────────

    private TabPane buildCenterPanel() {
        TabPane tabs = new TabPane();
        tabs.setStyle("-fx-background-color: #1e1e2e;");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            tab("📋 Info",           buildInfoTab()),
            tab("↑ Entry Rules",       buildEntryRulesTab()),
            tab("↓ Exit Rules",        buildExitRulesTab()),
            tab("🛡 Risk Settings",   buildRiskTab()),
            tab("👁 Preview",         buildPreviewTab()),
            tab("✔ Validation",        buildValidationTab())
        );
        return tabs;
    }

    private Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setStyle("-fx-text-fill: #cdd6f4;");
        return t;
    }

    // ── Info tab ───────────────────────────────────────────────────────────

    private ScrollPane buildInfoTab() {
        styleInput(nameField, "e.g. RSI Oversold Strategy");
        descField.setPrefRowCount(3);
        descField.setStyle("-fx-control-inner-background: #313244; -fx-text-fill: #cdd6f4; -fx-prompt-text-fill: #6c7086;");
        styleInput(symbolField, "e.g. BTC/USD (blank = any)");
        styleInput(timeframeField, "e.g. 1h (blank = any)");
        styleInput(authorField, "Your name or username");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: #1e1e2e;");
        addRow(grid, 0, "Strategy Name *", nameField);
        addRow(grid, 1, "Description", descField);
        addRow(grid, 2, "Primary Symbol", symbolField);
        addRow(grid, 3, "Primary Timeframe", timeframeField);
        addRow(grid, 4, "Author", authorField);

        Button saveBtn = actionBtn("💾 Save Strategy", e -> saveStrategy());
        HBox buttons = new HBox(10, saveBtn);
        buttons.setPadding(new Insets(0, 16, 16, 16));

        VBox content = new VBox(grid, buttons);
        content.setStyle("-fx-background-color: #1e1e2e;");
        return new ScrollPane(content);
    }

    // ── Entry Rules tab ─────────────────────────────────────────────────

    private VBox buildEntryRulesTab() {
        return buildRulesTab(entryRuleList, "Entry Rules (fire to OPEN positions)",
                NoCodeAction.BUY);
    }

    private VBox buildExitRulesTab() {
        return buildRulesTab(exitRuleList, "Exit Rules (fire to CLOSE positions)",
                NoCodeAction.SELL);
    }

    private VBox buildRulesTab(ListView<NoCodeRule> listView, String hint, NoCodeAction defaultAction) {
        listView.setStyle("-fx-background-color: #313244;");
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NoCodeRule rule, boolean empty) {
                super.updateItem(rule, empty);
                if (empty || rule == null) { setText(null); }
                else { setText(rule.toPreviewText()); setStyle("-fx-text-fill: #cdd6f4;"); }
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        Label hintLabel = new Label(hint);
        hintLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-padding: 4 0 4 0;");

        Button addBtn = actionBtn("➕ Add Rule", e -> addSimpleRule(listView, defaultAction));
        Button delBtn = actionBtn("🗑 Remove",  e -> {
            NoCodeRule sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) listView.getItems().remove(sel);
        });

        HBox btns = new HBox(8, addBtn, delBtn);
        btns.setPadding(new Insets(8));

        VBox box = new VBox(6, hintLabel, listView, btns);
        box.setStyle("-fx-background-color: #1e1e2e; -fx-padding: 12;");
        return box;
    }

    /** Adds a pre-built example rule (RSI < 30 → BUY or RSI > 70 → SELL). */
    private void addSimpleRule(ListView<NoCodeRule> listView, NoCodeAction action) {
        // Build a sensible default condition based on action
        NoCodeCondition cond;
        if (action == NoCodeAction.BUY) {
            cond = NoCodeCondition.builder()
                    .leftIndicator(NoCodeIndicatorReference.builder().type(NoCodeIndicatorType.RSI).period(14).build())
                    .operator(NoCodeConditionOperator.LESS_THAN)
                    .rightValue(30)
                    .label("RSI < 30 (oversold)")
                    .build();
        } else {
            cond = NoCodeCondition.builder()
                    .leftIndicator(NoCodeIndicatorReference.builder().type(NoCodeIndicatorType.RSI).period(14).build())
                    .operator(NoCodeConditionOperator.GREATER_THAN)
                    .rightValue(70)
                    .label("RSI > 70 (overbought)")
                    .build();
        }
        NoCodeRule rule = NoCodeRule.builder()
                .condition(cond)
                .action(action)
                .confidence(0.7)
                .build();
        listView.getItems().add(rule);
    }

    // ── Risk settings tab ───────────────────────────────────────────────

    private ScrollPane buildRiskTab() {
        slPctSpinner.setEditable(true);
        tpPctSpinner.setEditable(true);
        maxTradesSpin.setEditable(true);
        maxDdSpin.setEditable(true);
        maxPosSpin.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: #1e1e2e;");
        addRow(grid, 0, "Stop Loss (%)",        slPctSpinner);
        addRow(grid, 1, "Take Profit (%)",      tpPctSpinner);
        addRow(grid, 2, "Max Trades / Day",     maxTradesSpin);
        addRow(grid, 3, "Max Drawdown (%)",     maxDdSpin);
        addRow(grid, 4, "Max Position Size (%)",maxPosSpin);

        Label note = new Label("⚠ These settings inform the InvestPro RiskEngine. The RiskEngine is the final authority.");
        note.setStyle("-fx-text-fill: #f38ba8; -fx-wrap-text: true; -fx-padding: 10 16 0 16;");

        VBox content = new VBox(grid, note);
        content.setStyle("-fx-background-color: #1e1e2e;");
        return new ScrollPane(content);
    }

    // ── Preview tab ─────────────────────────────────────────────────────────

    private VBox buildPreviewTab() {
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-control-inner-background: #181825; -fx-text-fill: #a6e3a1; -fx-font-family: monospace;");
        VBox.setVgrow(previewArea, Priority.ALWAYS);

        Button refreshBtn = actionBtn("🔄 Refresh Preview", e -> refreshPreview());
        HBox btns = new HBox(refreshBtn); btns.setPadding(new Insets(8));

        VBox box = new VBox(6, previewArea, btns);
        box.setStyle("-fx-background-color: #1e1e2e; -fx-padding: 12;");
        return box;
    }

    // ── Validation tab ───────────────────────────────────────────────────

    private VBox buildValidationTab() {
        validationArea.setEditable(false);
        validationArea.setStyle("-fx-control-inner-background: #181825; -fx-text-fill: #cdd6f4; -fx-font-family: monospace;");
        VBox.setVgrow(validationArea, Priority.ALWAYS);

        Button validateBtn = actionBtn("✔ Validate Now", e -> runValidation());
        HBox btns = new HBox(validateBtn); btns.setPadding(new Insets(8));

        VBox box = new VBox(6, validationArea, btns);
        box.setStyle("-fx-background-color: #1e1e2e; -fx-padding: 12;");
        return box;
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void newStrategy() {
        clearForm();
        currentStrategy = null;
        nameField.setText("My Strategy");
    }

    private void saveStrategy() {
        NoCodeStrategyDefinition def = buildDefinitionFromForm();
        if (def == null) return;
        try {
            CompiledNoCodeStrategy result = service.save(def);
            currentStrategy = def;
            showValidationResult(result.getValidationResult());
            reloadStrategyList();
            log.info("Saved strategy: {}", def.getName());
        } catch (Exception ex) {
            log.error("Save failed: {}", ex.getMessage(), ex);
            Platform.runLater(() -> validationArea.setText("ERROR: " + ex.getMessage()));
        }
    }

    private void deleteSelected() {
        String selected = strategyList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Optional<NoCodeStrategyDefinition> found = service.getRepository().findAll().stream()
                .filter(d -> d.getName().equals(selected))
                .findFirst();
        found.ifPresent(def -> {
            service.delete(def.getStrategyId());
            clearForm();
            reloadStrategyList();
            log.info("Deleted strategy: {}", def.getName());
        });
    }

    private void cloneSelected() {
        if (currentStrategy == null) return;
        NoCodeStrategyDefinition clone = NoCodeStrategyDefinition.builder()
                .name(currentStrategy.getName() + " (Clone)")
                .description(currentStrategy.getDescription())
                .symbol(currentStrategy.getSymbol())
                .timeframe(currentStrategy.getTimeframe())
                .author(currentStrategy.getAuthor())
                .entryRules(new ArrayList<>(currentStrategy.getEntryRules()))
                .exitRules(new ArrayList<>(currentStrategy.getExitRules()))
                .riskSettings(currentStrategy.getRiskSettings())
                .build();
        service.save(clone);
        reloadStrategyList();
        log.info("Cloned strategy '{}'", currentStrategy.getName());
    }

    private void runValidation() {
        NoCodeStrategyDefinition def = buildDefinitionFromForm();
        if (def == null) return;
        ValidationResult result = validator.validate(def);
        showValidationResult(result);
    }

    private void refreshPreview() {
        NoCodeStrategyDefinition def = buildDefinitionFromForm();
        if (def == null) return;
        Platform.runLater(() -> previewArea.setText(def.toPreviewText()));
    }

    private void loadStrategyByName(String name) {
        service.getRepository().findAll().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .ifPresent(this::populateForm);
    }

    private void reloadStrategyList() {
        List<NoCodeStrategyDefinition> all = service.getRepository().findAll();
        Platform.runLater(() -> {
            ObservableList<String> names = FXCollections.observableArrayList();
            all.forEach(d -> names.add(d.getName()));
            strategyList.setItems(names);
            loadedStrategies.setAll(all);
        });
    }

    // =========================================================================
    // Form helpers
    // =========================================================================

    private NoCodeStrategyDefinition buildDefinitionFromForm() {
        String name = nameField.getText();
        if (name == null || name.isBlank()) {
            Platform.runLater(() -> validationArea.setText("ERROR: Strategy name is required."));
            return null;
        }

        NoCodeRiskSettings risk = NoCodeRiskSettings.builder()
                .stopLossPercent(slPctSpinner.getValue())
                .takeProfitPercent(tpPctSpinner.getValue())
                .maxTradesPerDay(maxTradesSpin.getValue())
                .maxDrawdownPercent(maxDdSpin.getValue())
                .maxPositionSizePercent(maxPosSpin.getValue())
                .build();

        String id = (currentStrategy != null) ? currentStrategy.getStrategyId() : null;
        var builder = NoCodeStrategyDefinition.builder()
                .name(name)
                .description(descField.getText())
                .symbol(symbolField.getText())
                .timeframe(timeframeField.getText())
                .author(authorField.getText())
                .riskSettings(risk)
                .entryRules(new ArrayList<>(entryRuleList.getItems()))
                .exitRules(new ArrayList<>(exitRuleList.getItems()));
        if (id != null) builder.strategyId(id);
        return builder.build();
    }

    private void populateForm(NoCodeStrategyDefinition def) {
        currentStrategy = def;
        Platform.runLater(() -> {
            nameField.setText(def.getName() != null ? def.getName() : "");
            descField.setText(def.getDescription() != null ? def.getDescription() : "");
            symbolField.setText(def.getSymbol() != null ? def.getSymbol() : "");
            timeframeField.setText(def.getTimeframe() != null ? def.getTimeframe() : "");
            authorField.setText(def.getAuthor() != null ? def.getAuthor() : "");
            entryRuleList.setItems(FXCollections.observableArrayList(def.getEntryRules()));
            exitRuleList.setItems(FXCollections.observableArrayList(def.getExitRules()));
            if (def.getRiskSettings() != null) {
                NoCodeRiskSettings risk = def.getRiskSettings();
                slPctSpinner.getValueFactory().setValue(risk.getStopLossPercent());
                tpPctSpinner.getValueFactory().setValue(risk.getTakeProfitPercent());
                maxTradesSpin.getValueFactory().setValue(risk.getMaxTradesPerDay());
                maxDdSpin.getValueFactory().setValue(risk.getMaxDrawdownPercent());
                maxPosSpin.getValueFactory().setValue(risk.getMaxPositionSizePercent());
            }
            previewArea.setText(def.toPreviewText());
        });
    }

    private void clearForm() {
        Platform.runLater(() -> {
            nameField.clear(); descField.clear(); symbolField.clear();
            timeframeField.clear(); authorField.clear();
            entryRuleList.getItems().clear();
            exitRuleList.getItems().clear();
            previewArea.clear(); validationArea.clear();
        });
    }

    private void showValidationResult(ValidationResult result) {
        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(result.isValid() ? "✅ VALID" : "? INVALID").append("\n\n");
            if (!result.getErrors().isEmpty()) {
                sb.append("ERRORS:\n");
                result.getErrors().forEach(e -> sb.append("  ✗ ").append(e).append("\n"));
                sb.append("\n");
            }
            if (!result.getWarnings().isEmpty()) {
                sb.append("WARNINGS:\n");
                result.getWarnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
            }
            validationArea.setText(sb.toString());
        });
    }

    // =========================================================================
    // UI style helpers
    // =========================================================================

    private Button actionBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        btn.setOnAction(handler);
        return btn;
    }

    private void styleInput(TextField tf, String prompt) {
        tf.setPromptText(prompt);
        tf.setStyle("-fx-control-inner-background: #313244; -fx-text-fill: #cdd6f4; -fx-prompt-text-fill: #6c7086;");
    }

    private Label styled(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        return l;
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #cdd6f4;");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }
}
