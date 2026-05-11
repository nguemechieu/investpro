package org.investpro.ui.theme;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.investpro.config.AppConfig;
import java.util.*;

/**
 * Theme configuration that can be loaded from settings or environment
 * variables.
 * Users can customize colors, fonts, spacing, and other theme properties.
 */
@Data
@NoArgsConstructor
public class ThemeConfig {
    // =====================================================================
    // Core Colors
    // =====================================================================
    private String darkBg = AppConfig.get("THEME_DARK_BG", "#070b12");
    private String workspaceBg = AppConfig.get("THEME_WORKSPACE_BG", "#0b1120");
    private String surfaceBg = AppConfig.get("THEME_SURFACE_BG", "#111827");
    private String panelBg = AppConfig.get("THEME_PANEL_BG", "#151f2e");
    private String elevatedBg = AppConfig.get("THEME_ELEVATED_BG", "#1f2937");
    private String headerBg = AppConfig.get("THEME_HEADER_BG", "#243244");
    private String terminalBg = AppConfig.get("THEME_TERMINAL_BG", "#0f172a");

    // =====================================================================
    // Component Colors
    // =====================================================================
    private String btnBg = AppConfig.get("THEME_BTN_BG", "#141f31");
    private String inputBg = AppConfig.get("THEME_INPUT_BG", "#0f172a");
    private String tableBg = AppConfig.get("THEME_TABLE_BG", "#020617");
    private String tableAltBg = AppConfig.get("THEME_TABLE_ALT_BG", "#08111f");
    private String tableHoverBg = AppConfig.get("THEME_TABLE_HOVER_BG", "#1e293b");
    private String tableHeaderBg = AppConfig.get("THEME_TABLE_HEADER_BG", "#1e293b");

    // =====================================================================
    // Borders
    // =====================================================================
    private String borderColor = AppConfig.get("THEME_BORDER_COLOR", "#334155");
    private String borderStrong = AppConfig.get("THEME_BORDER_STRONG", "#475569");
    private String borderSoft = AppConfig.get("THEME_BORDER_SOFT", "rgba(148, 163, 184, 0.22)");
    private String borderRadius = AppConfig.get("THEME_BORDER_RADIUS", "4px");
    private String borderRadiusLg = AppConfig.get("THEME_BORDER_RADIUS_LG", "8px");

    // =====================================================================
    // Text Colors
    // =====================================================================
    private String textPrimary = AppConfig.get("THEME_TEXT_PRIMARY", "#f8fafc");
    private String textSecondary = AppConfig.get("THEME_TEXT_SECONDARY", "#dbeafe");
    private String textMuted = AppConfig.get("THEME_TEXT_MUTED", "#94a3b8");
    private String textFaint = AppConfig.get("THEME_TEXT_FAINT", "#64748b");
    private String textHeader = AppConfig.get("THEME_TEXT_HEADER", "#e0f2fe");

    // =====================================================================
    // Trading Colors
    // =====================================================================
    private String buyColor = AppConfig.get("THEME_BUY_COLOR", "#16a34a");
    private String buyHover = AppConfig.get("THEME_BUY_HOVER", "#22c55e");
    private String buyLight = AppConfig.get("THEME_BUY_LIGHT", "#4ade80");
    private String sellColor = AppConfig.get("THEME_SELL_COLOR", "#dc2626");
    private String sellHover = AppConfig.get("THEME_SELL_HOVER", "#ef4444");
    private String sellLight = AppConfig.get("THEME_SELL_LIGHT", "#f87171");
    private String profitColor = AppConfig.get("THEME_PROFIT_COLOR", "#22c55e");
    private String lossColor = AppConfig.get("THEME_LOSS_COLOR", "#f87171");
    private String neutralColor = AppConfig.get("THEME_NEUTRAL_COLOR", "#64748b");

    // =====================================================================
    // Accent Colors
    // =====================================================================
    private String primaryColor = AppConfig.get("THEME_PRIMARY_COLOR", "#2563eb");
    private String primaryLight = AppConfig.get("THEME_PRIMARY_LIGHT", "#60a5fa");
    private String primaryDark = AppConfig.get("THEME_PRIMARY_DARK", "#1d4ed8");
    private String accentColor = AppConfig.get("THEME_ACCENT_COLOR", "#f59e0b");
    private String accentLight = AppConfig.get("THEME_ACCENT_LIGHT", "#fde68a");
    private String warningColor = AppConfig.get("THEME_WARNING_COLOR", "#fbbf24");
    private String infoColor = AppConfig.get("THEME_INFO_COLOR", "#38bdf8");
    private String dangerColor = AppConfig.get("THEME_DANGER_COLOR", "#ef4444");
    private String successColor = AppConfig.get("THEME_SUCCESS_COLOR", "#22c55e");

