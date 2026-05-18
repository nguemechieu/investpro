package org.investpro.ui.menu;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ui.TradingDesk;

import java.util.List;
import java.util.Map;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Builder for comprehensive system menu bar with all panels and features
 * organized.
 * Works with PanelRegistry to provide complete menu system for all UI
 * components.
 */
@Slf4j
public class EnhancedMenuBuilder {

    private final TradingDesk tradingDesk;
    private final PanelRegistry panelRegistry;

    public EnhancedMenuBuilder(TradingDesk tradingDesk, PanelRegistry panelRegistry) {
        this.tradingDesk = tradingDesk;
        this.panelRegistry = panelRegistry;
    }

    /**
     * Build the complete menu bar.
     */
    public MenuBar buildMenuBar() {
        return new MenuBar(
                buildFileMenu(),
                buildViewMenu(),
                buildPanelsMenu(),
                buildStrategyMenu(),
                buildAnalysisMenu(),
                buildToolsMenu(),
                buildHelpMenu());
    }

    // ========== FILE MENU ==========
    private Menu buildFileMenu() {
        Menu fileMenu = new Menu(t("menu.file", "File"));

        MenuItem connect = createMenuItem(t("menu.connect", "Connect Exchange"),
                KeyCode.C, KeyCombination.CONTROL_DOWN,
                () -> tradingDesk.getConnectButton().fire());

        MenuItem exit = createMenuItem(t("menu.exit", "Exit"),
                KeyCode.Q, KeyCombination.CONTROL_DOWN,
                () -> {
                    tradingDesk.shutdown();
                    Platform.exit();
                });

        fileMenu.getItems().addAll(
                connect,
                new SeparatorMenuItem(),
                exit);

        return fileMenu;
    }

    // ========== VIEW MENU ==========
    private Menu buildViewMenu() {
        Menu viewMenu = new Menu(t("menu.view", "View"));

        CheckMenuItem showConsole = new CheckMenuItem(t("menu.toggleTerminal", "Show Console"));
        showConsole.setSelected(true);
        showConsole.setAccelerator(new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN));
        showConsole.setOnAction(e -> log.info("Toggle console"));

        viewMenu.getItems().addAll(showConsole);

