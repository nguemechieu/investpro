package org.investpro.ui.menu;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ui.TradingDesk;

import static org.investpro.i18n.LocalizationService.t;

/**
 * System-wide menu bar providing access to all windows, panels, and system
 * features.
 * Organizes functionality into logical categories: File, View, Analysis,
 * Strategy, Tools, Help.
 */
@Slf4j
public class SystemMenuBar extends MenuBar {
    private final TradingDesk tradingDesk;

    public SystemMenuBar(TradingDesk tradingDesk) {
        this.tradingDesk = tradingDesk;

        this.getMenus().addAll(
                createFileMenu(),
                createViewMenu(),
                createAnalysisMenu(),
                createStrategyMenu(),
                createToolsMenu(),
                createHelpMenu());

        this.setStyle("-fx-font-size: 11;");
    }

    // ========== FILE MENU ==========
    private Menu createFileMenu() {
        Menu fileMenu = new Menu(t("menu.file", "File"));

        MenuItem connect = new MenuItem(t("menu.connect", "Connect Exchange"));
        connect.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        connect.setOnAction(e -> tradingDesk.getConnectButton().fire());

        MenuItem disconnect = new MenuItem(t("menu.disconnect", "Disconnect"));
        disconnect.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));
        disconnect.setOnAction(e -> {
            // Implement disconnect logic
            log.info("Disconnect requested from menu");
        });

        fileMenu.getItems().addAll(
                connect,
                disconnect,
                new SeparatorMenuItem(),
                createExportSubmenu(),
                new SeparatorMenuItem(),
                createExitMenuItem());

        return fileMenu;
    }

    private Menu createExportSubmenu() {
        Menu exportMenu = new Menu(t("menu.export", "Export"));

        MenuItem exportTrades = new MenuItem(t("menu.exportTrades", "Export Trades"));
        exportTrades.setOnAction(e -> log.info("Export trades requested"));

        MenuItem exportSettings = new MenuItem(t("menu.exportSettings", "Export Settings"));
        exportSettings.setOnAction(e -> log.info("Export settings requested"));

        MenuItem exportReport = new MenuItem(t("menu.exportReport", "Export Report"));
        exportReport.setOnAction(e -> log.info("Export report requested"));

        exportMenu.getItems().addAll(exportTrades, exportSettings, exportReport);
        return exportMenu;
    }

    private MenuItem createExitMenuItem() {
        MenuItem exit = new MenuItem(t("menu.exit", "Exit"));
        exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exit.setOnAction(e -> Platform.exit());
        return exit;
    }

    // ========== VIEW MENU ==========
    private Menu createViewMenu() {
        Menu viewMenu = new Menu(t("menu.view", "View"));

        // Toggle panels visibility
        CheckMenuItem showConsole = new CheckMenuItem(t("menu.showConsole", "Show Console"));
        showConsole.setSelected(true);
        showConsole.setOnAction(e -> {
            // Toggle console visibility
            log.info("Console visibility toggled");
        });

        CheckMenuItem showMarketWatch = new CheckMenuItem(t("menu.showMarketWatch", "Show Market Watch"));
        showMarketWatch.setSelected(true);
        showMarketWatch.setOnAction(e -> {
            // Toggle market watch visibility
            log.info("Market Watch visibility toggled");
        });

        CheckMenuItem showOrderBook = new CheckMenuItem(t("menu.showOrderBook", "Show Order Book"));
        showOrderBook.setSelected(true);
        showOrderBook.setOnAction(e -> {
            // Toggle order book visibility
            log.info("Order Book visibility toggled");
        });

        viewMenu.getItems().addAll(
                showConsole,
                showMarketWatch,
                showOrderBook,
                new SeparatorMenuItem(),
                createWindowsSubmenu(),
                new SeparatorMenuItem(),
                createThemeSubmenu());

        return viewMenu;
    }

    private Menu createWindowsSubmenu() {
        Menu windowsMenu = new Menu(t("menu.windows", "Windows"));

        MenuItem marketInfo = new MenuItem(t("menu.marketInfo", "Market Info"));
        marketInfo.setOnAction(e -> log.info("Market Info window requested"));

        MenuItem newsCalendar = new MenuItem(t("menu.newsCalendar", "News Calendar"));
        newsCalendar.setOnAction(e -> log.info("News Calendar window requested"));

        MenuItem systemMonitor = new MenuItem(t("menu.systemMonitor", "System Monitor"));
        systemMonitor.setOnAction(e -> log.info("System Monitor window requested"));

        MenuItem dataWindow = new MenuItem(t("menu.dataWindow", "Data Window"));
        dataWindow.setOnAction(e -> log.info("Data Window requested"));

        MenuItem navigation = new MenuItem(t("menu.navigation", "Navigation"));
        navigation.setOnAction(e -> log.info("Navigation window requested"));

        windowsMenu.getItems().addAll(
                marketInfo,
                newsCalendar,
                systemMonitor,
                dataWindow,
                navigation);

        return windowsMenu;
    }

    private Menu createThemeSubmenu() {
        Menu themeMenu = new Menu(t("menu.theme", "Theme"));

        ToggleGroup themeGroup = new ToggleGroup();

        RadioMenuItem lightTheme = new RadioMenuItem("Light");
        lightTheme.setToggleGroup(themeGroup);
        lightTheme.setSelected(true);
        lightTheme.setOnAction(e -> log.info("Light theme selected"));

        RadioMenuItem darkTheme = new RadioMenuItem("Dark");
        darkTheme.setToggleGroup(themeGroup);
        darkTheme.setOnAction(e -> log.info("Dark theme selected"));

        themeMenu.getItems().addAll(lightTheme, darkTheme);
        return themeMenu;
    }

    // ========== ANALYSIS MENU ==========
    private Menu createAnalysisMenu() {
        Menu analysisMenu = new Menu(t("menu.analysis", "Analysis"));

        MenuItem technicalAnalysis = new MenuItem(t("menu.technicalAnalysis", "Technical Analysis Panel"));
        technicalAnalysis.setOnAction(e -> log.info("Technical Analysis panel requested"));

        MenuItem fundamentalAnalysis = new MenuItem(t("menu.fundamentalAnalysis", "Fundamental Analysis"));
        fundamentalAnalysis.setOnAction(e -> log.info("Fundamental Analysis requested"));

        MenuItem riskAnalysis = new MenuItem(t("menu.riskAnalysis", "Risk Analysis"));
        riskAnalysis.setOnAction(e -> log.info("Risk Analysis requested"));

        analysisMenu.getItems().addAll(
                technicalAnalysis,
                fundamentalAnalysis,
                riskAnalysis,
                new SeparatorMenuItem(),
                createBacktestSubmenu());

        return analysisMenu;
    }

    private Menu createBacktestSubmenu() {
        Menu backtestMenu = new Menu(t("menu.backtest", "Backtesting"));

        MenuItem startBacktest = new MenuItem(t("menu.startBacktest", "Start Backtest"));
        startBacktest.setOnAction(e -> log.info("Start Backtest requested"));

        MenuItem backtestReport = new MenuItem(t("menu.backtestReport", "Backtest Report"));
        backtestReport.setOnAction(e -> log.info("Backtest Report requested"));

        backtestMenu.getItems().addAll(startBacktest, backtestReport);
        return backtestMenu;
    }

    // ========== STRATEGY MENU ==========
    private Menu createStrategyMenu() {
        Menu strategyMenu = new Menu(t("menu.strategy", "Strategy"));

        MenuItem strategyLab = new MenuItem(t("menu.strategyLab", "Strategy Lab"));
        strategyLab.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        strategyLab.setOnAction(e -> log.info("Strategy Lab requested"));

        MenuItem strategyDeveloper = new MenuItem(t("menu.strategyDeveloper", "Strategy Developer"));
        strategyDeveloper.setOnAction(e -> log.info("Strategy Developer requested"));

        MenuItem strategyBuilder = new MenuItem(t("menu.strategyBuilder", "Strategy Builder"));
        strategyBuilder.setOnAction(e -> log.info("Strategy Builder requested"));

        MenuItem strategyAssignment = new MenuItem(t("menu.strategyAssignment", "Strategy Assignment"));
        strategyAssignment.setOnAction(e -> log.info("Strategy Assignment requested"));

        MenuItem viewCatalog = new MenuItem(t("menu.viewCatalog", "View Strategy Catalog"));
        viewCatalog.setOnAction(e -> log.info("Strategy Catalog requested"));

        strategyMenu.getItems().addAll(
                strategyLab,
                strategyDeveloper,
                strategyBuilder,
                strategyAssignment,
                new SeparatorMenuItem(),
                viewCatalog);

        return strategyMenu;
    }

    // ========== TOOLS MENU ==========
    private Menu createToolsMenu() {
        Menu toolsMenu = new Menu(t("menu.tools", "Tools"));

        MenuItem settings = new MenuItem(t("menu.settings", "Settings"));
        settings.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
        settings.setOnAction(e -> {
            // Delegate to TradingDesk to show trading profile settings
            log.info("Settings requested");
        });

        MenuItem tradingSystemStatus = new MenuItem(t("menu.tradingSystemStatus", "Trading System Status"));
        tradingSystemStatus.setOnAction(e -> log.info("Trading System Status requested"));

        MenuItem diagnostics = new MenuItem(t("menu.diagnostics", "Diagnostics & Health Check"));
        diagnostics.setOnAction(e -> log.info("Diagnostics requested"));

        MenuItem orderManagement = new MenuItem(t("menu.orderManagement", "Order Management"));
        orderManagement.setOnAction(e -> log.info("Order Management requested"));

        toolsMenu.getItems().addAll(
                settings,
                new SeparatorMenuItem(),
                tradingSystemStatus,
                diagnostics,
                orderManagement,
                new SeparatorMenuItem(),
                createUtilsSubmenu());

        return toolsMenu;
    }

    private Menu createUtilsSubmenu() {
        Menu utilsMenu = new Menu(t("menu.utilities", "Utilities"));

        MenuItem clearCache = new MenuItem(t("menu.clearCache", "Clear Cache"));
        clearCache.setOnAction(e -> log.info("Clear Cache requested"));

        MenuItem resetLayout = new MenuItem(t("menu.resetLayout", "Reset Layout"));
        resetLayout.setOnAction(e -> log.info("Reset Layout requested"));

        MenuItem viewLogs = new MenuItem(t("menu.viewLogs", "View Logs"));
        viewLogs.setOnAction(e -> log.info("View Logs requested"));

        utilsMenu.getItems().addAll(clearCache, resetLayout, viewLogs);
        return utilsMenu;
    }

    // ========== HELP MENU ==========
    private Menu createHelpMenu() {
        Menu helpMenu = new Menu(t("menu.help", "Help"));

        MenuItem documentation = new MenuItem(t("menu.documentation", "Documentation"));
        documentation.setAccelerator(new KeyCodeCombination(KeyCode.F1));
        documentation.setOnAction(e -> log.info("Documentation requested"));

        MenuItem userGuide = new MenuItem(t("menu.userGuide", "User Guide"));
        userGuide.setOnAction(e -> log.info("User Guide requested"));

        MenuItem apiReference = new MenuItem(t("menu.apiReference", "API Reference"));
        apiReference.setOnAction(e -> log.info("API Reference requested"));

        MenuItem shortcuts = new MenuItem(t("menu.shortcuts", "Keyboard Shortcuts"));
        shortcuts.setAccelerator(new KeyCodeCombination(KeyCode.SLASH, KeyCombination.CONTROL_DOWN));
        shortcuts.setOnAction(e -> showKeyboardShortcutsDialog());

        MenuItem about = new MenuItem(t("menu.about", "About InvestPro"));
        about.setOnAction(e -> log.info("About dialog requested"));

        helpMenu.getItems().addAll(
                documentation,
                userGuide,
                apiReference,
                new SeparatorMenuItem(),
                shortcuts,
                new SeparatorMenuItem(),
                about);

        return helpMenu;
    }

    private void showKeyboardShortcutsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(t("menu.shortcuts", "Keyboard Shortcuts"));
        alert.setHeaderText(t("menu.shortcuts", "Keyboard Shortcuts"));

        StringBuilder shortcuts = new StringBuilder();
        shortcuts.append("Connect Exchange: Ctrl+C\n");
        shortcuts.append("Disconnect: Ctrl+D\n");
        shortcuts.append("Exit: Ctrl+Q\n");
        shortcuts.append("Settings: Ctrl+,\n");
        shortcuts.append("Strategy Lab: Ctrl+L\n");
        shortcuts.append("Help: F1\n");
        shortcuts.append("Shortcuts: Ctrl+/\n");

        alert.setContentText(shortcuts.toString());
        alert.showAndWait();
    }
}
