package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import org.investpro.repository.*;
import org.investpro.strategy.StrategyBootstrapper;
import org.investpro.ui.MarketConfiguration;
import org.investpro.ui.OnboardingView;
import org.investpro.ui.TradingDesk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Objects;

/**
 * InvestPro - Professional Multi-Exchange Trading Terminal.
 * <p>
 * Main JavaFX entry point.
 * <p>
 * Flow:
 * - Start onboarding
 * - User chooses broker/configuration
 * - Open trading terminal
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
    private static final String APP_CSS_RESOURCE = "css/app.css";

    private Stage primaryStage;
    private Scene mainScene;

    private final AnchorPane root = new AnchorPane();

    private TradingDesk tradingDesk;

    public static void main(String[] args) {
        launch(args);
    }

    public InvestPro() {
        super();

        try {
            this.tradeRepository = RepositoryFactory.createTradeRepository();
            this.orderRepository = RepositoryFactory.createOrderRepository();
            this.currencyRepository = RepositoryFactory.createCurrencyRepository();

        } catch (Exception exception) {
            throw new RuntimeException("Failed to initialize InvestPro repositories", exception);
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

        } catch (Exception exception) {
            log.error("Failed to start InvestPro", exception);
            showErrorAlert(exception);
        }
    }

    private void configurePrimaryStage() {
        root.getStyleClass().add("root");

        mainScene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        loadStylesheet(mainScene);
        loadWindowIcon();
        loadBackgroundImage();

        primaryStage.setTitle(buildWindowTitle());
        primaryStage.setScene(mainScene);

        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setResizable(true);

        /*
         * Better than setFullScreen(true) for desktop apps:
         * - no forced fullscreen warning
         * - behaves like normal professional workstation software
         * - user can still resize/minimize
         */
        primaryStage.setMaximized(true);

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            closeApplication();
        });
    }

    public void showOnboarding() {
        OnboardingView onboardingView = new OnboardingView(this::showTradingTerminal);
        setRootContent(onboardingView);
        primaryStage.setTitle("%s - Onboarding".formatted(buildWindowTitle()));
    }

    private TradeRepository tradeRepository;
    private OrderRepository orderRepository;
    private CurrencyRepository currencyRepository;

    private void showTradingTerminal(MarketConfiguration configuration) {
        try {
            shutdownTradingTerminal();

            tradingDesk = new TradingDesk(configuration,tradeRepository,orderRepository,currencyRepository);
            setRootContent(tradingDesk);

            primaryStage.setTitle("%s - Trading Desk".formatted(buildWindowTitle()));

        } catch (Exception exception) {

            log.error("Failed to open trading terminal", exception);
            showErrorAlert(exception);

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
            URL iconUrl = getClass().getClassLoader().getResource(InvestPro.APP_ICON_RESOURCE);

            if (iconUrl == null) {
                log.warn("Window icon not found: {}", InvestPro.APP_ICON_RESOURCE);
                return;
            }

            Image icon = new Image(iconUrl.toExternalForm());

            if (icon.isError()) {
                log.warn("Failed to load window icon: {}", InvestPro.APP_ICON_RESOURCE);
                return;
            }

            primaryStage.getIcons().add(icon);
            primaryStage.centerOnScreen();
            primaryStage.setResizable(true);

            log.info("Loaded window icon: {}", InvestPro.APP_ICON_RESOURCE);

        } catch (Exception exception) {
            log.warn("Failed to load window icon: {}", InvestPro.APP_ICON_RESOURCE, exception);
        }
    }

    private void loadStylesheet(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");

        try {
            URL cssUrl = getClass().getClassLoader().getResource(InvestPro.APP_CSS_RESOURCE);

            if (cssUrl == null) {
                log.warn("Stylesheet not found: {}", InvestPro.APP_CSS_RESOURCE);
                return;
            }

            scene.getStylesheets().setAll(cssUrl.toExternalForm());
            log.info("Loaded stylesheet: {}", InvestPro.APP_CSS_RESOURCE);

        } catch (Exception exception) {
            log.warn("Failed to load stylesheet: {}", InvestPro.APP_CSS_RESOURCE, exception);
        }
    }

    private void loadBackgroundImage() {
        try {
            URL imageUrl = getClass().getClassLoader().getResource("images/Invest.png");

            if (imageUrl == null) {
                log.warn("Background image not found: images/Invest.png");
                return;
            }

            Image backgroundImage = new Image(imageUrl.toExternalForm());

            if (backgroundImage.isError()) {
                log.warn("Failed to load background image: images/Invest.png");
                return;
            }

            BackgroundImage bgImage = new BackgroundImage(
                    backgroundImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false,false,true, true));

            Background background = new Background(bgImage);
            root.setBackground(background);


            log.info("Loaded background image: images/Invest.png");

        } catch (Exception exception) {
            log.warn("Failed to load background image", exception);
        }
    }

    private void closeApplication() {
        try {
            shutdownTradingTerminal();
        } catch (Exception exception) {
            log.warn("Error while shutting down trading terminal", exception);
            throw new RuntimeException(exception);
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }

    private void shutdownTradingTerminal() {
        if (tradingDesk == null) {
            return;
        }

        try {
            tryInvokeShutdown(tradingDesk);
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
            log.debug("TradingDesk does not expose shutdown() yet.");
        } catch (Exception exception) {
            throw new RuntimeException("Failed to invoke shutdown on " + target.getClass().getSimpleName(), exception);
        }
    }

    private @NotNull String buildWindowTitle() {
        return "InvestPro---------------------------------------------------------------------------------------------Desk | ";
    }

    private void showErrorAlert(Throwable throwable) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Terminal Error");
            alert.setHeaderText("Failed to open the trading terminal.");
            alert.setContentText(
                    throwable == null || throwable.getMessage() == null
                            ? "Unknown error"
                            : throwable.getMessage());
            alert.showAndWait();
        });
    }
}
