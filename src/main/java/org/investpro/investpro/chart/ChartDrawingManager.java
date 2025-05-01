package org.investpro.investpro.chart;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;

public class ChartDrawingManager {

    private final Group drawingLayer;
    private Line currentLine;

    public ChartDrawingManager(Scene scene, Group drawingLayer) {
        this.drawingLayer = drawingLayer;
        setupMouseListeners(scene);
    }

    private void setupMouseListeners(@NotNull Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
    }

    private void onMousePressed(@NotNull MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            currentLine = new Line();
            currentLine.setStartX(event.getX());
            currentLine.setStartY(event.getY());
            currentLine.setEndX(event.getX());
            currentLine.setEndY(event.getY());
            currentLine.setStroke(Color.BLUE);
            currentLine.setStrokeWidth(2);
            drawingLayer.getChildren().add(currentLine);
        } else if (event.getButton() == MouseButton.SECONDARY) {
            // Right click = Add annotation text
            Text annotation = new Text(event.getX(), event.getY(), "Note");
            annotation.setFill(Color.YELLOW);
            drawingLayer.getChildren().add(annotation);
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (currentLine != null) {
            currentLine.setEndX(event.getX());
            currentLine.setEndY(event.getY());
        }
    }

    private void onMouseReleased(MouseEvent event) {
        currentLine = null;
    }
}
