package org.investpro.ui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ui.TradingDesk;

import java.util.*;
import java.util.function.Consumer;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Central registry and builder for all UI panels and windows in the system.
 * Provides organized access to 15+ panel classes with keyboard shortcuts and
 * menu organization.
 */
@Slf4j
public class PanelRegistry {

    private final TradingDesk tradingDesk;
    private final Map<String, PanelDescriptor> panels = new LinkedHashMap<>();

    public PanelRegistry(TradingDesk tradingDesk) {
        this.tradingDesk = tradingDesk;
        registerAllPanels();
    }

    /**
     * Register all available panels with their descriptors, shortcuts, and actions.
     */
    private void registerAllPanels() {
        // === STRATEGY PANELS (5 total) ===
        registerPanel(
                "strategy-lab",
                "Strategy Lab",
                "org.investpro.ui.panels.StrategyLabPanel",
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                "Develop and test trading strategies in an isolated environment");

        registerPanel(
                "strategy-developer",
                "Strategy Developer",
                "org.investpro.ui.panels.StrategyDeveloperPanel",
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                "Advanced strategy development with live code editing");

        registerPanel(
                "strategy-builder",
                "Strategy Builder",
                "org.investpro.ui.panels.StrategyBuilderPanel",
                new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                "Visual strategy builder with drag-and-drop composition");

        registerPanel(
                "strategy-assignment",
                "Strategy Assignment",
                "org.investpro.ui.panels.StrategyAssignmentPanel",
                new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                "Assign strategies to symbols and manage active assignments");

        registerPanel(
                "trading-system-status",
                "Trading System Status",
                "org.investpro.ui.panels.TradingSystemStatusPanel",
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN),
                "Real-time system health, performance metrics, and status indicators");

        // === TRADING & ORDER PANELS (1 total) ===
        registerPanel(
                "order-panel",
                "Order Management",
                "org.investpro.ui.panels.OrderPanel",
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                "Place, monitor, and manage orders across exchanges");

        // === MARKET & DATA PANELS (3 total) ===
        registerPanel(
                "market-watch",
                "Market Watch",
                "org.investpro.ui.panels.MarketWatchPanel",
                new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN),
                "Monitor multiple symbols with real-time price and volume data");

        registerPanel(
                "market-info",
                "Market Info",
                "org.investpro.ui.panels.MarketInfoPanel",
                new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                "Detailed market information, trading volumes, and exchange data");

        registerPanel(
                "news-calendar",
                "News Calendar",
                "org.investpro.ui.panels.NewsCalendarPanel",
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                "Economic calendar with news events and market impact indicators");

        // === ANALYSIS PANELS (4 total) ===
        registerPanel(
                "technical-indicators",
                "Technical Indicators",
                "org.investpro.ui.panels.TechnicalIndicatorsPanel",
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                "Access and configure technical analysis indicators");

        registerPanel(
                "volume-indicator",
                "Volume Indicator",
                "org.investpro.ui.panels.VolumeIndicatorPanel",
                new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN),
                "Advanced volume analysis with on-balance volume and accumulation");

        registerPanel(
                "analysis",
                "Analysis Panel",
                "org.investpro.ui.panels.AnalysisPanel",
                new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                "Comprehensive technical and fundamental analysis tools");

        registerPanel(
                "backtest-report",
                "Backtest Report",
                "org.investpro.ui.panels.BacktestReportPanel",
                null,
                "View and analyze detailed backtest results and performance metrics");

        // === BACKTESTING PANEL (1 total) ===
        registerPanel(
                "backtesting",
                "Backtesting",
                "org.investpro.ui.panels.BacktestingPanel",
                new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                "Run historical simulations and validate trading strategies");

        // === SETTINGS PANEL (1 total) ===
        registerPanel(
                "settings",
                "Settings",
                "org.investpro.ui.panels.SettingsPanel",
                new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN),
                "Application configuration, preferences, and account settings");
    }

    /**
     * Register a panel with its metadata and keyboard shortcut.
     */
    private void registerPanel(String id, String name, String className,
            KeyCodeCombination shortcut, String description) {
        PanelDescriptor descriptor = new PanelDescriptor(
                id, name, className, shortcut, description);

        panels.put(id, descriptor);

        if (shortcut != null) {
            log.debug("Registered panel: {} with shortcut: {}", name, shortcut);
        }
    }

    /**
     * Get panel descriptor by ID.
     */
    public PanelDescriptor getPanel(String id) {
        return panels.get(id);
    }

    /**
     * Get all registered panels.
     */
    public Collection<PanelDescriptor> getAllPanels() {
        return Collections.unmodifiableCollection(panels.values());
    }

    /**
     * Get panels grouped by category for menu organization.
     */
    public Map<String, List<PanelDescriptor>> getPanelsByCategory() {
        Map<String, List<PanelDescriptor>> grouped = new LinkedHashMap<>();

        List<PanelDescriptor> strategyPanels = new ArrayList<>();
        List<PanelDescriptor> tradingPanels = new ArrayList<>();
        List<PanelDescriptor> marketPanels = new ArrayList<>();
        List<PanelDescriptor> analysisPanels = new ArrayList<>();
        List<PanelDescriptor> settingsPanels = new ArrayList<>();

        for (PanelDescriptor panel : panels.values()) {
            if (panel.id.contains("strategy")) {
                strategyPanels.add(panel);
            } else if (panel.id.contains("order") || panel.id.contains("trading")) {
                tradingPanels.add(panel);
            } else if (panel.id.contains("market") || panel.id.contains("watch") || panel.id.contains("news")) {
                marketPanels.add(panel);
            } else if (panel.id.contains("analysis") || panel.id.contains("backtest") || panel.id.contains("indicator")
                    || panel.id.contains("volume")) {
                analysisPanels.add(panel);
            } else if (panel.id.contains("settings")) {
                settingsPanels.add(panel);
            }
        }

        if (!strategyPanels.isEmpty())
            grouped.put("Strategy Development", strategyPanels);
        if (!tradingPanels.isEmpty())
            grouped.put("Trading & Orders", tradingPanels);
        if (!marketPanels.isEmpty())
            grouped.put("Market Data", marketPanels);
        if (!analysisPanels.isEmpty())
            grouped.put("Analysis & Backtesting", analysisPanels);
        if (!settingsPanels.isEmpty())
            grouped.put("Settings", settingsPanels);

        return grouped;
    }

    /**
     * Create a menu item for opening a panel.
     */
    public MenuItem createPanelMenuItem(String panelId) {
        PanelDescriptor panel = getPanel(panelId);
        if (panel == null) {
            log.warn("Panel not found: {}", panelId);
            return null;
        }

        MenuItem item = new MenuItem(panel.name);
        if (panel.shortcut != null) {
            item.setAccelerator(panel.shortcut);
        }

        item.setOnAction(e -> openPanel(panelId));
        return item;
    }

    /**
     * Open a panel by ID.
     */
    public void openPanel(String panelId) {
        PanelDescriptor panel = getPanel(panelId);
        if (panel == null) {
            log.warn("Cannot open panel: {} (not registered)", panelId);
            return;
        }

        try {
            log.info("Opening panel: {}", panel.name);
            // Panel opening will be implemented when TradingDesk methods are created
            // For now, just log the request
        } catch (Exception ex) {
            log.error("Error opening panel: {}", panelId, ex);
        }
    }

    /**
     * Descriptor for a single UI panel.
     */
    @Data
    @AllArgsConstructor
    public static class PanelDescriptor {
        private String id;
        private String name;
        private String className;
        private KeyCodeCombination shortcut;
        private String description;
    }
}
