package org.investpro.ui.docking;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dockable chart workspace module used by TradingDesk center region.
 */
public final class DockableChartPanel implements DockablePane {
    private final String id;
    private final String title;
    private final TabPane chartTabPane = new TabPane();
    private final BorderPane workspace = new BorderPane();

    private final Label chartHeaderSymbolLabel = new Label("No chart");
    private final Label chartHeaderTimeframeLabel = new Label("TF: -");
    private final Label chartHeaderQuoteLabel = new Label("Bid/Ask: -");
    private final Label chartHeaderLastLabel = new Label("Last: -");
    private final Label chartHeaderSpreadLabel = new Label("Spread: -");

    private final Runnable openChartAction;
    private final Runnable fitAction;
    private final Runnable refreshAction;
    private final Runnable crosshairAction;
    private final Runnable detachAction;
    private final Runnable closeAllAction;
    private final Consumer<Tab> tabSelectionAction;
    private final Runnable updateHeaderAction;

    private boolean initialized;

    public DockableChartPanel(
            String id,
            String title,
            Runnable openChartAction,
            Runnable fitAction,
            Runnable refreshAction,
            Runnable crosshairAction,
            Runnable detachAction,
            Runnable closeAllAction,
            Consumer<Tab> tabSelectionAction,
            Runnable updateHeaderAction) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.openChartAction = openChartAction;
        this.fitAction = fitAction;
        this.refreshAction = refreshAction;
        this.crosshairAction = crosshairAction;
        this.detachAction = detachAction;
        this.closeAllAction = closeAllAction;
        this.tabSelectionAction = tabSelectionAction;
        this.updateHeaderAction = updateHeaderAction;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Node getView() {
        ensureInitialized();
        return workspace;
    }

    public TabPane getChartTabPane() {
        ensureInitialized();
        return chartTabPane;
    }

    public void setTimeframeLabel(String timeframeText) {
        chartHeaderTimeframeLabel.setText(timeframeText == null ? "TF: -" : timeframeText);
    }

    public void updateHeaderValues(String symbol, String timeframe, String quote, String last, String spread) {
        chartHeaderSymbolLabel.setText(symbol == null ? "No chart" : symbol);
        chartHeaderTimeframeLabel.setText(timeframe == null ? "TF: -" : timeframe);
        chartHeaderQuoteLabel.setText(quote == null ? "Bid/Ask: -" : quote);
        chartHeaderLastLabel.setText(last == null ? "Last: -" : last);
        chartHeaderSpreadLabel.setText(spread == null ? "Spread: -" : spread);
    }

    @Override
    public Map<String, String> saveState() {
        Tab selected = chartTabPane.getSelectionModel().getSelectedItem();
        return selected == null ? Map.of() : Map.of("selectedTab", safeTabIdentity(selected));
    }

    @Override
    public void restoreState(Map<String, String> state) {
        if (state == null || state.isEmpty()) {
            return;
        }
        String tabIdentity = state.get("selectedTab");
        if (tabIdentity == null || tabIdentity.isBlank()) {
            return;
        }
        for (Tab tab : chartTabPane.getTabs()) {
            if (tabIdentity.equals(safeTabIdentity(tab))) {
                chartTabPane.getSelectionModel().select(tab);
                break;
            }
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        workspace.getStyleClass().addAll("chart-workspace", "mt5-chart-workspace");
        workspace.setTop(createHeader());

        chartTabPane.setSide(javafx.geometry.Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        chartTabPane.getStyleClass().addAll("chart-tabs", "mt5-chart-tabs");

        VBox emptyState = createEmptyState();
        emptyState.setVisible(chartTabPane.getTabs().isEmpty());
        emptyState.setManaged(chartTabPane.getTabs().isEmpty());

        chartTabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            boolean empty = chartTabPane.getTabs().isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
            if (updateHeaderAction != null) {
                updateHeaderAction.run();
            }
        });

        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && !Objects.equals(oldTab, newTab) && tabSelectionAction != null) {
                tabSelectionAction.accept(newTab);
            }
            if (updateHeaderAction != null) {
                updateHeaderAction.run();
            }
        });

        StackPane chartSurface = new StackPane(chartTabPane, emptyState);
        chartSurface.getStyleClass().add("mt5-chart-surface");
        workspace.setCenter(chartSurface);
    }

    private HBox createHeader() {
        chartHeaderSymbolLabel.getStyleClass().add("mt5-chart-symbol");
        chartHeaderTimeframeLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderQuoteLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderLastLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderSpreadLabel.getStyleClass().add("mt5-chart-meta");

        Button fitButton = toolbarButton("Fit", fitAction, "Fit active chart");
        Button refreshButton = toolbarButton("Refresh", refreshAction, "Refresh active chart");
        Button crosshairButton = toolbarButton("Crosshair", crosshairAction, "Toggle active chart crosshair");
        Button detachButton = toolbarButton("Detach", detachAction, "Detach active chart tab");
        Button closeAllButton = toolbarButton("Close All", closeAllAction, "Close all chart tabs");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10,
                chartHeaderSymbolLabel,
                chartHeaderTimeframeLabel,
                chartHeaderQuoteLabel,
                chartHeaderLastLabel,
                chartHeaderSpreadLabel,
                spacer,
                fitButton,
                refreshButton,
                crosshairButton,
                detachButton,
                closeAllButton);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("mt5-chart-header");
        return header;
    }

    private VBox createEmptyState() {
        Label titleLabel = new Label("Open a symbol from Market Watch or press Ctrl+N.");
        titleLabel.getStyleClass().add("mt5-empty-title");
        Label subtitle = new Label("Charts, strategy overlays, and live trade updates appear here.");
        subtitle.getStyleClass().add("mt5-empty-subtitle");

        Button openButton = toolbarButton("Open Chart", openChartAction, "Open selected symbol chart");
        openButton.getStyleClass().add("mt5-toolbar-button");

        VBox empty = new VBox(10, titleLabel, subtitle, openButton);
        empty.setAlignment(javafx.geometry.Pos.CENTER);
        empty.getStyleClass().add("mt5-chart-empty-state");
        return empty;
    }

    private Button toolbarButton(String text, Runnable action, String tooltipText) {
        Button button = new Button(text);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltipText));
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private String safeTabIdentity(Tab tab) {
        String idValue = tab.getId();
        if (idValue != null && !idValue.isBlank()) {
            return idValue;
        }
        String text = tab.getText();
        return text == null ? "" : text;
    }
}
