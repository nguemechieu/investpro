# Theme Customization System - User Guide

## Overview

InvestPro now includes a comprehensive **theme customization system** that allows you to adjust all styling properties without touching code. Every color, font, spacing, and border radius can be customized through an intuitive UI and saved to configuration files.

## How to Access Theme Customization

1. **Open the Application**
2. **Go to Tools Menu** → Select **"Theme Customization"**
3. A dedicated window opens with three tabs: **Colors**, **Typography**, and **Export**

## Features

### 1. **Colors Tab** 🎨
Customize all colors used in the application through interactive color pickers:

#### Background Colors
- **Dark Background**: Main dark color for app background
- **Panel Background**: Color for panel containers
- **Surface Background**: Secondary container color
- **Elevated Background**: Higher elevation component color

#### Trading Colors
- **Buy Color**: Color for buy signals and positive indicators
- **Sell Color**: Color for sell signals and negative indicators
- **Profit Color**: Color highlighting profit amounts
- **Loss Color**: Color highlighting loss amounts

#### Text Colors
- **Primary Text**: Main text color (headers, labels)
- **Secondary Text**: Supporting text color
- **Muted Text**: Faded/disabled text color

#### Accent Colors
- **Primary Color**: Main accent color for buttons and highlights
- **Warning Color**: Warning/attention indicators
- **Success Color**: Success state indicators

**How to use:**
1. Click the color picker next to any color setting
2. Select your desired color from the color palette
3. See the hex value update instantly
4. Changes preview in real-time

### 2. **Typography Tab** 📝
Customize fonts, sizes, and spacing:

#### Font Settings
- **Font Family**: Choose from Segoe UI, Inter, Arial, Courier New, Consolas, or Monaco
- **Base Font Size**: Default size for regular text (8-20px)
- **Small Font Size**: Size for small text/captions (8-20px)
- **Large Font Size**: Size for emphasized text (8-20px)

#### Spacing
- **Component Spacing**: Padding and gap between elements (4-24px)

**How to use:**
1. Use sliders to adjust sizes (shows current value)
2. Select font family from dropdown
3. See preview updates as you adjust

### 3. **Export Tab** 💾
Save and export your custom theme:

#### Current Theme Preview
- See all customized colors as swatches
- Preview how text looks with current theme
- Quick visual validation before saving

#### Export Options

**Option A: Export as CSS**
```
Click "Export as CSS"
→ Saves theme as CSS variables file to Downloads/theme-custom.css
→ Can be imported into other projects
```

**Option B: Save to .env**
```
Click "Save to .env"
→ Saves theme settings to project's .env file
→ Settings persist across application restarts
→ Environment variables format for easy CI/CD integration
```

#### Additional Controls
- **Reset to Defaults**: Restore all settings to factory defaults (with confirmation)
- **Apply Theme**: Apply current theme to the running application

## Configuration Files

### .env File Configuration

Theme settings are stored in `.env` file with the following structure:

```bash
# ============================================
# Theme Customization
# ============================================

# Core background colors
THEME_DARK_BG=#070b12
THEME_WORKSPACE_BG=#0b1120
THEME_SURFACE_BG=#111827
THEME_PANEL_BG=#151f2e

# Text colors
THEME_TEXT_PRIMARY=#f8fafc
THEME_TEXT_SECONDARY=#dbeafe
THEME_TEXT_MUTED=#94a3b8

# Trading colors
THEME_BUY_COLOR=#16a34a
THEME_SELL_COLOR=#dc2626
THEME_PROFIT_COLOR=#22c55e

# Accent colors
THEME_PRIMARY_COLOR=#2563eb
THEME_ACCENT_COLOR=#f59e0b
THEME_WARNING_COLOR=#fbbf24

# Typography
THEME_FONT_FAMILY="Segoe UI", "Inter", "Arial", sans-serif
THEME_FONT_SIZE=12px
THEME_FONT_WEIGHT=700

# Spacing
THEME_SPACING=8px
```

### ThemeConfig.java

The `ThemeConfig` class automatically loads settings from `.env` file:

```java
ThemeConfig theme = ThemeConfig.loadFromConfig();
String buyColor = theme.getBuyColor();        // Get current color
theme.setBuyColor("#00ff00");                 // Update color
String css = theme.toCSSVariables();          // Export as CSS
```

## CSS Organization

All inline styles have been extracted into organized CSS files:

