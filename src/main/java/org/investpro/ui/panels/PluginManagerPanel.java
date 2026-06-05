package org.investpro.ui.panels;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.investpro.spi.InvestProPlugin;
import org.investpro.spi.PluginRegistry;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.investpro.strategy.StrategyCatalog;

import java.util.Collection;
import java.util.LinkedHashMap;


public class PluginManagerPanel extends BorderPane {

    private final PluginRegistry pluginRegistry;

    public PluginManagerPanel() {
        this(PluginRegistry.loadDefault());
    }

    public PluginManagerPanel(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry == null ? PluginRegistry.loadDefault() : pluginRegistry;
        initializeUI();
    }

    private void initializeUI() {
        getStyleClass().add("plugin-manager-panel");
        setPadding(new Insets(12));

        Label title = new Label("Plugin Manager");
        title.getStyleClass().add("panel-title");

        Label subtitle = new Label("ServiceLoader providers discovered at runtime");
        subtitle.getStyleClass().add("panel-meta");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("compact-tabs");
        tabs.getTabs().setAll(
                createTab("Exchanges", exchangeRows()),
                createTab("Strategies", strategyRows()),
                createTab("Indicators", indicatorRows()),
                createTab("Risk Modules", riskModuleRows()),
                createTab("Market Data", marketDataRows()));

        VBox root = new VBox(8, title, subtitle, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getStyleClass().add("pro-panel");
        root.setPadding(new Insets(10));
        setCenter(root);
    }

    private @NonNull Tab createTab(String title, Collection<PluginRow> rows) {
        TableView<PluginRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No providers loaded"));
        table.getColumns().add(column("ID", PluginRow::id, 150));
        table.getColumns().add(column("Display Name", PluginRow::displayName, 220));
        table.getColumns().add(column("Version", PluginRow::version, 80));
        table.getColumns().add(column("Enabled", PluginRow::enabledByDefault, 80));
        table.getColumns().add(column("Type / Category", PluginRow::type, 150));
        table.getColumns().add(column("Aliases / Supports", PluginRow::details, 280));

        Tab tab = new Tab(title, table);
        tab.setClosable(false);
        return tab;
    }

    private TableColumn<PluginRow, String> column(String title, java.util.function.Function<PluginRow, String> mapper, int width) {
        TableColumn<PluginRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(mapper.apply(cell.getValue())));
        return column;
    }

    private Collection<PluginRow> exchangeRows() {
        return pluginRegistry.exchangeProviders().stream()
                .map(provider -> row(
                        provider,
                        "Exchange",
                        String.join(", ", provider.aliases())))
                .toList();
    }

    private Collection<PluginRow> strategyRows() {
        LinkedHashMap<String, PluginRow> rows = new LinkedHashMap<>();

        pluginRegistry.strategyProviders().forEach(provider -> rows.putIfAbsent(
                provider.id(),
                row(
                        provider,
                        provider.category(),
                        String.join(", ", provider.supportedMarketTypes()))));

        for (String strategyName : StrategyCatalog.availableStrategyNames()) {
            String normalized = StrategyCatalog.normalizeStrategyName(strategyName);
            rows.putIfAbsent(normalized, new PluginRow(
                    normalized,
                    strategyName,
                    "catalog",
                    "true",
                    StrategyCatalog.resolveBaseStrategyName(strategyName),
                    "Built-in strategy catalog entry"));
        }

        return rows.values();
    }

    private Collection<PluginRow> indicatorRows() {
        return pluginRegistry.indicatorProviders().stream()
                .map(provider -> row(
                        provider,
                        provider.indicatorName(),
                        String.join(", ", provider.supportedInputs())))
                .toList();
    }

    private Collection<PluginRow> riskModuleRows() {
        return pluginRegistry.riskModuleProviders().stream()
                .map(provider -> row(provider, "Risk Module", ""))
                .toList();
    }

    private Collection<PluginRow> marketDataRows() {
        return pluginRegistry.marketDataProviders().stream()
                .map(provider -> row(
                        provider,
                        "Market Data",
                        String.join(", ", provider.supportedAssetClasses())))
                .toList();
    }

    @Contract("_, _, _ -> new")
    private @NonNull PluginRow row(@NonNull InvestProPlugin plugin, String type, String details) {
        return new PluginRow(
                plugin.id(),
                plugin.displayName(),
                plugin.version(),
                String.valueOf(plugin.enabledByDefault()),
                type == null ? "" : type,
                details == null ? "" : details);
    }

    private record PluginRow(
            String id,
            String displayName,
            String version,
            String enabledByDefault,
            String type,
            String details) {
    }
}
