# Comprehensive Menu System for InvestPro

## Overview

A complete, well-organized menu system has been created to provide centralized access to all 15+ UI panels and system features. This system consists of:

1. **PanelRegistry** - Central registry of all UI panels with metadata
2. **EnhancedMenuBuilder** - Menu builder that organizes panels by category
3. **SystemMenuBar** - Alternative standalone menu implementation
4. **Keyboard Shortcuts** - Comprehensive shortcut system for quick panel access

## Architecture

### 1. PanelRegistry (`org.investpro.ui.menu.PanelRegistry`)

**Purpose:** Central registry managing all UI panels with their:
- Unique IDs
- Display names
- Class names
- Keyboard shortcuts
- Descriptions
- Categorization

**15 Registered Panels:**

#### Strategy Development (5 panels)
- **Strategy Lab** (Ctrl+L) - Develop and test strategies in isolation
- **Strategy Developer** (Ctrl+Shift+D) - Advanced development with live coding
- **Strategy Builder** (Ctrl+Shift+B) - Visual drag-and-drop composition
- **Strategy Assignment** (Ctrl+Shift+A) - Assign strategies to symbols
- **Trading System Status** (Ctrl+Alt+T) - Real-time system metrics

#### Trading & Orders (1 panel)
- **Order Management** (Ctrl+O) - Place, monitor, and manage orders

#### Market Data (3 panels)
- **Market Watch** (Ctrl+M) - Monitor multiple symbols in real-time
- **Market Info** (Ctrl+I) - Detailed market information
- **News Calendar** (Ctrl+N) - Economic calendar with news events

#### Analysis & Backtesting (4 panels)
- **Technical Indicators** (Ctrl+T) - Configure technical indicators
- **Volume Indicator** (Ctrl+V) - Advanced volume analysis
- **Analysis Panel** (Ctrl+Shift+X) - Comprehensive analysis tools
- **Backtest Report** - View and analyze backtest results

#### Settings (1 panel)
- **Settings** (Ctrl+,) - Application configuration and preferences

#### Backtesting (1 panel)
- **Backtesting** (Ctrl+B) - Run historical simulations

## Menu Structure

### File Menu
- **Connect Exchange** (Ctrl+C) - Connect to selected exchange
- **New Chart** (Ctrl+N) - Open new chart
- **Save Chart** (Ctrl+S) - Save chart screenshot
- **Export Data** - Export trades, settings, reports
- **Exit** (Ctrl+Q) - Close application

