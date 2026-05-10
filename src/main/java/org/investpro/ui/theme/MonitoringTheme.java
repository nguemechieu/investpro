package org.investpro.ui.theme;

/**
 * Modern monitoring theme colors and styles.
 * Provides a cohesive color palette for all monitoring UI components.
 */
public final class MonitoringTheme {

    // ============ Color Palette ============

    // Status Colors - Semantic meanings
    public static final String HEALTHY = "#10b981"; // Emerald Green
    public static final String DEGRADED = "#f59e0b"; // Amber
    public static final String WARNING = "#f59e0b"; // Amber
    public static final String FAILED = "#ef4444"; // Red
    public static final String DISABLED = "#6b7280"; // Slate Gray
    public static final String UNKNOWN = "#6b7280"; // Slate Gray

    // Background Colors
    public static final String PRIMARY_BG = "#0f172a"; // Deep Blue-Black
    public static final String SECONDARY_BG = "#1e293b"; // Slate
    public static final String CARD_BG = "#1e293b"; // Slate (same as secondary)
    public static final String SURFACE_BG = "#0f172a"; // Deep (same as primary)

    // Text Colors
    public static final String TEXT_PRIMARY = "#f1f5f9"; // Light White
    public static final String TEXT_SECONDARY = "#cbd5e1"; // Light Gray
    public static final String TEXT_MUTED = "#64748b"; // Muted Gray

    // Accent Colors
    public static final String ACCENT_BLUE = "#3b82f6"; // Bright Blue
    public static final String ACCENT_CYAN = "#06b6d4"; // Cyan
    public static final String ACCENT_PURPLE = "#8b5cf6"; // Purple

    // Border Colors
    public static final String BORDER_LIGHT = "#334155"; // Slate Border
    public static final String BORDER_DARK = "#1e293b"; // Dark Border

    // ============ Font Sizing ============

    public static final int FONT_SMALL = 11;
    public static final int FONT_NORMAL = 12;
    public static final int FONT_MEDIUM = 13;
    public static final int FONT_LARGE = 14;
    public static final int FONT_EXTRA_LARGE = 16;
    public static final int FONT_TITLE = 18;
    public static final int FONT_HEADING = 20;

    // ============ Font Family ============

    public static final String FONT_SANS = "'Segoe UI', 'Helvetica Neue', sans-serif";
    public static final String FONT_MONO = "'Monaco', 'Courier New', monospace";

    // ============ Spacing ============

    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 12;
    public static final int SPACING_LG = 16;
    public static final int SPACING_XL = 24;

    // ============ Border Radius ============

    public static final int RADIUS_SM = 4;
    public static final int RADIUS_MD = 6;
    public static final int RADIUS_LG = 8;

    // ============ Component Styles ============

    /**
     * Get status color based on component status.
     */
    public static String getStatusColor(org.investpro.monitoring.ComponentStatus status) {
        if (status == null)
            return UNKNOWN;
        return switch (status) {
            case HEALTHY -> HEALTHY;
            case DEGRADED -> DEGRADED;
            case WARNING -> WARNING;
            case FAILED -> FAILED;
            case DISABLED -> DISABLED;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /**
     * Get status emoji based on component status.
     */
    public static String getStatusEmoji(org.investpro.monitoring.ComponentStatus status) {
        if (status == null)
            return "❓";
        return switch (status) {
            case HEALTHY -> "✅";
            case DEGRADED -> "⚠️";
            case WARNING -> "⚡";
            case FAILED -> "❌";
            case DISABLED -> "⊘";
            case UNKNOWN -> "❓";
        };
    }

    /**
     * Get CSS style string for status badge.
     */
    public static String getStatusBadgeStyle(org.investpro.monitoring.ComponentStatus status) {
        String color = getStatusColor(status);
        return String.format(
                "-fx-text-fill: %s; -fx-font-size: %dpx; -fx-font-weight: bold;",
                color, FONT_MEDIUM);
    }

    /**
     * Get CSS style string for status card.
     */
    public static String getStatusCardStyle(org.investpro.monitoring.ComponentStatus status) {
        String color = getStatusColor(status);
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2 0 0 0; " +
                        "-fx-border-radius: %d; " +
                        "-fx-background-radius: %d;",
                CARD_BG, color, RADIUS_MD, RADIUS_MD);
    }

    /**
     * Get CSS style string for metric card.
     */
    public static String getMetricCardStyle(String accentColor) {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2 0 0 0; " +
                        "-fx-border-radius: %d; " +
                        "-fx-background-radius: %d;",
                CARD_BG, accentColor, RADIUS_MD, RADIUS_MD);
    }