### app.css
- Main stylesheet with color variables and base styles
- Global styling rules
- Theme token definitions

### components.css (NEW)
- Extracted component-specific styles
- Classes for: buttons, panels, headers, indicators, etc.
- Clean separation of concerns

**Example CSS Classes:**
```css
.chart-header { ... }
.tab-close-button { ... }
.panel-container { ... }
.indicator-value-label { ... }
.settings-primary-button { ... }
```

## Workflow: Customizing Your Theme

### Quick Customization (5 minutes)
```
1. Tools → Theme Customization
2. Click on Colors tab
3. Adjust 2-3 key colors (Primary, Buy, Sell)
4. Click "Apply Theme"
5. Done! Changes are immediate
```

### Full Theme Design (15-20 minutes)
```
1. Colors Tab:
   - Adjust all background colors for your preferred tone
   - Set text colors for readability
   - Customize trading colors for your preference
   
2. Typography Tab:
   - Select preferred font family
   - Adjust base font size to preference
   - Set spacing for density preference
   
3. Export Tab:
   - Review color preview
   - Save to .env for persistence
   - Export as CSS if needed
```

### Sharing Themes

**To share your custom theme:**
1. Export as CSS from the Export tab
2. Share the `theme-custom.css` file
3. Others can import it or convert values to .env

**To apply someone's theme:**
1. Get their theme values (CSS or .env format)
2. Update your `.env` file with their values
3. Restart the application

## Environment Variables Reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `THEME_DARK_BG` | `#070b12` | Main background |
| `THEME_PANEL_BG` | `#151f2e` | Panel containers |
| `THEME_TEXT_PRIMARY` | `#f8fafc` | Main text color |
| `THEME_BUY_COLOR` | `#16a34a` | Buy signals |
| `THEME_SELL_COLOR` | `#dc2626` | Sell signals |
| `THEME_PRIMARY_COLOR` | `#2563eb` | Primary accent |
| `THEME_WARNING_COLOR` | `#fbbf24` | Warnings |
| `THEME_FONT_SIZE` | `12px` | Base font size |
| `THEME_SPACING` | `8px` | Component gaps |

*Full list of 50+ variables in .env file*

## Troubleshooting

### Theme changes don't persist
- Make sure to click "Save to .env"
- Check that .env file has write permissions
- Restart application to load saved values

### Colors look wrong
- Verify hex color values in .env are valid (#RRGGBB format)
- Check that color picker is using correct format
- Use "Reset to Defaults" and reconfigure

### Application doesn't load with custom theme
- Open .env and look for syntax errors
- Try resetting to defaults
- Check application logs for errors

### Want to revert to factory defaults
- Click "Reset to Defaults" in Theme Customization
- Or manually restore original values in .env
- Restart application

## Technical Details

### How It Works

1. **Startup**: Application loads theme from `.env` via `AppConfig`
2. **UI**: `ThemeCustomizationPanel` displays current values from `ThemeConfig`
3. **Editing**: Users adjust colors/fonts through interactive controls
4. **Export**: Settings can be saved back to `.env` or exported as CSS
5. **Application**: CSS uses custom properties that reference .env values

### Architecture

```
.env file
    ↓
AppConfig (loads values)
    ↓
ThemeConfig (manages properties)
    ↓
ThemeCustomizationPanel (UI for adjustment)
    ↓
CSS Variables (applied to components)
```

## CSS Classes Available

All components have been refactored to use CSS classes:

- `.chart-header` - Chart header container
- `.panel-container` - Generic panel styling
- `.analysis-panel` - Analysis panel specific
- `.settings-primary-button` - Primary buttons
- `.indicator-value-label` - Technical indicator values
- `.market-info-link` - Hyperlinks in market info
- `.news-calendar-pill-*` - News calendar pills

## Future Enhancements

- [ ] Theme presets (Dark, Light, High Contrast)
- [ ] Theme import/export dialog with file picker
- [ ] Real-time theme switching without restart
- [ ] Theme history/undo
- [ ] Color harmony suggestions
- [ ] Accessibility contrast checker

## Support

For issues or questions about theme customization:
1. Check the `.env` file format
2. Verify all hex colors are valid
3. Check application logs (Tools → Activity Monitor)
4. Review this guide's troubleshooting section

---

**Version**: 1.0  
**Last Updated**: May 11, 2026  
**Status**: Production Ready ✅
