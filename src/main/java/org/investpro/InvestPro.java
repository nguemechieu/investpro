package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.persistence.repository.CurrencyRepository;
import org.investpro.persistence.repository.OrderRepository;
import org.investpro.persistence.repository.RepositoryFactory;
import org.investpro.persistence.repository.TradeRepository;
import org.investpro.strategy.StrategyBootstrapper;
import org.investpro.ui.MarketConfiguration;
import org.investpro.ui.OnboardingView;
import org.investpro.ui.TradingDesk;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InvestPro - Professional Multi-Exchange Trading Terminal.
 * <p>
 * Main JavaFX entry point.
 * <p>
 * Responsibilities:
 * - start JavaFX application
 * - load app resources
 * - initialize strategy bootstrapper
 * - initialize repositories
 * - show onboarding
 * - open trading terminal
 * - shutdown terminal cleanly
 *
 * This class should NOT:
 * - own broker logic
 * - own strategy logic
 * - own execution logic
 * - own risk logic
 * - own bot runtime logic
 */
@Slf4j
@Getter
@Setter
public class InvestPro extends Application {

    private static final double DEFAULT_WIDTH = 1530;
    private static final double DEFAULT_HEIGHT = 840;
    private static final double MIN_WIDTH = 1200;
    private static final double MIN_HEIGHT = 720;

    private static final String APP_ICON_RESOURCE = "images/Invest.png";
    private static final String APP_BACKGROUND_RESOURCE = "images/Invest.png";
    private static final String APP_CSS_RESOURCE = "css/app.css";
    private static final String COMPONENTS_CSS_RESOURCE = "css/components.css";
    private static final AtomicBoolean ERROR_DIALOG_SHOWING = new AtomicBoolean(false);

    private Stage primaryStage;
    private Scene mainScene;

    private final AnchorPane root = new AnchorPane();

    private TradingDesk tradingDesk;

    private TradeRepository tradeRepository;
    private OrderRepository orderRepository;
    private CurrencyRepository currencyRepository;

    public static void main(String[] args) {
        installGlobalExceptionDialog();
        launch(args);
    }

    public InvestPro() {
        super();
    }

