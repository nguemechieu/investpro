package org.investpro;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

public class OrdersDisplay extends Stage {
    public OrdersDisplay(@NotNull Exchange exchange) throws IOException, InterruptedException, ParseException, URISyntaxException {

        VBox vbox = new VBox();
        vbox.getStyleClass().add("/app");
        vbox.setSpacing(20);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);
        vbox.setPrefSize(700, 500);
        vbox.getChildren().add(new ListView<>(FXCollections.observableArrayList(exchange.getName(), exchange.getAllOrders())));


        this.setScene(new Scene(vbox, 800, 400));
        this.show();

    }
}
