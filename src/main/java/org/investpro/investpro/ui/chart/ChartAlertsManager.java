package org.investpro.investpro.ui.chart;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Getter
@Setter
public class ChartAlertsManager {

    private final CandleStickChart chart;
    private final Pane drawingLayer;
    private final List<CandleData> latestPriceSupplier;
    private final List<Line> alertLines = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    public ChartAlertsManager(@NotNull CandleStickChart chart, @NotNull AnchorPane chartLayer, List<CandleData> latestPriceSupplier) {
        this.chart = chart;
        this.drawingLayer = chartLayer;
        this.latestPriceSupplier = latestPriceSupplier;
    }

    public void addAlertLine(double price) {
        if (chart.getYAxis() == null) return;

        double y = chart.getYAxis().getDisplayPosition(price);

        Line alertLine = new Line(0, y, chart.getWidth(), y);
        alertLine.setStroke(Color.ORANGE);
        alertLine.setStrokeWidth(1.5);

        alertLines.add(alertLine);
        drawingLayer.getChildren().add(alertLine);
    }

    public void startChecking() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkPriceAgainstAlerts, 0, 2, TimeUnit.SECONDS);
    }

    public void stopChecking() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void checkPriceAgainstAlerts() {
        double latestPrice = latestPriceSupplier.getLast().getOpenPrice();

        Platform.runLater(() -> {
            if (chart.getYAxis() == null) return;

            double y = chart.getYAxis().getDisplayPosition(latestPrice);
            List<Line> triggeredLines = new ArrayList<>();

            for (Line line : alertLines) {
                if (Math.abs(line.getStartY() - y) <= 5) {
                    triggeredLines.add(line);
                    triggerAlert(latestPrice);
                }
            }

            alertLines.removeAll(triggeredLines);
            drawingLayer.getChildren().removeAll(triggeredLines);
        });
    }

    private void triggerAlert(double price) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🔔 Price Alert Triggered");
        alert.setHeaderText("Price crossed your alert line!");
        alert.setContentText(String.format("Current Price: %.2f", price));
        alert.show();
    }
}