### View Menu
- **Toggle Console** (Ctrl+`) - Show/hide console
- **Toggle Market Watch** (Ctrl+M) - Show/hide market watch
- **Toggle Order Book** (Ctrl+B) - Show/hide order book
- **Zoom** submenu:
  - Zoom In (Ctrl++)
  - Zoom Out (Ctrl+-)
  - Fit Chart

### Panels Menu (Auto-organized by category)
Organized into 5 categories:
1. **Strategy Development** - All strategy-related panels
2. **Trading & Orders** - Order management panels
3. **Market Data** - Market watch, info, news
4. **Analysis & Backtesting** - All analysis and backtest panels
5. **Settings** - Application settings

### Strategy Menu
- **Strategy Lab** (Ctrl+L)
- **Strategy Developer** (Ctrl+Shift+D)
- **Strategy Builder** (Ctrl+Shift+B)
- **Strategy Assignment** (Ctrl+Shift+A)
- **View Strategy Catalog**
- **Import Strategy**
- **Export Strategy**

### Analysis Menu
- **Technical Indicators** (Ctrl+T)
- **Volume Indicator** (Ctrl+V)
- **Analysis Panel** (Ctrl+Shift+X)
- **Backtesting** (Ctrl+B)
- **Backtest Report**

### Tools Menu
- **Settings** (Ctrl+,)
- **Trading System Status** (Ctrl+Alt+T)
- **Order Management** (Ctrl+O)
- **Toggle Bot Trading** (Ctrl+T)
- **System Monitor**
- **Data Management** submenu:
  - Refresh Symbols (F5)
  - Refresh Account (Ctrl+R)
  - Refresh Positions
- **Orders** submenu:
  - Order Panel
  - Cancel All Orders

### Help Menu
- **Documentation** (F1)
- **Keyboard Shortcuts** (Ctrl+/)
- **About InvestPro**

## Keyboard Shortcuts

### Navigation & Charts
| Shortcut | Action |
|----------|--------|
| Ctrl+C | Connect to exchange |
| Ctrl+N | New chart |
| Ctrl+S | Save chart screenshot |
| Ctrl+F5 | Refresh symbols |
| Ctrl+R | Refresh account data |

### Strategy Management
| Shortcut | Action |
|----------|--------|
| Ctrl+L | Open Strategy Lab |
| Ctrl+Shift+D | Open Strategy Developer |
| Ctrl+Shift+B | Open Strategy Builder |
| Ctrl+Shift+A | Open Strategy Assignment |

### Trading & Orders
| Shortcut | Action |
|----------|--------|
| Ctrl+O | Open Order Panel |
| Ctrl+T | Toggle Bot Trading |
| Ctrl+B | Open Order Book / Backtesting (context-dependent) |
| Ctrl+M | Toggle Market Watch |

### Analysis & Data
| Shortcut | Action |
|----------|--------|
| Ctrl+T | Technical Indicators |
| Ctrl+V | Volume Indicator |
| Ctrl+Shift+X | Analysis Panel |
| Ctrl+I | Market Info |
| Ctrl+N | News Calendar |

### Application
| Shortcut | Action |
|----------|--------|
| Ctrl+, | Open Settings |
| Ctrl+Alt+T | Trading System Status |
| Ctrl+` | Toggle Console |
| Ctrl+Q | Exit Application |
| F1 | Help/Documentation |
| Ctrl+/ | Show Keyboard Shortcuts |

## Integration with TradingDesk

### Step 1: Add Panel Registry Initialization
```java
// In TradingDesk constructor or initialize method
this.panelRegistry = new PanelRegistry(this);
this.menuBuilder = new EnhancedMenuBuilder(this, panelRegistry);
```

### Step 2: Update createMenuBar()
Replace or augment the existing menu creation:
```java
private MenuBar createMenuBar() {
    // Option 1: Use the enhanced builder
    if (menuBuilder != null) {
        return menuBuilder.buildMenuBar();
    }
    
    // Option 2: Keep existing code and add new menus
    // Existing implementation...
}
```

### Step 3: Implement Panel Opening Methods
Each panel needs a corresponding open method in TradingDesk:

```java
public void openStrategyLab() {
    try {
        if (strategyLabPanel == null) {
            strategyLabPanel = new StrategyLabPanel(/* dependencies */);
        }
        createIndependentWindow("Strategy Lab", strategyLabPanel, 1000, 700);
    } catch (Exception ex) {
        log.error("Error opening Strategy Lab", ex);
        showWarning("Strategy Lab", "Unable to open: " + ex.getMessage());
    }
}

public void openStrategyDeveloper() {
    // Similar implementation
}

public void openOrderPanel() {
    try {
        OrderPanel orderPanel = new OrderPanel(/* dependencies */);
        createIndependentWindow("Order Management", orderPanel, 800, 600);
    } catch (Exception ex) {
        log.error("Error opening Order Panel", ex);
    }
}

// ... and similar methods for all 15 panels
```

### Step 4: Add Keyboard Shortcut Handlers
Already integrated in the menu items, but ensure TradingDesk parent Scene captures:
```java
private void setupKeyboardShortcuts() {
    setOnKeyPressed(event -> {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case L -> openStrategyLab();
                case D -> openStrategyDeveloper();
                case B -> openStrategyBuilder();
                case O -> openOrderPanel();
                case M -> toggleMarketWatchVisibility();
                // ... etc
            }
        }
    });
}
```

## Feature Highlights

### 1. Centralized Panel Management
- Single registry of all 15 panels
- Consistent access patterns
- Easy to add new panels

### 2. Automatic Menu Organization
- Panels grouped by category
- Automatic submenu generation
- Visual hierarchy maintained

### 3. Comprehensive Keyboard Shortcuts
- Quick access to all major features
- Consistent shortcut conventions
- Help menu shows all shortcuts

### 4. Context-Aware Operations
- Panels open in appropriate windows
- Settings applied per panel
- State management for multiple instances

### 5. Dynamic Enablement
- Panels can be enabled/disabled based on system state
- Menus can be updated at runtime
- User preferences can customize visibility

## Usage Examples

### Open a Panel from Code
```java
panelRegistry.openPanel("strategy-lab");
```

### Get Panel Information
```java
PanelRegistry.PanelDescriptor panel = panelRegistry.getPanel("strategy-lab");
System.out.println(panel.getName()); // "Strategy Lab"
System.out.println(panel.getShortcut()); // KeyCodeCombination
```

### Get All Panels by Category
```java
Map<String, List<PanelDescriptor>> grouped = panelRegistry.getPanelsByCategory();
grouped.forEach((category, panels) -> {
    System.out.println(category + ":");
    panels.forEach(p -> System.out.println("  - " + p.getName()));
});
```

### Create Menu Item for a Panel
```java
MenuItem item = panelRegistry.createPanelMenuItem("strategy-lab");
menu.getItems().add(item);
```

## Implementation Status

### Completed ✅
- PanelRegistry with all 15 panels registered
- EnhancedMenuBuilder with complete menu structure
- SystemMenuBar as alternative implementation
- Keyboard shortcut system
- Panel categorization and organization

### Pending Implementation 🔄
- Integration with TradingDesk.initialize()
- Implementation of panel opening methods
- Keyboard shortcut registration in TradingDesk
- Testing of menu functionality
- User preference persistence

### Optional Enhancements 💡
- Panel drag-and-drop organization
- Custom keyboard shortcut configuration
- Menu search/filter functionality
- Recent panels submenu
- Favorite panels section
- Panel state persistence

## File References

- **PanelRegistry**: `src/main/java/org/investpro/ui/menu/PanelRegistry.java`
- **EnhancedMenuBuilder**: `src/main/java/org/investpro/ui/menu/EnhancedMenuBuilder.java`
- **SystemMenuBar**: `src/main/java/org/investpro/ui/menu/SystemMenuBar.java`
- **TradingDesk**: `src/main/java/org/investpro/ui/TradingDesk.java`

## Next Steps

1. Add `panelRegistry` and `menuBuilder` fields to TradingDesk
2. Initialize registry in TradingDesk constructor
3. Update `createMenuBar()` to use EnhancedMenuBuilder
4. Implement 15 panel opening methods in TradingDesk
5. Test menu navigation and keyboard shortcuts
6. Gather user feedback and refine as needed
7. Document panel-specific shortcuts in help system
