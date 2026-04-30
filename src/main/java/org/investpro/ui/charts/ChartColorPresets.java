package org.investpro.ui.charts;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-defined color themes for candlestick charts.
 * Allows users to quickly switch between professional color schemes.
 */
public final class ChartColorPresets {
    
    public static final String DEFAULT = "Default";
    public static final String DARK_MODE = "Dark Mode";
    public static final String LIGHT_MODE = "Light Mode";
    public static final String HIGH_CONTRAST = "High Contrast";
    
    private static final Map<String, ColorScheme> PRESETS = new HashMap<>();
    
    static {
        // Default professional scheme
        PRESETS.put(DEFAULT, new ColorScheme(
                "Default Professional",
                Color.rgb(8, 153, 129, 0.96),      // Bull fill
                Color.rgb(8, 153, 129),             // Bull border
                Color.rgb(242, 54, 69, 0.96),      // Bear fill
                Color.rgb(242, 54, 69),             // Bear border
                Color.rgb(59, 130, 246, 0.2),      // Volume color
                Color.rgb(120, 134, 156),           // Text color
                Color.rgb(42, 52, 68, 0.42)        // Grid color
        ));
        
        // Dark mode with muted colors
        PRESETS.put(DARK_MODE, new ColorScheme(
                "Dark Mode",
                Color.rgb(34, 197, 94, 0.85),      // Bull fill (softer green)
                Color.rgb(34, 197, 94),             // Bull border
                Color.rgb(248, 113, 113, 0.85),    // Bear fill (softer red)
                Color.rgb(248, 113, 113),           // Bear border
                Color.rgb(96, 165, 250, 0.15),     // Volume color (softer blue)
                Color.rgb(189, 198, 206),           // Text color (lighter)
                Color.rgb(55, 65, 81, 0.35)        // Grid color (darker)
        ));
        
        // Light mode for bright backgrounds
        PRESETS.put(LIGHT_MODE, new ColorScheme(
                "Light Mode",
                Color.rgb(5, 120, 100, 0.9),       // Bull fill (darker green)
                Color.rgb(5, 120, 100),             // Bull border
                Color.rgb(220, 38, 38, 0.9),       // Bear fill (darker red)
                Color.rgb(220, 38, 38),             // Bear border
                Color.rgb(30, 90, 200, 0.15),      // Volume color (navy blue)
                Color.rgb(50, 50, 50),              // Text color (dark)
                Color.rgb(200, 200, 200, 0.4)      // Grid color (light)
        ));
        
        // High contrast for accessibility
        PRESETS.put(HIGH_CONTRAST, new ColorScheme(
                "High Contrast",
                Color.rgb(0, 200, 100),             // Bull fill (bright green)
                Color.rgb(0, 150, 75),              // Bull border (darker green)
                Color.rgb(255, 0, 0),               // Bear fill (bright red)
                Color.rgb(200, 0, 0),               // Bear border (darker red)
                Color.rgb(0, 0, 255, 0.25),        // Volume color (bright blue)
                Color.rgb(0, 0, 0),                 // Text color (black)
                Color.rgb(150, 150, 150, 0.5)      // Grid color (dark gray)
        ));
    }
    
    public static ColorScheme getPreset(String name) {
        return PRESETS.getOrDefault(name, PRESETS.get(DEFAULT));
    }
    
    @Contract(pure = true)
    public static java.util.@NotNull Set<String> getPresetNames() {
        return PRESETS.keySet();
    }

    /**
         * Immutable color scheme containing all chart colors.
         */
        public record ColorScheme(String name, Paint bullFill, Paint bullBorder, Paint bearFill, Paint bearBorder,
                                  Paint volumeColor, Paint textColor, Paint gridColor) {

    }
    
    private ChartColorPresets() {
        throw new AssertionError();
    }
}
