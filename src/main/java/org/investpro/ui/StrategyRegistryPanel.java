package org.investpro.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyDescriptor;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategyType;
import org.investpro.strategy.StrategyValidationStatus;

import java.util.Collection;

/**
 * JavaFX panel displaying all strategies registered in the {@link StrategyRegistry}.
 *
 * <p>Displays both developer plugin strategies ({@link StrategyType#USER_PLUGIN})
 * and no-code strategies ({@link StrategyType#NO_CODE}) in a unified table so
 * that portfolio managers and quant researchers can see the full strategy inventory.
 *
 * <p>This panel is READ-ONLY for execution. It shows strategy metadata and
 * pipeline status. No order submission logic exists here.</p>
 */
@Slf4j
public class StrategyRegistryPanel extends VBox {

    private final StrategyRegistry registry;
    private final TableView<StrategyDescriptor> table;
    private final ObservableList<StrategyDescriptor> items;
    private final Label statusLabel;

    /** Creates the panel wired to the global singleton registry. */
    public StrategyRegistryPanel() {
        this(StrategyRegistry.getInstance());
    }

    /** Creates the panel with a specific registry (useful for testing). */
    public StrategyRegistryPanel(StrategyRegistry registry) {
        this.registry = registry;
        this.items = FXCollections.observableArrayList();
        this.statusLabel = new Label("Ready");
        this.table = buildTable();
        buildLayout();
        refresh();
    }

    // =========================================================================
    // Layout
    // =========================================================================

    private void buildLayout() {
        setSpacing(10);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #1e1e2e;");

        Label title = new Label("📋 Strategy Registry");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

        HBox toolbar = buildToolbar();

        statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 12px;");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, toolbar, table, statusBar);
    }

    private HBox buildToolbar() {
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> refresh());

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All", "BUILT_IN", "USER_PLUGIN", "NO_CODE", "AI_GENERATED");
        typeFilter.setValue("All");
        typeFilter.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        typeFilter.setOnAction(e -> applyFilter(typeFilter.getValue()));

        Label filterLabel = new Label("Filter:");
        filterLabel.setStyle("-fx-text-fill: #cdd6f4;");

        HBox bar = new HBox(10, refreshBtn, filterLabel, typeFilter);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<StrategyDescriptor> buildTable() {
        TableView<StrategyDescriptor> tv = new TableView<>(items);
        tv.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPlaceholder(new Label("No strategies registered"));

        tv.getColumns().addAll(
            col("ID",          d -> new SimpleStringProperty(d.getStrategyId())),
            col("Name",        d -> new SimpleStringProperty(d.getName())),
            col("Type",        d -> new SimpleStringProperty(d.getStrategyType().name())),
            col("Status",      d -> new SimpleStringProperty(d.getValidationStatus().name())),
            col("Warmup Bars", d -> new SimpleStringProperty(String.valueOf(d.getWarmupBars()))),
            col("Live?",       d -> new SimpleStringProperty(d.isLiveAllowed() ? "✅ YES" : "🚫 NO")),
            col("Author",      d -> new SimpleStringProperty(d.getAuthor() != null ? d.getAuthor() : ""))
        );
        return tv;
    }

    // =========================================================================
    // Data binding
    // =========================================================================

    /** Refreshes the table from the registry. */
    public void refresh() {
        Collection<StrategyDescriptor> all = registry.getAllDescriptors();
        Platform.runLater(() -> {
            items.setAll(all);
            statusLabel.setText(all.size() + " strategy/strategies registered");
        });
        log.debug("StrategyRegistryPanel refreshed: {} strategies", all.size());
    }

    private void applyFilter(String type) {
        if ("All".equals(type)) {
            refresh();
            return;
        }
        Collection<StrategyDescriptor> all = registry.getAllDescriptors();
        Platform.runLater(() -> {
            ObservableList<StrategyDescriptor> filtered = FXCollections.observableArrayList();
            for (StrategyDescriptor d : all) {
                if (d.getStrategyType().name().equals(type)) {
                    filtered.add(d);
                }
            }
            items.setAll(filtered);
            statusLabel.setText("Showing " + filtered.size() + " " + type + " strategy/strategies");
        });
    }

    // =========================================================================
    // Table column helper
    // =========================================================================

    private TableColumn<StrategyDescriptor, String> col(
            String title,
            java.util.function.Function<StrategyDescriptor, SimpleStringProperty> valueFactory) {
        TableColumn<StrategyDescriptor, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cd -> valueFactory.apply(cd.getValue()));
        column.setStyle("-fx-text-fill: #cdd6f4;");
        return column;
    }
}
