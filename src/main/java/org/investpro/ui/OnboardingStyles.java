package org.investpro.ui;

/**
 * Centralized styling configuration for OnboardingView.
 * All input field styles, colors, fonts, and layouts are defined here.
 * 
 * @author NOEL NGUEMECHIEU
 */
public final class OnboardingStyles {
    
    // =====================================================================
    // Colors
    // =====================================================================
    
    public static final String COLOR_DARK_BG = "#0f172a";
    public static final String COLOR_SURFACE_BG = "#1e293b";
    public static final String COLOR_PRIMARY_TEXT = "#f1f5f9";
    public static final String COLOR_SECONDARY_TEXT = "#cbd5e1";
    public static final String COLOR_MUTED_TEXT = "#94a3b8";
    public static final String COLOR_BORDER = "#475569";
    
    public static final String COLOR_PRIMARY_BUTTON = "#3b82f6";
    public static final String COLOR_SECONDARY_BUTTON = "#1e40af";
    public static final String COLOR_SUCCESS_BUTTON = "#10b981";
    
    public static final String COLOR_ERROR = "#ef4444";
    public static final String COLOR_WARNING = "#f59e0b";
    
    // =====================================================================
    // Font Sizes
    // =====================================================================
    
    public static final int FONT_TITLE_LARGE = 44;      // App name
    public static final int FONT_TITLE = 32;             // Step titles
    public static final int FONT_SUBTITLE = 16;          // Prompts
    public static final int FONT_BODY = 12;              // Button text
    public static final int FONT_SMALL = 11;             // Default text
    
    // =====================================================================
    // Input Field Styles
    // =====================================================================
    
    public static final String TEXT_FIELD_STYLE = String.format(
            "-fx-control-inner-background: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-prompt-text-fill: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1; " +
            "-fx-padding: 8; " +
            "-fx-font-size: %dpt;",
            COLOR_SURFACE_BG, COLOR_PRIMARY_TEXT, COLOR_MUTED_TEXT, COLOR_BORDER, FONT_SMALL
    );
    
    public static final String TEXT_FIELD_FOCUSED_STYLE = String.format(
            "-fx-control-inner-background: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-prompt-text-fill: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 2; " +
            "-fx-padding: 7; " +
            "-fx-font-size: %dpt;",
            COLOR_SURFACE_BG, COLOR_PRIMARY_TEXT, COLOR_MUTED_TEXT, COLOR_PRIMARY_BUTTON, FONT_SMALL
    );
    
    // =====================================================================
    // Button Styles
    // =====================================================================
    
    public static final String PRIMARY_BUTTON_STYLE = String.format(
            "-fx-padding: 10 20; " +
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: %dpt; " +
            "-fx-font-weight: bold; " +
            "-fx-border-radius: 4; " +
            "-fx-cursor: hand;",
            COLOR_PRIMARY_BUTTON, FONT_BODY
    );
    
    public static final String SECONDARY_BUTTON_STYLE = String.format(
            "-fx-padding: 10 20; " +
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: %dpt; " +
            "-fx-font-weight: bold; " +
            "-fx-border-radius: 4; " +
            "-fx-cursor: hand;",
            COLOR_SECONDARY_BUTTON, FONT_BODY
    );
    
    public static final String SUCCESS_BUTTON_STYLE = String.format(
            "-fx-padding: 10 20; " +
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: %dpt; " +
            "-fx-font-weight: bold; " +
            "-fx-border-radius: 4; " +
            "-fx-cursor: hand;",
            COLOR_SUCCESS_BUTTON, FONT_BODY
    );
    
    public static final String SMALL_BUTTON_STYLE = String.format(
            "-fx-padding: 4 12; " +
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-cursor: hand;",
            COLOR_SECONDARY_BUTTON
    );
    
    // =====================================================================
    // Label Styles
    // =====================================================================
    
    public static final String TITLE_LABEL_STYLE = String.format(
            "-fx-font-size: %dpt; " +
            "-fx-font-weight: 700; " +
            "-fx-text-fill: %s;",
            FONT_TITLE_LARGE, COLOR_PRIMARY_BUTTON
    );
    
    public static final String SUBTITLE_LABEL_STYLE = String.format(
            "-fx-font-size: %dpt; " +
            "-fx-text-fill: %s;",
            FONT_SUBTITLE, COLOR_PRIMARY_BUTTON
    );
    
    public static final String PROMPT_LABEL_STYLE = String.format(
            "-fx-font-size: %dpt; " +
            "-fx-text-fill: %s;",
            FONT_SUBTITLE, COLOR_SECONDARY_TEXT
    );
    
    public static final String BODY_LABEL_STYLE = String.format(
            "-fx-font-size: %dpt; " +
            "-fx-text-fill: %s;",
            FONT_SMALL, COLOR_PRIMARY_TEXT
    );
    
    public static final String ERROR_LABEL_STYLE = String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: %dpt;",
            COLOR_ERROR, FONT_BODY
    );
    
    // =====================================================================
    // CheckBox Styles
    // =====================================================================
    
    public static final String CHECKBOX_STYLE = String.format(
            "-fx-text-fill: %s;",
            COLOR_PRIMARY_TEXT
    );
    
    // =====================================================================
    // ComboBox Styles
    // =====================================================================
    
    public static final String COMBO_BOX_STYLE = String.format(
            "-fx-control-inner-background: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1; " +
            "-fx-padding: 8; " +
            "-fx-font-size: %dpt;",
            COLOR_SURFACE_BG, COLOR_PRIMARY_TEXT, COLOR_BORDER, FONT_SMALL
    );
    
    // =====================================================================
    // Layout Constants
    // =====================================================================
    
    public static final double WINDOW_WIDTH = 1540;
    public static final double WINDOW_HEIGHT = 780;
    public static final double FORM_MAX_WIDTH = 360;
    public static final double LEFT_PADDING = 120;
    public static final double FORM_SPACING = 12;
    public static final double SECTION_SPACING = 18;
    public static final double GRID_H_GAP = 14;
    public static final double GRID_V_GAP = 14;
    
    public static final double LOGO_SIZE = 80;
    public static final double LOADING_OVERLAY_WIDTH = 520;
    public static final double LOADING_OVERLAY_HEIGHT = 240;
    
    // =====================================================================
    // Pane Styles
    // =====================================================================
    
    public static final String DARK_PANE_STYLE = String.format(
            "-fx-background-color: %s;",
            COLOR_DARK_BG
    );
    
    public static final String LOADING_PANE_STYLE = String.format(
            "-fx-background-color: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-border-width: 1;",
            COLOR_SURFACE_BG, COLOR_BORDER
    );
    
    public static final String LOADING_OVERLAY_BG_STYLE = "-fx-background-color: rgba(15, 23, 42, 0.85);";
    
    private OnboardingStyles() {
        throw new AssertionError("Utility class - no instantiation");
    }
}
