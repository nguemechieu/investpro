package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.investpro.ui.MarketConfiguration;
import org.investpro.ui.OnboardingView;
import org.investpro.ui.TradingWindow;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * InvestPro - Professional Multi-Exchange Trading Terminal.
 *
 * Main JavaFX entry point.
 *
 * Flow:
 * - Start onboarding
 * - User chooses broker/configuration
 * - Open MT4-style trading terminal
 */
public class InvestPro extends Application {

    private static final double DEFAULT_WIDTH = 1540;
    private static final double DEFAULT_HEIGHT = 860;
    private static final double MIN_WIDTH = 1200;
    private static final double MIN_HEIGHT = 720;

    private Stage primaryStage;
    private final AnchorPane root = new AnchorPane();

    private TradingWindow tradingWindow;

   public static void main(String[] args) {
        launch(args);
    }

    public InvestPro() {
        super();
    }

    @Override
    public void start(@NotNull Stage primaryStage) {
        this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage must not be null");

        configurePrimaryStage();
        configureRootScene();

        showOnboarding();

        primaryStage.show();
    }

    private void configurePrimaryStage() {
        primaryStage.setTitle(buildWindowTitle());
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setWidth(DEFAULT_WIDTH);
        primaryStage.setHeight(DEFAULT_HEIGHT);
        primaryStage.setResizable(true);

        primaryStage.setOnCloseRequest(_ -> {
            shutdownTradingTerminal();
            Platform.exit();
        });
    }

    private void configureRootScene() {
//        root.setStyle("-fx-background-color: #0f172a;");


        root.getChildren().clear();
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        installStylesheet(scene);

        primaryStage.setScene(scene);
    }

    public void showOnboarding() {
        OnboardingView onboardingView = new OnboardingView(this::showTradingTerminal);
        setRootContent(onboardingView);
        primaryStage.setTitle("%s - Onboarding".formatted(buildWindowTitle()));
    }

    private void showTradingTerminal(MarketConfiguration configuration) {
        try {
            shutdownTradingTerminal();

            tradingWindow = new TradingWindow(configuration);
            setRootContent(tradingWindow);

            primaryStage.setTitle("%s - Terminal".formatted(buildWindowTitle()));

        } catch (ParseException | IOException | InterruptedException | ClassNotFoundException exception) {
            Thread.currentThread().interrupt();
            showErrorAlert(
                    "Terminal Error",
                    "Failed to open the trading terminal.",
                    exception
            );
        } catch (Exception exception) {
            showErrorAlert(
                    "Terminal Error",
                    "Failed to open the trading terminal.",
                    exception
            );
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

    private void installStylesheet(Scene scene) {
        try {
            String cssResource = Objects.requireNonNull(
                    InvestPro.class.getResource("..\\..\\app.css"),
                    "Missing /app.css resource"
            ).toExternalForm();

            scene.getStylesheets().setAll(cssResource);
        } catch (Exception exception) {
            throw new RuntimeException("Unable to load /app.css: %s%n", exception);
        }
    }

    private void shutdownTradingTerminal() {
        if (tradingWindow == null) {
            return;
        }

        try {
            /*
             * Add public void shutdown() to TradingWindow later if you want to:
             * - stop SmartBot
             * - stop exchange streams
             * - close WebSockets
             * - shutdown schedulers
             *
             * For now this keeps the app compatible.
             */
            tryInvokeShutdown(tradingWindow);
        } catch (Exception exception) {
            System.err.println("Trading terminal shutdown failed: " + exception.getMessage());
        } finally {
            tradingWindow = null;
        }
    }

    private void tryInvokeShutdown(Object target) {
        try {
            target.getClass().getMethod("shutdown").invoke(target);
        } catch (NoSuchMethodException ignored) {
            // TradingWindow does not expose shutdown() yet.
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private @NotNull String buildWindowTitle() {
        return "Professional Trading Terminal | © 2020-%d InvestPro .Inc"
                .formatted(LocalDate.now().getYear());
    }

    private void showErrorAlert(String title, String message, Throwable throwable) {
        // Defer dialog display to allow JavaFX to finish animation/layout processing
        // This prevents "showAndWait is not allowed during animation or layout processing" error
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(message);
            alert.setContentText(
                    throwable == null || throwable.getMessage() == null
                            ? "Unknown error"
                            : throwable.getMessage()
            );
            alert.showAndWait();
        });
    }
}