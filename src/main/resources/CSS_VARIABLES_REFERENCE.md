/*******************************************************************************
 * CSS VARIABLES QUICK REFERENCE
 * Complete list of all available CSS custom properties (variables)
 * Use these in any CSS rule with: -fx-background-color: -variable-name;
 ******************************************************************************/

/* ============================================================================
   COLOR PALETTE - BACKGROUNDS
   ============================================================================ */

-dark-bg                    /* Main dark background #070b12 */
-workspace-bg              /* Workspace area #0b1120 */
-surface-bg                /* Surface elements #111827 */
-panel-bg                  /* Panel backgrounds #151f2e */
-elevated-bg               /* Elevated/floating #1f2937 */
-header-bg                 /* Headers #243244 */
-terminal-bg               /* Terminal console #0f172a */

/* Component backgrounds */
-btn-bg                    /* Button background #141f31 */
-input-bg                  /* Input field background #0f172a */
-table-bg                  /* Table background #020617 */
-table-alt-bg              /* Table alternate row #08111f */
-table-hover-bg            /* Table row hover #1e293b */
-table-header-bg           /* Table header background #1e293b */

/* ============================================================================
   COLOR PALETTE - TRADING COLORS
   ============================================================================ */

-buy-color                 /* Buy action (dark green) #16a34a */
-buy-hover                 /* Buy hover state #22c55e */
-buy-light                 /* Buy light variant #4ade80 */

-sell-color                /* Sell action (dark red) #dc2626 */
-sell-hover                /* Sell hover state #ef4444 */
-sell-light                /* Sell light variant #f87171 */

-profit-color              /* Profit/gains (green) #22c55e */
-loss-color                /* Loss/declines (red) #f87171 */
-neutral-color             /* Neutral state (gray) #64748b */

/* ============================================================================
   COLOR PALETTE - STATUS COLORS
   ============================================================================ */

-primary-color             /* Primary action (blue) #2563eb */
-primary-light             /* Primary light (light blue) #60a5fa */
-primary-dark              /* Primary dark (dark blue) #1d4ed8 */

-accent-color              /* Accent color (amber) #f59e0b */
-accent-light              /* Accent light (light amber) #fde68a */

-warning                   /* Warning state #fbbf24 */
-info                      /* Info state (cyan) #38bdf8 */
-danger                    /* Danger state (red) #ef4444 */
-success                   /* Success state (green) #22c55e */

/* ============================================================================
   COLOR PALETTE - TEXT
   ============================================================================ */

-text-primary              /* Primary text (light) #f8fafc */
-text-secondary            /* Secondary text (lighter blue) #dbeafe */
-text-muted                /* Muted text (gray) #94a3b8 */
-text-faint                /* Faint text (darker gray) #64748b */
-text-header               /* Header text (light blue) #e0f2fe */

/* ============================================================================
   COLOR PALETTE - CHART COLORS
   ============================================================================ */

-chart-bg                  /* Chart background #050914 */
-chart-grid                /* Chart grid lines (rgba) */
-chart-axis                /* Chart axis labels #cbd5e1 */
-chart-border              /* Chart border #334155 */

/* ============================================================================
   COLOR PALETTE - BORDERS
   ============================================================================ */

-border-color              /* Default border #334155 */
-border-strong             /* Strong/emphasized border #475569 */
-border-soft               /* Subtle border (rgba) */

/* ============================================================================
   SIZING & SPACING
   ============================================================================ */

-border-radius             /* Standard border radius 4px */
-border-radius-lg          /* Large border radius 8px */

/* ============================================================================
   TYPOGRAPHY
   ============================================================================ */

-fx-font-family            /* "Segoe UI", "Inter", "Arial", sans-serif */

-fx-font-size              /* Base font size 12px */
-fx-font-size-sm           /* Small font size 11px */
-fx-font-size-lg           /* Large font size 13px */

-fx-font-weight            /* Base font weight 700 (bold) */

/* ============================================================================
   USAGE EXAMPLES
   ============================================================================ */

/*
Example 1: Using background color
.custom-panel {
    -fx-background-color: -panel-bg;
}

Example 2: Using text color
.custom-label {
    -fx-text-fill: -text-secondary;
}

Example 3: Using border
.custom-button {
    -fx-border-color: -border-strong;
    -fx-border-width: 1;
}

Example 4: Using trading colors
.buy-indicator {
    -fx-text-fill: -buy-color;
}

.sell-indicator {
    -fx-text-fill: -sell-color;
}

Example 5: Using status colors
.profit-cell {
    -fx-text-fill: -profit-color;
}

.loss-cell {
    -fx-text-fill: -loss-color;
}

Example 6: Complex gradient with variables
.header {
    -fx-background-color: linear-gradient(to bottom, -header-bg, -terminal-bg);
    -fx-border-color: -border-color;
    -fx-border-width: 0 0 1 0;
}

Example 7: Using typography
.section-title {
    -fx-font-family: -fx-font-family;
    -fx-font-size: -fx-font-size-lg;
    -fx-text-fill: -text-primary;
}
*/

/* ============================================================================
   COLOR PALETTE REFERENCE CHART
   ============================================================================ */

/*
GREENS (Buy/Profit):
  -buy-color         #16a34a  (dark green - primary action)
  -buy-hover         #22c55e  (medium green - hover state)
  -buy-light         #4ade80  (light green - secondary variant)
  -profit-color      #22c55e  (alias for -buy-hover)
  -success           #22c55e  (semantic green)

REDS (Sell/Loss):
  -sell-color        #dc2626  (dark red - primary action)
  -sell-hover        #ef4444  (medium red - hover state)
  -sell-light        #f87171  (light red - secondary variant)
  -loss-color        #f87171  (alias for -sell-light)
  -danger            #ef4444  (semantic red)

BLUES (Primary/Info):
  -primary-color     #2563eb  (medium blue - main actions)
  -primary-light     #60a5fa  (light blue - hover/focus)
  -primary-dark      #1d4ed8  (dark blue - pressed state)
  -info              #38bdf8  (cyan - informational)

AMBERS (Warning/Accent):
  -accent-color      #f59e0b  (medium amber - accent)
  -accent-light      #fde68a  (light amber - secondary)
  -warning           #fbbf24  (medium amber - warning state)

GRAYS (Neutral):
  -neutral-color     #64748b  (medium gray)
  -text-muted        #94a3b8  (light gray - text)
  -text-faint        #64748b  (darker gray - text)
*/

/* ============================================================================
   WHEN TO USE WHICH COLOR
   ============================================================================ */

/*
Use -buy-color / -buy-light for:
  • Buy buttons
  • Up/increase indicators
  • Positive trends
  • Long positions
  • Success states

Use -sell-color / -sell-light for:
  • Sell buttons
  • Down/decrease indicators
  • Negative trends
  • Short positions
  • Error states

Use -primary-color for:
  • Primary actions
  • Active selections
  • Important controls
  • Focus states

Use -accent-color for:
  • Secondary emphasis
  • Automation indicators
  • Toggle states
  • Less critical highlights

Use -text-* for:
  • Text content hierarchy
  • Label differentiation
  • Disabled states
  • Secondary information
*/
