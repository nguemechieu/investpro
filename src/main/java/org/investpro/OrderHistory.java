package org.investpro;

import javafx.scene.layout.StackPane;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OrderHistory extends StackPane {
    public OrderHistory(@NotNull Exchange exchange) throws IOException, InterruptedException {
        super();
        StackPane stackPane = new StackPane();
        stackPane.setStyle("-fx-border-color: rgb(115,134,213,1);");
        stackPane.setPrefSize(800, 300);
        stackPane.getChildren().add(exchange.getAllOrders());
        getChildren().add(stackPane);
    }
}
