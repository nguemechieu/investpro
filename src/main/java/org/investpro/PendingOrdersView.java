package org.investpro;

import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PendingOrdersView extends Region {
    public PendingOrdersView(@NotNull Exchange exchange) throws IOException, NoSuchAlgorithmException, ExecutionException, InvalidKeyException, InterruptedException {

        getStyleClass().add("pending-orders-view");

        List<Order> pendingOrderList = exchange.getPendingOrders();

        ListView<Order> pendingOrdersView = new ListView<>();
        pendingOrdersView.getItems().addAll(pendingOrderList);

        pendingOrdersView.setPrefSize(1500, 700);

        getChildren().add(pendingOrdersView);


    }
}