    private static void installGlobalExceptionDialog() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception on {}", thread == null ? "unknown thread" : thread.getName(), throwable);
            showExceptionDialog(
                    "Unexpected Error",
                    "An unexpected application error occurred.",
                    throwable);
        });
    }

    @Override
    public void init() {
        try {
            AppConfig.logStartupSummary();

            tradeRepository = RepositoryFactory.createTradeRepository();
            orderRepository = RepositoryFactory.createOrderRepository();
            currencyRepository = RepositoryFactory.createCurrencyRepository();

            log.info("InvestPro repositories initialized.");

        } catch (Exception exception) {
            log.error("Failed to initialize InvestPro", exception);
            throw new RuntimeException("Failed to initialize InvestPro", exception);
        }
    }

    @Override
    public void start(@NotNull Stage primaryStage) {
        this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage must not be null");

        try {
            StrategyBootstrapper.initialize();

            configurePrimaryStage();
            showOnboarding();

            primaryStage.show();

            log.info("InvestPro JavaFX application started.");

        } catch (Exception exception) {
            log.error("Failed to start InvestPro", exception);
            showErrorAlert("Startup Error", "Failed to start InvestPro.", exception);
        }
    }

    @Override
    public void stop() {
        closeApplication(false);
    }

    private void configurePrimaryStage() {
        root.getStyleClass().add("root");

        mainScene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        loadStylesheet(mainScene);
        loadWindowIcon();
        loadBackgroundImage();

        primaryStage.setTitle(buildWindowTitle("Onboarding"));
        primaryStage.setScene(mainScene);

        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);
        primaryStage.centerOnScreen();

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            closeApplication(true);
        });
    }

    public void showOnboarding() {
        OnboardingView onboardingView = new OnboardingView(this::showTradingTerminal);
        setRootContent(onboardingView);
        primaryStage.setTitle(buildWindowTitle("Onboarding"));
    }

    private void showTradingTerminal(MarketConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        try {
            shutdownTradingTerminal();

            tradingDesk = new TradingDesk(
                    configuration,
                    tradeRepository,
                    orderRepository,
                    currencyRepository);

            setRootContent(tradingDesk);

            primaryStage.setTitle(buildWindowTitle("Trading Desk"));

            log.info("Trading desk opened. configuration={}", configuration);

        } catch (Exception exception) {
            log.error("Failed to open trading terminal", exception);
            showErrorAlert("Trading Terminal Error", "Failed to open the trading terminal.", exception);
        }
    }

    private void setRootContent(javafx.scene.Node node) {
        Objects.requireNonNull(node, "node must not be null");

        root.getChildren().setAll(node);

        AnchorPane.setTopAnchor(node, 0.0);
        AnchorPane.setRightAnchor(node, 0.0);
        AnchorPane.setBottomAnchor(node, 0.0);
        AnchorPane.setLeftAnchor(node, 0.0);
    }

    private void loadWindowIcon() {
        try {
            URL iconUrl = getClass().getClassLoader().getResource(APP_ICON_RESOURCE);

            if (iconUrl == null) {
                log.warn("Window icon not found: {}", APP_ICON_RESOURCE);
                return;
            }

            Image icon = new Image(iconUrl.toExternalForm());

            if (icon.isError()) {
                log.warn("Failed to load window icon: {}", APP_ICON_RESOURCE);
                return;
            }

            primaryStage.getIcons().add(icon);
            log.info("Loaded window icon: {}", APP_ICON_RESOURCE);

        } catch (Exception exception) {
            log.warn("Failed to load window icon: {}", APP_ICON_RESOURCE, exception);
        }
    }

    private void loadStylesheet(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");

        try {
            // Load main stylesheet
            URL appCssUrl = getClass().getClassLoader().getResource(APP_CSS_RESOURCE);
            if (appCssUrl == null) {
                log.warn("Stylesheet not found: {}", APP_CSS_RESOURCE);
                return;
            }

            // Load components stylesheet
            URL componentsCssUrl = getClass().getClassLoader().getResource(COMPONENTS_CSS_RESOURCE);
            if (componentsCssUrl != null) {
                scene.getStylesheets().add(componentsCssUrl.toExternalForm());
                log.info("Loaded stylesheet: {}", COMPONENTS_CSS_RESOURCE);
            } else {
                log.warn("Stylesheet not found: {}", COMPONENTS_CSS_RESOURCE);
            }

            scene.getStylesheets().add(appCssUrl.toExternalForm());
            log.info("Loaded stylesheet: {}", APP_CSS_RESOURCE);

        } catch (Exception exception) {
            log.warn("Failed to load stylesheets", exception);
        }
    }

    private void loadBackgroundImage() {
        try {
            URL imageUrl = getClass().getClassLoader().getResource(APP_BACKGROUND_RESOURCE);

            if (imageUrl == null) {
                log.warn("Background image not found: {}", APP_BACKGROUND_RESOURCE);
                return;
            }

            Image backgroundImage = new Image(imageUrl.toExternalForm());

            if (backgroundImage.isError()) {
                log.warn("Failed to load background image: {}", APP_BACKGROUND_RESOURCE);
                return;
            }

            BackgroundImage bgImage = new BackgroundImage(
                    backgroundImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(
                            BackgroundSize.AUTO,
                            BackgroundSize.AUTO,
                            false,
                            false,
                            true,
                            true));

            root.setBackground(new Background(bgImage));

            log.info("Loaded background image: {}", APP_BACKGROUND_RESOURCE);

        } catch (Exception exception) {
            log.warn("Failed to load background image: {}", APP_BACKGROUND_RESOURCE, exception);
        }
    }

    private void closeApplication(boolean exitPlatform) {
        try {
            shutdownTradingTerminal();
            log.info("InvestPro shutdown completed.");

        } catch (Exception exception) {
            log.warn("Error while shutting down InvestPro", exception);

        } finally {
            if (exitPlatform) {
                Platform.exit();
            }
        }
    }

    private void shutdownTradingTerminal() {
        if (tradingDesk == null) {
            return;
        }

        try {
            tryInvokeShutdown(tradingDesk);
            log.info("Trading desk shutdown completed.");

        } catch (Exception exception) {
            log.error("Trading terminal shutdown failed: {}", exception.getMessage(), exception);

        } finally {
            tradingDesk = null;
        }
    }

    private void tryInvokeShutdown(Object target) {
        if (target == null) {
            return;
        }

        try {
            target.getClass().getMethod("shutdown").invoke(target);

        } catch (NoSuchMethodException ignored) {
            log.debug("{} does not expose shutdown().", target.getClass().getSimpleName());

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to invoke shutdown on " + target.getClass().getSimpleName(),
                    exception);
        }
    }

    private @NotNull String buildWindowTitle(@NotNull String section) {
        String appName = AppConfig.get(AppConfigKeys.APP_NAME, "InvestPro");
        String env = AppConfig.get(AppConfigKeys.APP_ENV, "development");
        String defaultExchange = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA");
        String defaultMarket = AppConfig.get(AppConfigKeys.DEFAULT_MARKET_TYPE, "FOREX");

        return "%s Desk | %s | %s/%s | %s".formatted(
                appName,
                section,
                defaultExchange,
                defaultMarket,
                env.toUpperCase());
    }

    private void showErrorAlert(String title, String header, Throwable throwable) {
        showExceptionDialog(title, header, throwable);
    }

    private static void showExceptionDialog(String title, String header, Throwable throwable) {
        Runnable showDialog = () -> {
            if (!ERROR_DIALOG_SHOWING.compareAndSet(false, true)) {
                return;
            }

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title == null || title.isBlank() ? "InvestPro Error" : title);
            alert.setHeaderText(header == null || header.isBlank() ? "An error occurred." : header);
            alert.setContentText(errorMessage(throwable));

            TextArea details = new TextArea(stackTrace(throwable));
            details.setEditable(false);
            details.setWrapText(false);
            details.setPrefColumnCount(100);
            details.setPrefRowCount(22);
            alert.getDialogPane().setExpandableContent(details);
            alert.getDialogPane().setExpanded(false);
            alert.setOnHidden(event -> ERROR_DIALOG_SHOWING.set(false));
            alert.show();
        };

        try {
            if (Platform.isFxApplicationThread()) {
                showDialog.run();
            } else {
                Platform.runLater(showDialog);
            }
        } catch (IllegalStateException exception) {
            log.error("{}: {}", header, errorMessage(throwable), throwable);
        }
    }

    private static @NotNull String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private static @NotNull String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return "No stack trace available.";
        }

        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
