package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.investpro.Exchange;
import org.investpro.Order;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PendingOrdersUI extends Region {
    private final Exchange exchange;
    private final ScheduledExecutorService scheduler;
    private final Label statusLabel;
    ListView<Order> pendingOrdersView;

    public PendingOrdersUI(@NotNull Exchange exchange) {
        this.exchange = exchange;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // **Title Label**
        Label titleLabel = new Label("📌 Pending Orders");
        titleLabel.setFont(Font.font("Arial", 22));
        titleLabel.setTextFill(Color.DARKORANGE);

        // **Status Label**
        statusLabel = new Label("Loading pending orders...");
        statusLabel.setFont(Font.font("Arial", 16));
        statusLabel.setTextFill(Color.GRAY);

        // **ListView for Orders**
        pendingOrdersView = new ListView<>();
        pendingOrdersView.setPrefWidth(1400);
        pendingOrdersView.setPrefHeight(700);
        pendingOrdersView.setPlaceholder(new Label("No pending orders available"));
        pendingOrdersView.setCellFactory(_ -> new OrderCell());

        // **ScrollPane for smooth scrolling**
        ScrollPane scrollPane = new ScrollPane(pendingOrdersView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPadding(new Insets(10));

        // **UI Layout**
        setPadding(new Insets(10));
        setPrefSize(
                1500, 780);

        // **Start Auto-Refreshing**
        startUpdating();
        getChildren().addAll(titleLabel, statusLabel, scrollPane);

    }

    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Order> pendingOrders;
                try {
                    pendingOrders = exchange.getPendingOrders();
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    throw new RuntimeException(e);
                }

                List<Order> finalPendingOrders = pendingOrders;
                Platform.runLater(() -> {
                    pendingOrdersView.getItems().setAll(finalPendingOrders);
                    statusLabel.setText("✅ Updated at " + java.time.LocalTime.now());

                    if (finalPendingOrders.isEmpty()) {
                        statusLabel.setText("⚠️ No pending orders.");
                    }
                });

            } catch (IOException | InterruptedException | ExecutionException e) {
                Platform.runLater(() -> statusLabel.setText("❌ Error fetching orders!"));
            }
        }, 3, 10, TimeUnit.SECONDS);
    }

    // **Custom ListCell for Orders**
    private static class OrderCell extends ListCell<Order> {
        @Override
        protected void updateItem(Order order, boolean empty) {
            super.updateItem(order, empty);
            if (empty || order == null) {
                setText(null);
            } else {
                setText(String.format("📌 %s | %s | Amount: %.4f | Price: %.2f",
                        order.getSymbol(), order.getOrderType(), order.getSize(), order.getPrice()));
            }
        }
    }
}
