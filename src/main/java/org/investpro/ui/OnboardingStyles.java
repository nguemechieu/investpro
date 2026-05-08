package org.investpro.ui;

/**
 * Centralized styling configuration for OnboardingView.
 *
 * This class defines shared colors, fonts, layouts, and reusable JavaFX inline
 * styles used by the onboarding screen.
 *
 * @author NOEL NGUEMECHIEU
 */
public final class OnboardingStyles {

    // =====================================================================
    // Colors
    // =====================================================================

    public static final String COLOR_DARK_BG = "#0f172a";
    public static final String COLOR_SURFACE_BG = "#1e293b";
    public static final String COLOR_SURFACE_BG_LIGHT = "#263449";

    public static final String COLOR_PRIMARY_TEXT = "#f1f5f9";
    public static final String COLOR_SECONDARY_TEXT = "#cbd5e1";
    public static final String COLOR_MUTED_TEXT = "#94a3b8";
    public static final String COLOR_BORDER = "#475569";

    public static final String COLOR_PRIMARY_BUTTON = "#3b82f6";
    public static final String COLOR_PRIMARY_BUTTON_HOVER = "#2563eb";
    public static final String COLOR_SECONDARY_BUTTON = "#1e40af";
    public static final String COLOR_SECONDARY_BUTTON_HOVER = "#1d4ed8";
    public static final String COLOR_SUCCESS_BUTTON = "#10b981";
    public static final String COLOR_SUCCESS_BUTTON_HOVER = "#059669";

    public static final String COLOR_ERROR = "#ef4444";
    public static final String COLOR_WARNING = "#f59e0b";
    public static final String COLOR_INFO = "#38bdf8";

    // =====================================================================
    // Font Sizes
    // =====================================================================

    public static final int FONT_TITLE_LARGE = 44;
    public static final int FONT_TITLE = 32;
    public static final int FONT_SUBTITLE = 16;
    public static final int FONT_BODY = 12;
    public static final int FONT_SMALL = 11;

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

    public OnboardingStyles() {
        throw new AssertionError("Utility class - no instantiation");
    }

    // =====================================================================
    // Style Builders
    // =====================================================================

    public static String textFieldStyle() {
        return textFieldStyle(COLOR_BORDER, 1, 8);
    }

    public static String focusedTextFieldStyle() {
        return textFieldStyle(COLOR_PRIMARY_BUTTON, 2, 7);
    }

    private static String textFieldStyle(String borderColor, int borderWidth, int padding) {
        return """
                -fx-control-inner-background: %s;
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-border-color: %s;
                -fx-border-width: %d;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-padding: %d;
                -fx-font-size: %dpt;
                """.formatted(
                COLOR_SURFACE_BG,
                COLOR_SURFACE_BG,
                COLOR_PRIMARY_TEXT,
                COLOR_MUTED_TEXT,
                borderColor,
                borderWidth,
                padding,
                FONT_SMALL
        );
    }

    public static String buttonStyle(String backgroundColor, int fontSize) {
        return """
                -fx-padding: 10 20;
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-size: %dpt;
                -fx-font-weight: bold;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-cursor: hand;
                """.formatted(backgroundColor, fontSize);
    }

    public static String labelStyle(int fontSize, String color, String weight) {
        String fontWeight = weight == null || weight.isBlank()
                ? ""
                : "-fx-font-weight: %s;".formatted(weight);

        return """
                -fx-font-size: %dpt;
                -fx-text-fill: %s;
                %s
                """.formatted(fontSize, color, fontWeight);
    }

    public static String paneStyle(String backgroundColor) {
        return "-fx-background-color: %s;".formatted(backgroundColor);
    }

    public static String cardStyle() {
        return """
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-radius: 10;
                -fx-background-radius: 10;
                -fx-border-width: 1;
                -fx-padding: 18;
                """.formatted(COLOR_SURFACE_BG, COLOR_BORDER);
    }

    // =====================================================================
    // Input Field Styles
    // =====================================================================

    public static final String TEXT_FIELD_STYLE = textFieldStyle();

    public static final String TEXT_FIELD_FOCUSED_STYLE = focusedTextFieldStyle();

