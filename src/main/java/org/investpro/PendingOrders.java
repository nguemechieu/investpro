package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class PendingOrders extends Stage {
    public PendingOrders(@NotNull Exchange exchange) throws IOException, InterruptedException {
        ListView<Order> listView = new ListView<>();
        listView.getItems().addAll(exchange.getPendingOrders());

        listView.setPrefSize(900, 500);
        listView.setBackground(Background.fill(Paint.valueOf("#000000")));

        setTitle("Pending Orders");
        setScene(new Scene(listView));
        show();

    }
}
