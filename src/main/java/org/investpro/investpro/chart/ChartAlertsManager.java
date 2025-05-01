package org.investpro.investpro.chart;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Getter
@Setter
public class ChartAlertsManager {

    private final XYChart<String, Number> chart;
    private final Pane drawingLayer; // ✅ Instead of plotChildren
    private final Supplier<Double> latestPriceSupplier;
    private final List<Line> alertLines = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    public ChartAlertsManager(XYChart<String, Number> chart, Group drawingLayer, Supplier<Double> latestPriceSupplier) {
        this.chart = chart;
        this.drawingLayer = new Pane(drawingLayer);
        this.latestPriceSupplier = latestPriceSupplier;
    }

    public void addAlertLine(double price) {
        double y = chart.getYAxis().getDisplayPosition(price);

        Line alertLine = new Line(0, y, chart.getWidth(), y);
        alertLine.setStroke(Color.ORANGE);
        alertLine.setStrokeWidth(2);

        alertLines.add(alertLine);
        drawingLayer.getChildren().add(alertLine); // ✅ Use your top-level drawing layer
    }

    public void startChecking() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkPriceAgainstAlerts, 0, 2, TimeUnit.SECONDS);
    }

    public void stopChecking() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void checkPriceAgainstAlerts() {
        double latestPrice = latestPriceSupplier.get();

        Platform.runLater(() -> {
            List<Line> triggered = new ArrayList<>();
            double currentY = chart.getYAxis().getDisplayPosition(latestPrice);

            for (Line line : alertLines) {
                if (Math.abs(line.getStartY() - currentY) < 5) {
                    triggered.add(line);
                    triggerAlert(latestPrice);
                }
            }

            drawingLayer.getChildren().removeAll(triggered);
            alertLines.removeAll(triggered);
        });
    }

    private void triggerAlert(double price) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Price Alert Triggered");
        alert.setHeaderText("Price crossed your alert line!");
        alert.setContentText("Current Price: " + String.format("%.2f", price));
        alert.show();
    }
}
