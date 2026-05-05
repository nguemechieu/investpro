package org.investpro.investpro;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Window;

import java.util.function.BooleanSupplier;

public final class FxLifecycle {
    private FxLifecycle() {
    }

    public static boolean isShowing(Node node) {
        if (node == null) {
            return false;
        }
        if (!node.isVisible() || !node.isManaged()) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        var scene = node.getScene();
        if (scene == null) {
            return false;
        }
        Window window = scene.getWindow();
        if (window == null || !window.isShowing()) {
            return false;
        }
        try {
            var bounds = node.localToScreen(node.getLayoutBounds());
            return bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static void runLaterIf(BooleanSupplier guard, Runnable task) {
        try {
            if (Platform.isFxApplicationThread()) {
                if (guard.getAsBoolean()) {
                    task.run();
                }
                return;
            }

            Platform.runLater(() -> {
                if (guard.getAsBoolean()) {
                    task.run();
                }
            });
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit is shutting down; ignore late UI work.
        }
    }
}
