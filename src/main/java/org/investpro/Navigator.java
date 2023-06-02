package org.investpro;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;


public class Navigator extends Stage {
    public Navigator(@NotNull Exchange exchange) {
        super();
        javafx.scene.control.Label title = new javafx.scene.control.Label(exchange.getName());


        VBox vBox = new VBox(title);
        HBox hBox = new HBox();
        hBox.getChildren().addAll(new HomeButton(), new AboutButton(), new ExitButton());

        vBox.setPrefSize(
                800, 600
        );
        ObservableList<?> observableList = FXCollections.observableArrayList(exchange);
        vBox.getChildren().addAll(hBox, new ListView<>(observableList
        ));

        setScene(
                new Scene(vBox)
        );
        setResizable(true);
        setTitle(exchange.getName());
        show();

    }

    private static class HomeButton extends Button {
        public HomeButton() {
            super();
            getStyleClass().add("home-button");
            setPrefSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
    }

    private static class AboutButton extends Button {
        public AboutButton() {
            super();
            getStyleClass().add("about-button");
            setPrefSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
    }

    private class ExitButton extends Button {
        public ExitButton() {
            super();
            getStyleClass().add("exit-button");
            setPrefSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
    }
}
