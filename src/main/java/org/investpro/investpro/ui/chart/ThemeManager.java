package org.investpro.investpro.ui.chart;

import javafx.scene.Scene;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class ThemeManager {

    private static final String LIGHT_THEME = "../../src/main/resources/css/themes/light.css";
    private static final String DARK_THEME = "../../src/main/resources/css/themes/dark.css";
    private final Scene scene;

    private boolean darkMode = false;
    public ThemeManager(Scene scene) {
        this.scene = scene;
    }
    public void toggleTheme() {
        if (darkMode) {
            applyLightTheme();
        } else {
            applyDarkTheme();
        }
        darkMode = !darkMode;
    }

    public void applyLightTheme() {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(LIGHT_THEME)).toExternalForm());
    }
    public void applyDarkTheme() {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(DARK_THEME)).toExternalForm());
    }

    public void toggleTheme(int i) {
    }
}
