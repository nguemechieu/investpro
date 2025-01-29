package org.investpro;

import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OrdersView extends AnchorPane {
    public OrdersView(@NotNull Exchange exchange) throws SQLException, IOException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException, InterruptedException, ClassNotFoundException {

        List<Order> orderList = exchange.getOrders();

        ListView<Order> view = new ListView<>();
        view.getItems().addAll(orderList);


        setPrefSize(800, 700);

        AnchorPane.setTopAnchor(view, 5.0);
        AnchorPane.setLeftAnchor(view, 5.0);
        AnchorPane.setRightAnchor(view, 5.0);
        AnchorPane.setBottomAnchor(view, 5.0);

        getChildren().add(view);
        setPrefSize(1500, 700);

    }

}