    // =====================================================================
    // Button Styles
    // =====================================================================

    public static final String PRIMARY_BUTTON_STYLE = buttonStyle(COLOR_PRIMARY_BUTTON, FONT_BODY);

    public static final String PRIMARY_BUTTON_HOVER_STYLE = buttonStyle(COLOR_PRIMARY_BUTTON_HOVER, FONT_BODY);

    public static final String SECONDARY_BUTTON_STYLE = buttonStyle(COLOR_SECONDARY_BUTTON, FONT_BODY);

    public static final String SECONDARY_BUTTON_HOVER_STYLE = buttonStyle(COLOR_SECONDARY_BUTTON_HOVER, FONT_BODY);

    public static final String SUCCESS_BUTTON_STYLE = buttonStyle(COLOR_SUCCESS_BUTTON, FONT_BODY);

    public static final String SUCCESS_BUTTON_HOVER_STYLE = buttonStyle(COLOR_SUCCESS_BUTTON_HOVER, FONT_BODY);

    public static final String SMALL_BUTTON_STYLE = """
            -fx-padding: 4 12;
            -fx-background-color: %s;
            -fx-text-fill: white;
            -fx-border-radius: 5;
            -fx-background-radius: 5;
            -fx-cursor: hand;
            """.formatted(COLOR_SECONDARY_BUTTON);

    // =====================================================================
    // Label Styles
    // =====================================================================

    public static final String TITLE_LABEL_STYLE =
            labelStyle(FONT_TITLE_LARGE, COLOR_PRIMARY_BUTTON, "700");

    public static final String STEP_TITLE_LABEL_STYLE =
            labelStyle(FONT_TITLE, COLOR_PRIMARY_TEXT, "700");

    public static final String SUBTITLE_LABEL_STYLE =
            labelStyle(FONT_SUBTITLE, COLOR_PRIMARY_BUTTON, "");

    public static final String PROMPT_LABEL_STYLE =
            labelStyle(FONT_SUBTITLE, COLOR_SECONDARY_TEXT, "");

    public static final String BODY_LABEL_STYLE =
            labelStyle(FONT_SMALL, COLOR_PRIMARY_TEXT, "");

    public static final String MUTED_LABEL_STYLE =
            labelStyle(FONT_SMALL, COLOR_MUTED_TEXT, "");

    public static final String ERROR_LABEL_STYLE =
            labelStyle(FONT_BODY, COLOR_ERROR, "");

    public static final String WARNING_LABEL_STYLE =
            labelStyle(FONT_BODY, COLOR_WARNING, "");

    public static final String INFO_LABEL_STYLE =
            labelStyle(FONT_BODY, COLOR_INFO, "");

    // =====================================================================
    // CheckBox Styles
    // =====================================================================

    public static final String CHECKBOX_STYLE = """
            -fx-text-fill: %s;
            """.formatted(COLOR_PRIMARY_TEXT);

    // =====================================================================
    // ComboBox Styles
    // =====================================================================

    public static final String COMBO_BOX_STYLE = """
            -fx-control-inner-background: %s;
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-border-color: %s;
            -fx-border-width: 1;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 8;
            -fx-font-size: %dpt;
            """.formatted(
            COLOR_SURFACE_BG,
            COLOR_SURFACE_BG,
            COLOR_PRIMARY_TEXT,
            COLOR_BORDER,
            FONT_SMALL
    );

    // =====================================================================
    // Pane Styles
    // =====================================================================

    public static final String DARK_PANE_STYLE = paneStyle(COLOR_DARK_BG);

    public static final String SURFACE_PANE_STYLE = paneStyle(COLOR_SURFACE_BG);

    public static final String CARD_STYLE = cardStyle();

    public static final String LOADING_PANE_STYLE = """
            -fx-background-color: %s;
            -fx-border-color: %s;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-border-width: 1;
            """.formatted(COLOR_SURFACE_BG, COLOR_BORDER);

    public static final String LOADING_OVERLAY_BG_STYLE =
            "-fx-background-color: rgba(15, 23, 42, 0.85);";
}