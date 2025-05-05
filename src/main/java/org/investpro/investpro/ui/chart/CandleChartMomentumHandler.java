package org.investpro.investpro.ui.chart;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.util.Duration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class CandleChartMomentumHandler {
    private final Canvas canvas;
    private final Runnable moveXAxisByPixels;
    private Timeline timeline;

    public CandleChartMomentumHandler(Canvas canvas, Runnable moveXAxisByPixels) {
        this.canvas = canvas;
        this.moveXAxisByPixels = moveXAxisByPixels;
        initializeMomentumScrolling();
    }

    private void initializeMomentumScrolling() {
        AtomicReference<Double> velocityX = new AtomicReference<>(0.0);
        AtomicReference<Double> lastMouseX = new AtomicReference<>(0.0);
        AtomicReference<Instant> lastDragTime = new AtomicReference<>(Instant.now());

        canvas.setOnMousePressed(event -> {
            lastMouseX.set(event.getX());
            lastDragTime.set(Instant.now());
            velocityX.set(0.0);
        });

        canvas.setOnMouseDragged(event -> {
            double currentX = event.getX();
            Instant currentTime = Instant.now();
            double timeDiff = (currentTime.toEpochMilli() - lastDragTime.get().toEpochMilli()) / 1000.0;
            if (timeDiff > 0) {
                velocityX.set((currentX - lastMouseX.get()) / timeDiff);
            }
            lastMouseX.set(currentX);
            lastDragTime.set(currentTime);
        });

        canvas.setOnMouseReleased(_ -> applyMomentumScrolling(velocityX.get()));
    }

    private void applyMomentumScrolling(double initialVelocity) {
        if (Math.abs(initialVelocity) < 1) return;

        DoubleProperty velocityProperty = new SimpleDoubleProperty(initialVelocity);
        timeline = new Timeline(new KeyFrame(Duration.millis(16), _ -> {
            double newVelocity = velocityProperty.get() * 0.95;
            if (Math.abs(newVelocity) < 1) {
                timeline.stop();
            } else {
                moveXAxisByPixels.run();
                velocityProperty.set(newVelocity);
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }
}