    /**
     * Get CSS style string for main container.
     */
    public static String getMainContainerStyle() {
        return "-fx-background-color: " + PRIMARY_BG + ";";
    }

    /**
     * Get CSS style string for secondary container.
     */
    public static String getSecondaryContainerStyle() {
        return "-fx-background-color: " + SECONDARY_BG + ";";
    }

    /**
     * Get CSS style string for label.
     */
    public static String getLabelStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + ";";
    }

    /**
     * Get CSS style string for secondary label.
     */
    public static String getSecondaryLabelStyle() {
        return "-fx-text-fill: " + TEXT_SECONDARY + ";";
    }

    /**
     * Get CSS style string for button.
     */
    public static String getButtonStyle() {
        return String.format(
                "-fx-text-fill: %s; " +
                        "-fx-background-color: %s; " +
                        "-fx-padding: %d %dpx; " +
                        "-fx-border-radius: %d;",
                TEXT_PRIMARY, ACCENT_BLUE, SPACING_SM, SPACING_MD, RADIUS_SM);
    }

    /**
     * Get complete global stylesheet CSS.
     */
    public static String getGlobalStylesheet() {
        return "data:text/css," +
                ".root { " +
                "  -fx-font-family: " + FONT_SANS + "; " +
                "  -fx-font-size: " + FONT_NORMAL + "px; " +
                "  -fx-base: " + SECONDARY_BG + "; " +
                "  -fx-control-inner-background: " + PRIMARY_BG + "; " +
                "  -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "} " +
                ".text-area { " +
                "  -fx-font-family: " + FONT_MONO + "; " +
                "  -fx-font-size: " + FONT_SMALL + "px; " +
                "  -fx-text-fill: " + TEXT_SECONDARY + "; " +
                "  -fx-control-inner-background: " + PRIMARY_BG + "; " +
                "  -fx-padding: " + SPACING_MD + "px; " +
                "} " +
                ".tab-pane .tab-header-background { " +
                "  -fx-background-color: " + SECONDARY_BG + "; " +
                "} " +
                ".tab-pane .tab { " +
                "  -fx-background-color: " + SECONDARY_BG + "; " +
                "  -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "} " +
                ".tab-pane .tab:selected { " +
                "  -fx-background-color: " + ACCENT_BLUE + "; " +
                "} " +
                ".button { " +
                "  -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "  -fx-background-color: " + ACCENT_BLUE + "; " +
                "  -fx-padding: " + SPACING_SM + "px " + SPACING_MD + "px; " +
                "  -fx-font-size: " + FONT_NORMAL + "px; " +
                "  -fx-border-radius: " + RADIUS_SM + "; " +
                "  -fx-cursor: hand; " +
                "} " +
                ".button:hover { " +
                "  -fx-background-color: " + ACCENT_CYAN + "; " +
                "} " +
                ".button:pressed { " +
                "  -fx-background-color: #2563eb; " +
                "} " +
                ".label { " +
                "  -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "} " +
                ".scroll-pane { " +
                "  -fx-background-color: " + PRIMARY_BG + "; " +
                "} " +
                ".scroll-bar { " +
                "  -fx-background-color: " + SECONDARY_BG + "; " +
                "  -fx-padding: " + SPACING_XS + "px; " +
                "} " +
                ".scroll-bar:vertical .thumb { " +
                "  -fx-background-color: " + ACCENT_BLUE + "; " +
                "  -fx-background-radius: 5; " +
                "} " +
                ".scroll-bar:vertical .thumb:hover { " +
                "  -fx-background-color: " + ACCENT_CYAN + "; " +
                "} ";
    }

    public MonitoringTheme() {
        // Utility class
    }
}