        return viewMenu;
    }

    // ========== PANELS MENU ==========
    private Menu buildPanelsMenu() {
        Menu panelsMenu = new Menu("Panels");

        Map<String, List<PanelRegistry.PanelDescriptor>> panelsByCategory = panelRegistry.getPanelsByCategory();

        for (Map.Entry<String, List<PanelRegistry.PanelDescriptor>> entry : panelsByCategory.entrySet()) {
            Menu categoryMenu = new Menu(entry.getKey());

            for (PanelRegistry.PanelDescriptor panel : entry.getValue()) {
                MenuItem item = new MenuItem(panel.getName());
                if (panel.getShortcut() != null) {
                    item.setAccelerator(panel.getShortcut());
                }
                item.setOnAction(e -> panelRegistry.openPanel(panel.getId()));
                categoryMenu.getItems().add(item);
            }

            panelsMenu.getItems().add(categoryMenu);
        }

        return panelsMenu;
    }

    // ========== STRATEGY MENU ==========
    private Menu buildStrategyMenu() {
        Menu strategyMenu = new Menu(t("menu.strategy", "Strategy"));

        MenuItem strategyLab = panelRegistry.createPanelMenuItem("strategy-lab");
        MenuItem strategyDeveloper = panelRegistry.createPanelMenuItem("strategy-developer");
        MenuItem strategyBuilder = panelRegistry.createPanelMenuItem("strategy-builder");
        MenuItem strategyAssignment = panelRegistry.createPanelMenuItem("strategy-assignment");

        addIfPresent(strategyMenu, strategyLab, strategyDeveloper, strategyBuilder, strategyAssignment);

        return strategyMenu;
    }

    // ========== ANALYSIS MENU ==========
    private Menu buildAnalysisMenu() {
        Menu analysisMenu = new Menu(t("menu.analysis", "Analysis"));

        MenuItem technicalIndicators = panelRegistry.createPanelMenuItem("technical-indicators");
        MenuItem volumeIndicator = panelRegistry.createPanelMenuItem("volume-indicator");
        MenuItem analysisPanel = panelRegistry.createPanelMenuItem("analysis");
        MenuItem backtesting = panelRegistry.createPanelMenuItem("backtesting");
        MenuItem backtestReport = panelRegistry.createPanelMenuItem("backtest-report");

        addIfPresent(analysisMenu, technicalIndicators, volumeIndicator, analysisPanel);
        analysisMenu.getItems().add(new SeparatorMenuItem());
        addIfPresent(analysisMenu, backtesting, backtestReport);

        return analysisMenu;
    }

    // ========== TOOLS MENU ==========
    private Menu buildToolsMenu() {
        Menu toolsMenu = new Menu(t("menu.tools", "Tools"));

        MenuItem settings = panelRegistry.createPanelMenuItem("settings");
        MenuItem tradingSystemStatus = panelRegistry.createPanelMenuItem("trading-system-status");
        MenuItem orderPanel = panelRegistry.createPanelMenuItem("order-panel");

        addIfPresent(toolsMenu, settings);
        toolsMenu.getItems().add(new SeparatorMenuItem());
        addIfPresent(toolsMenu, tradingSystemStatus, orderPanel);

        return toolsMenu;
    }

    // ========== HELP MENU ==========
    private Menu buildHelpMenu() {
        Menu helpMenu = new Menu(t("menu.help", "Help"));

        MenuItem documentation = createMenuItem("Documentation",
                KeyCode.F1, null,
                () -> log.info("Documentation requested"));

        MenuItem shortcuts = createMenuItem("Keyboard Shortcuts",
                KeyCode.SLASH, KeyCombination.CONTROL_DOWN,
                this::showKeyboardShortcuts);

        helpMenu.getItems().addAll(
                documentation,
                new SeparatorMenuItem(),
                shortcuts);

        return helpMenu;
    }

    // ========== HELPER METHODS ==========
    private MenuItem createMenuItem(String text, KeyCode keyCode, KeyCombination.Modifier modifier, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (keyCode != null && modifier != null) {
            item.setAccelerator(new KeyCodeCombination(keyCode, modifier));
        }
        item.setOnAction(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                log.error("Error executing menu action: {}", text, ex);
            }
        });
        return item;
    }

    private void addIfPresent(Menu menu, MenuItem... items) {
        for (MenuItem item : items) {
            if (item != null) {
                menu.getItems().add(item);
            }
        }
    }

    private void showKeyboardShortcuts() {
        StringBuilder shortcuts = new StringBuilder();
        shortcuts.append("=== STRATEGY ===\n");
        shortcuts.append("Strategy Lab: Ctrl+L\n");
        shortcuts.append("Strategy Developer: Ctrl+Shift+D\n");
        shortcuts.append("Strategy Builder: Ctrl+Shift+B\n");
        shortcuts.append("Strategy Assignment: Ctrl+Shift+A\n");
        shortcuts.append("\n=== TRADING ===\n");
        shortcuts.append("Order Panel: Ctrl+O\n");
        shortcuts.append("Trading System Status: Ctrl+Alt+T\n");
        shortcuts.append("\n=== ANALYSIS ===\n");
        shortcuts.append("Technical Indicators: Ctrl+T\n");
        shortcuts.append("Volume Indicator: Ctrl+V\n");
        shortcuts.append("Backtesting: Ctrl+B\n");
        shortcuts.append("\n=== MARKET DATA ===\n");
        shortcuts.append("Market Watch: Ctrl+M\n");
        shortcuts.append("Market Info: Ctrl+I\n");
        shortcuts.append("News Calendar: Ctrl+N\n");
        shortcuts.append("\n=== APPLICATION ===\n");
        shortcuts.append("Settings: Ctrl+,\n");
        shortcuts.append("Exit: Ctrl+Q\n");
        shortcuts.append("Help: F1\n");
        shortcuts.append("Shortcuts: Ctrl+/\n");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("InvestPro Keyboard Shortcuts");

        TextArea textArea = new TextArea(shortcuts.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }
}
