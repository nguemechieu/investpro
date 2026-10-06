package org.investpro.ui.utils;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WindowSizing {
    private WindowSizing() {}

    public static void fitToScreen(Stage stage) {
        Screen screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D bounds = screen.getVisualBounds();
        double width = Math.min(stage.getWidth(), bounds.getWidth());
        double height = Math.min(stage.getHeight(), bounds.getHeight());
        stage.setMinWidth(Math.min(320, bounds.getWidth()));
        stage.setMinHeight(Math.min(240, bounds.getHeight()));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(Math.max(bounds.getMinX(), Math.min(stage.getX(), bounds.getMaxX() - width)));
        stage.setY(Math.max(bounds.getMinY(), Math.min(stage.getY(), bounds.getMaxY() - height)));
    }
}
