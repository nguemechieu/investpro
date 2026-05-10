package org.investpro.ui.theme;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages application themes and visual preferences
 * Supports Dark Mode, Light Mode, and Custom themes with visibility controls
 */
@Slf4j
public class ThemeManager {
    public enum Theme {
        DARK_MODE("Dark Mode", ThemeMode.DARK),
        LIGHT_MODE("Light Mode", ThemeMode.LIGHT),
        SYSTEM_DEFAULT("System Default", ThemeMode.SYSTEM);

        private final String displayName;
        private final ThemeMode mode;

        Theme(String displayName, ThemeMode mode) {
            this.displayName = displayName;
            this.mode = mode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ThemeMode getMode() {
            return mode;
        }
    }

    public enum ThemeMode {
        DARK, LIGHT, SYSTEM
    }

    @Getter
    public static class ThemeConfig {
        private Theme theme;
        private double opacity = 1.0; // 0.0 to 1.0
        private boolean useCompactLayout = false;
        private boolean useHighContrast = false;
        private String accentColor = "#1e90ff";

        public ThemeConfig() {
            this.theme = Theme.DARK_MODE;
        }

        public void setTheme(Theme theme) {
            this.theme = theme;
        }

        public void setOpacity(double opacity) {
            this.opacity = Math.max(0.3, Math.min(1.0, opacity));
        }

        public void setCompactLayout(boolean compact) {
            this.useCompactLayout = compact;
        }

        public void setHighContrast(boolean highContrast) {
            this.useHighContrast = highContrast;
        }

        public void setAccentColor(String color) {
            this.accentColor = color;
        }
    }

    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.investpro/theme.properties";
    private static ThemeManager instance;
    private final ThemeConfig themeConfig;
    private final Map<String, String> darkThemeColors;
    private final Map<String, String> lightThemeColors;

    private ThemeManager() {
        this.themeConfig = new ThemeConfig();
        this.darkThemeColors = initDarkThemeColors();
        this.lightThemeColors = initLightThemeColors();
        loadConfiguration();
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Dark theme color palette
     */
    private Map<String, String> initDarkThemeColors() {
        Map<String, String> colors = new HashMap<>();
        colors.put("primary-bg", "#0f3460");
        colors.put("secondary-bg", "#16213e");
        colors.put("tertiary-bg", "#1a1a2e");
        colors.put("text-primary", "#ffffff");
        colors.put("text-secondary", "#a0aec0");
        colors.put("border", "#374151");
        colors.put("accent", "#1e90ff");
        colors.put("success", "#10b981");
        colors.put("error", "#ef4444");
        colors.put("warning", "#f59e0b");
        colors.put("info", "#06b6d4");
        return colors;
    }

    /**
     * Light theme color palette
     */
    private Map<String, String> initLightThemeColors() {
        Map<String, String> colors = new HashMap<>();
        colors.put("primary-bg", "#ffffff");
        colors.put("secondary-bg", "#f3f4f6");
        colors.put("tertiary-bg", "#e5e7eb");
        colors.put("text-primary", "#1f2937");
        colors.put("text-secondary", "#6b7280");
        colors.put("border", "#d1d5db");
        colors.put("accent", "#1e90ff");
        colors.put("success", "#059669");
        colors.put("error", "#dc2626");
        colors.put("warning", "#d97706");
        colors.put("info", "#0891b2");
        return colors;
    }

    /**
     * Apply theme to a region with all color mappings
     */
    public void applyTheme(Region region) {
        if (region == null)
            return;

        Map<String, String> colors = getActiveColors();
        String bgColor = colors.get("primary-bg");
        String textColor = colors.get("text-primary");
        String borderColor = colors.get("border");

        // Apply base styles
        region.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s;",
                bgColor, textColor, borderColor));

        // Apply opacity
        region.setOpacity(themeConfig.opacity);
    }

    /**
     * Apply theme to a node recursively
     */
    public void applyThemeRecursively(Node node) {
        if (node instanceof Region region) {
            applyTheme(region);
        }
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(this::applyThemeRecursively);
        }
    }

    /**
     * Get the active color palette based on current theme
     */
    public Map<String, String> getActiveColors() {
        return themeConfig.theme.getMode() == ThemeMode.DARK
                ? darkThemeColors
                : lightThemeColors;
    }

    /**
     * Get specific color from active theme
     */
    public String getColor(String colorKey) {
        return getActiveColors().getOrDefault(colorKey, "#000000");
    }

    /**
     * Get complete inline CSS style for a component
     */
    public String getComponentStyle(String componentType) {
        Map<String, String> colors = getActiveColors();
        String style = "";

        switch (componentType.toLowerCase()) {
            case "button" -> style = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 8px 16px; -fx-border-radius: 4;",
                    colors.get("accent"), colors.get("primary-bg"));
            case "text-input" -> style = String.format(
                    "-fx-control-inner-background: %s; -fx-text-fill: %s; -fx-border-color: %s;",
                    colors.get("secondary-bg"), colors.get("text-primary"), colors.get("border"));
            case "panel" -> style = String.format(
                    "-fx-background-color: %s; -fx-border-color: %s;",
                    colors.get("secondary-bg"), colors.get("border"));
            case "chart" -> style = String.format(
                    "-fx-background-color: %s;",
                    colors.get("primary-bg"));
        }

        // Apply opacity and contrast settings
        if (themeConfig.useHighContrast) {
            style += " -fx-font-weight: bold;";
        }

        return style;
    }

    /**
     * Save current theme configuration to disk
     */
    public void saveConfiguration() {
        try {
            File configDir = new File(System.getProperty("user.home") + "/.investpro");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            Properties props = new Properties();
            props.setProperty("theme", themeConfig.theme.name());
            props.setProperty("opacity", String.valueOf(themeConfig.opacity));
            props.setProperty("compact_layout", String.valueOf(themeConfig.useCompactLayout));
            props.setProperty("high_contrast", String.valueOf(themeConfig.useHighContrast));
            props.setProperty("accent_color", themeConfig.accentColor);

            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                props.store(fos, "InvestPro Theme Configuration");
                log.info("Theme configuration saved: {}", CONFIG_FILE);
            }
        } catch (IOException e) {
            log.error("Failed to save theme configuration", e);
        }
    }

    /**
     * Load theme configuration from disk
     */
    public void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);

                    String theme = props.getProperty("theme", "DARK_MODE");
                    themeConfig.setTheme(Theme.valueOf(theme));

                    String opacity = props.getProperty("opacity", "1.0");
                    themeConfig.setOpacity(Double.parseDouble(opacity));

                    String compact = props.getProperty("compact_layout", "false");
                    themeConfig.setCompactLayout(Boolean.parseBoolean(compact));

                    String contrast = props.getProperty("high_contrast", "false");
                    themeConfig.setHighContrast(Boolean.parseBoolean(contrast));

                    String accentColor = props.getProperty("accent_color", "#1e90ff");
                    themeConfig.setAccentColor(accentColor);

                    log.info("Theme configuration loaded: {}", theme);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load theme configuration, using defaults", e);
        }
    }

    /**
     * Get current theme configuration
     */
    public ThemeConfig getConfig() {
        return themeConfig;
    }

    /**
     * Set theme and optionally apply to a root region
     */
    public void setTheme(Theme theme, Region rootRegion) {
        themeConfig.setTheme(theme);
        if (rootRegion != null) {
            applyThemeRecursively(rootRegion);
        }
        saveConfiguration();
    }

    /**
     * Get all available themes
     */
    public Theme[] getAvailableThemes() {
        return Theme.values();
    }
}