    // =====================================================================
    // Chart Colors
    // =====================================================================
    private String chartBg = AppConfig.get("THEME_CHART_BG", "#050914");
    private String chartGrid = AppConfig.get("THEME_CHART_GRID", "rgba(148, 163, 184, 0.18)");
    private String chartAxis = AppConfig.get("THEME_CHART_AXIS", "#cbd5e1");
    private String chartBorder = AppConfig.get("THEME_CHART_BORDER", "#334155");

    // =====================================================================
    // Typography
    // =====================================================================
    private String fontFamily = AppConfig.get("THEME_FONT_FAMILY", "\"Segoe UI\", \"Inter\", \"Arial\", sans-serif");
    private String fontSize = AppConfig.get("THEME_FONT_SIZE", "12px");
    private String fontSizeSm = AppConfig.get("THEME_FONT_SIZE_SM", "11px");
    private String fontSizeLg = AppConfig.get("THEME_FONT_SIZE_LG", "13px");
    private String fontWeight = AppConfig.get("THEME_FONT_WEIGHT", "700");

    // =====================================================================
    // Spacing & Sizing
    // =====================================================================
    private String spacing = AppConfig.get("THEME_SPACING", "8px");
    private String paddingSmall = AppConfig.get("THEME_PADDING_SMALL", "4 8 4 8");
    private String paddingMedium = AppConfig.get("THEME_PADDING_MEDIUM", "8 16 8 16");
    private String paddingLarge = AppConfig.get("THEME_PADDING_LARGE", "12 24 12 24");

    /**
     * Load theme from .env file or use defaults
     */
    public static ThemeConfig loadFromConfig() {
        return new ThemeConfig();
    }

    /**
     * Export current theme as CSS variable definitions
     */
    public String toCSSVariables() {
        StringBuilder css = new StringBuilder(".root {\n");
        css.append("    /* Core Backgrounds */\n");
        css.append("    -dark-bg: ").append(darkBg).append(";\n");
        css.append("    -workspace-bg: ").append(workspaceBg).append(";\n");
        css.append("    -surface-bg: ").append(surfaceBg).append(";\n");
        css.append("    -panel-bg: ").append(panelBg).append(";\n");
        css.append("    -elevated-bg: ").append(elevatedBg).append(";\n");
        css.append("    -header-bg: ").append(headerBg).append(";\n");
        css.append("    -terminal-bg: ").append(terminalBg).append(";\n");

        css.append("\n    /* Text Colors */\n");
        css.append("    -text-primary: ").append(textPrimary).append(";\n");
        css.append("    -text-secondary: ").append(textSecondary).append(";\n");
        css.append("    -text-muted: ").append(textMuted).append(";\n");

        css.append("\n    /* Trading Colors */\n");
        css.append("    -buy-color: ").append(buyColor).append(";\n");
        css.append("    -sell-color: ").append(sellColor).append(";\n");
        css.append("    -profit-color: ").append(profitColor).append(";\n");

        css.append("\n    /* Accent Colors */\n");
        css.append("    -primary-color: ").append(primaryColor).append(";\n");
        css.append("    -accent-color: ").append(accentColor).append(";\n");
        css.append("    -warning: ").append(warningColor).append(";\n");

        css.append("}\n");
        return css.toString();
    }

    /**
     * Get theme as map for template rendering
     */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("darkBg", darkBg);
        map.put("workspaceBg", workspaceBg);
        map.put("surfaceBg", surfaceBg);
        map.put("panelBg", panelBg);
        map.put("textPrimary", textPrimary);
        map.put("textSecondary", textSecondary);
        map.put("buyColor", buyColor);
        map.put("sellColor", sellColor);
        map.put("primaryColor", primaryColor);
        map.put("accentColor", accentColor);
        map.put("fontFamily", fontFamily);
        map.put("fontSize", fontSize);
        return map;
    }
}
