package org.investpro.ui.docking;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Detached Stage hosting a DockablePane.
 */
public final class FloatingWindow {
    private final DockablePane pane;
    private final Stage stage;

    public FloatingWindow(DockablePane pane, Runnable onClose) {
        this.pane = Objects.requireNonNull(pane, "pane must not be null");
        this.stage = new Stage();

        BorderPane root = new BorderPane(pane.getView());
        stage.setTitle(pane.getTitle());
        stage.setScene(new Scene(root, 920, 620));
        stage.setOnHidden(event -> {
            if (onClose != null) {
                onClose.run();
            }
        });
    }

    public DockablePane getPane() {
        return pane;
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }
}
