package org.investpro.investpro.ui;

import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.models.Order;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter

public class OrdersUI extends Region {
    private static final Logger logger = LoggerFactory.getLogger(OrdersUI.class);
    private final List<Order> orderList = new CopyOnWriteArrayList<>();
    private Exchange exchange;
    private ListView<Order> orderListView = new ListView<>();
    private ScrollPane orderScrollPane = createScrollPane();
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public OrdersUI(@NotNull Exchange exchange) {
        this.exchange = exchange;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        setupUI();
        startUpdating();
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                shutdown();
            }
        });
    }

    /**
     * ✅ Creates a ScrollPane to contain the ListView
     */
    private @NotNull ScrollPane createScrollPane() {
        ScrollPane scrollPane = new ScrollPane(orderListView);
        scrollPane.setPrefSize(1500, 700);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        return scrollPane;
    }

    /**
     * ✅ Set up the UI layout
     */
    private void setupUI() {
        orderListView.setStyle("-fx-background-color: #0a5c50; -fx-text-fill: #75e6d5;");
        orderListView.setPrefSize(800, 700);
        getChildren().add(orderScrollPane);
    }

    /**
     * ✅ Start fetching and updating orders
     */
    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            if (disposed.get() || !FxLifecycle.isShowing(this)) {
                return;
            }
            try {
                List<Order> newOrders = exchange.getOrders();
                synchronized (orderList) {
                    orderList.clear();
                    orderList.addAll(newOrders);
                }

                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    synchronized (orderList) {
                        updateOrderListView();
                    }
                });

            } catch (IOException | SQLException | NoSuchAlgorithmException | InvalidKeyException | ExecutionException |
                     InterruptedException | ClassNotFoundException | RuntimeException e) {
                logger.error("Error fetching orders", e);
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    /**
     * ✅ Updates the ListView with the latest order data
     */
    private void updateOrderListView() {
        orderListView.getItems().clear();
        orderListView.getItems().addAll(orderList);
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
    }
}
