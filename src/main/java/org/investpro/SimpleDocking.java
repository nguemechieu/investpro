package org.investpro;

import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class SimpleDocking extends Application {
    public void start(final @NotNull Stage stage) throws Exception {
        final SplitPane rootPane = new SplitPane();
        rootPane.setOrientation(Orientation.VERTICAL);

        final FlowPane dockedArea = new FlowPane();
        dockedArea.getChildren().add(new Label("Some docked content"));

        final FlowPane centerArea = new FlowPane();
        final Button undockButton = new Button("Undock");
        undockButton.setOnAction(
                e -> {
                    centerArea.getChildren().add(undockButton);

                }
        );

        rootPane.getItems().addAll(centerArea, dockedArea);

        stage.setScene(new Scene(rootPane, 300, 300));
        stage.show();

        final Dialog dialog = new Dialog();
        undockButton.disableProperty().bind(dialog.getDialogPane().cacheProperty());

        undockButton.setOnAction(actionEvent -> {
            rootPane.getItems().remove(dockedArea);
        });
    }
}
