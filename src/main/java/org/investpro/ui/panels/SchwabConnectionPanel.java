package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.investpro.exchange.schwab.Schwab;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Browser authorization is explicit; broker work never blocks the JavaFX thread. */
public final class SchwabConnectionPanel extends VBox {
    private volatile long actionGeneration;
    private boolean busy;
    public SchwabConnectionPanel(Schwab broker, Consumer<Schwab> onConnected) {
        super(12); setPadding(new javafx.geometry.Insets(18));
        var status = new Label(broker.authenticationStatus()); status.setWrapText(true);
        var help = new Label("Schwab uses browser login and account consent. Configure the developer app client ID/secret, exact HTTPS callback, callback TLS keystore and encrypted token-store password before connecting. No Schwab username or password is stored in InvestPro.");
        help.setWrapText(true);
        var connect = new Button("Connect Schwab"); var authorize = new Button("Reauthorize");
        var disconnect = new Button("Disconnect"); var unlink = new Button("Unlink account");
        Consumer<java.util.function.Supplier<CompletableFuture<Void>>> finish = operation -> {
            long expected = ++actionGeneration;
            busy = true;
            connect.setDisable(true); authorize.setDisable(true); status.setText("Connecting: complete browser consent if requested.");
            CompletableFuture<Void> future;
            try { future = operation.get(); }
            catch (RuntimeException error) { future = CompletableFuture.failedFuture(error); }
            future.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (expected != actionGeneration) return;
                busy = false;
                connect.setDisable(false); authorize.setDisable(false);
                if (error == null) { status.setText("Connected"); onConnected.accept(broker); }
                else status.setText("Authentication Error / Authorization Required. Check the HTTPS callback, TLS certificate, OAuth endpoints and token-store password; then reauthorize.");
            }));
        };
        connect.setOnAction(_ -> {
            finish.accept(() -> {
                long expected = actionGeneration;
                return broker.connectAsync().exceptionallyCompose(error -> {
                    Throwable cause = error;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (!(cause instanceof org.investpro.exchange.schwab.SchwabAuthenticationException))
                        return CompletableFuture.failedFuture(error);
                    var retry = new CompletableFuture<Void>();
                    Platform.runLater(() -> {
                        if (expected != actionGeneration) { retry.completeExceptionally(error); return; }
                        try {
                            broker.reauthorize().whenComplete((_, failure) -> {
                                if (failure == null) retry.complete(null); else retry.completeExceptionally(failure);
                            });
                        } catch (RuntimeException failure) { retry.completeExceptionally(failure); }
                    });
                    return retry;
                });
            });
        });
        authorize.setOnAction(_ -> finish.accept(broker::reauthorize));
        disconnect.setOnAction(_ -> {
            actionGeneration++; busy = false; broker.disconnect();
            connect.setDisable(false); authorize.setDisable(false);
            status.setText("Disconnected. Saved authorization retained.");
        });
        unlink.setOnAction(_ -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove this app's saved Schwab authorization?", ButtonType.YES, ButtonType.CANCEL);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) return;
            long expected = ++actionGeneration; busy = true;
            broker.disconnect(); connect.setDisable(true); authorize.setDisable(true);
            broker.unlinkAsync().whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (expected != actionGeneration) return;
                busy = false; connect.setDisable(false); authorize.setDisable(false);
                status.setText(error == null ? "Authorization Required. Saved tokens removed." : "Unable to remove saved authorization.");
            }));
        });
        getChildren().setAll(new Label("Charles Schwab OAuth"), status, help, new FlowPane(10, 8, connect, authorize, disconnect, unlink));
        var timer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), _ -> {
            if (!busy) status.setText(broker.authenticationStatus());
        }));
        timer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) {
                timer.stop(); actionGeneration++;
                if (busy) { busy = false; broker.disconnect(); }
            } else timer.play();
        });
    }
}
