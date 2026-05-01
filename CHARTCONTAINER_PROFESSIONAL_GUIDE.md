# ChartContainer Professional Implementation Guide

## Overview

The `ChartContainer` is a production-ready, professional chart container component that manages the full lifecycle of candlestick charts with interactive controls. It provides seamless timeframe switching, smooth chart transitions, comprehensive error handling, and a clean public API.

## Features

### Core Capabilities
- **Chart Management**: Creates, configures, and disposes CandleStickChart instances
- **Timeframe Selection**: Interactive dropdown with predefined timeframes
- **Smooth Transitions**: Fade in/out animations when switching charts
- **Symbol Management**: Switch trading pairs with automatic chart reload
- **Responsive Design**: Adapts to container size changes dynamically
- **Live Syncing**: Optional real-time candle updates
- **Professional Styling**: Dark theme with proper spacing and typography

### Advanced Features
- **Error Handling**: Comprehensive error callbacks and logging
- **Resource Management**: Proper cleanup and disposal of resources
- **State Management**: Observable properties for bindings
- **Lifecycle Callbacks**: Events for chart creation, disposal, and errors
- **Logging Integration**: JDK logging for debugging and monitoring

## Architecture

### Class Structure

```
ChartContainer (Region)
├── Root Layout (AnchorPane)
│   ├── Toolbar Container (HBox)
│   │   ├── Timeframe Label
│   │   ├── Timeframe Selector (ComboBox)
│   │   ├── Vertical Separator
│   │   ├── ChartToolbar (HBox)
│   │   └── Spacer (Region)
│   └── Chart Host (VBox)
│       └── CandleStickChart (Region)
```

### Component Interactions

```
User Action (UI)
    ↓
TimeframeSelector / ChartToolbar Button
    ↓
SimpleIntegerProperty (secondsPerCandle)
    ↓
Change Listener
    ↓
recreateChartForTimeframe()
    ↓
Chart Transition (with optional fade effect)
    ↓
registerToolbar() → Callback → onChartCreated()
```

## Usage Examples

### Basic Setup

```java
// Create a chart container
ChartContainer container = new ChartContainer(
    exchange,           // The exchange providing candle data
    tradePair,          // The trading pair (e.g., BTC/USDT)
    true                // Enable live syncing
);

// Add to UI
VBox.setVgrow(container, Priority.ALWAYS);
myVBox.getChildren().add(container);
```

### Setting Error Handler

```java
container.setOnChartError(errorMessage -> {
    System.err.println("Chart Error: " + errorMessage);
    showNotification("Error", errorMessage);
});
```

### Handling Chart Lifecycle Events

```java
container.setOnChartCreated(() -> {
    System.out.println("Chart ready!");
    // Enable UI controls, update status bar, etc.
});

container.setOnChartDisposed(() -> {
    System.out.println("Chart cleaned up");
    // Disable UI controls, etc.
});
```

### Programmatic Timeframe Changes

```java
// Change by seconds
container.setSecondsPerCandle(3600);  // 1 hour

// Change by timeframe string
container.setSelectedTimeframe("4h");

// Get current timeframe
int seconds = container.getSecondsPerCandle();
String timeframe = container.getSelectedTimeframe();
```

### Symbol Switching

```java
// Switch to a different trading pair
TradePair newPair = new TradePair("ETH", "USDT");
container.setTradePairAndReload(newPair);

// Get current trading pair
TradePair current = container.getTradePair();
```

### Toolbar Customization

```java
// Access toolbar for advanced customization
ChartToolbar toolbar = container.getToolbar();

// Set custom action callbacks
toolbar.setOnScreenshotAction(() -> saveChartImage());
toolbar.setOnAutoTradeAction(() -> toggleAutoTrading());
toolbar.setOnPrintAction(() -> printChart());
```

### Chart Refresh

```java
// Rebuild chart with current settings
container.refreshChart();

// Get the underlying chart for direct manipulation
CandleStickChart chart = container.getChart();
if (chart != null) {
    // Manipulate chart directly
    chart.toggleCrosshair();
    chart.changeZoom(ZoomDirection.IN);
}
```

## Constants Reference

### Layout Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `MIN_WIDTH` | 350 | Minimum container width |
| `MIN_HEIGHT` | 350 | Minimum container height |
| `PREF_WIDTH` | 900 | Preferred container width |
| `PREF_HEIGHT` | 600 | Preferred container height |
| `TOOLBAR_HEIGHT` | 46 | Fixed toolbar height |
| `TOOLBAR_SPACING` | 10 | Spacing between toolbar items |
| `TOOLBAR_PADDING` | 7 | Toolbar vertical padding |
| `TOOLBAR_PADDING_HORIZONTAL` | 10 | Toolbar horizontal padding |

### Timing Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `FADE_OUT_DURATION` | 180ms | Chart fade-out transition |
| `FADE_IN_DURATION` | 220ms | Chart fade-in transition |

### Defaults

| Constant | Value | Purpose |
|----------|-------|---------|
| `DEFAULT_SECONDS_PER_CANDLE` | 3600 | 1 hour default |
| `DEFAULT_TIMEFRAME` | "1h" | Default timeframe string |

## CSS Styling

### Available Style Classes

```css
.candle-chart-container {
    /* Main container */
}

.chart-container-root {
    /* Root layout pane */
}

.chart-toolbar-container {
    /* Toolbar container */
}

.chart-host {
    /* Chart host container */
}

.chart-timeframe-label {
    /* Timeframe label */
}

.chart-timeframe-selector {
    /* Timeframe dropdown */
}

.candle-chart-toolbar {
    /* Toolbar (from ChartToolbar) */
}

.candle-chart-toolbar-button {
    /* Toolbar buttons (from ChartToolbar) */
}

.candle-chart-toolbar-button:active {
    /* Active toolbar button state */
}
```

### Example Custom Styling

```css
.candle-chart-container {
    -fx-border-color: #334155;
    -fx-border-width: 1;
    -fx-background-color: #0f172a;
}

.chart-timeframe-label {
    -fx-text-fill: #cbd5e1;
    -fx-font-size: 12px;
    -fx-font-weight: bold;
}

.chart-timeframe-selector {
    -fx-font-size: 12px;
    -fx-padding: 4;
    -fx-border-radius: 3;
}
```

## Error Handling

### Common Errors

#### 1. Unsupported Timeframe

```java
container.setSelectedTimeframe("2h");  // Throws IllegalArgumentException
// Only use timeframes from CandleAggregator.getSupportedTimeframes()
```

**Solution:**
```java
String timeframe = "1h";  // Use a supported timeframe
if (CandleAggregator.isValidTimeframe(timeframe)) {
    container.setSelectedTimeframe(timeframe);
}
```

#### 2. Non-Positive Seconds

```java
container.setSecondsPerCandle(0);  // Throws IllegalArgumentException
```

**Solution:**
```java
container.setSecondsPerCandle(3600);  // Must be positive
```

#### 3. Null Trading Pair

```java
container.setTradePairAndReload(null);  // Throws NullPointerException
```

**Solution:**
```java
TradePair pair = new TradePair("BTC", "USDT");
container.setTradePairAndReload(pair);
```

### Error Callback Pattern

```java
container.setOnChartError(errorMessage -> {
    LOGGER.log(Level.SEVERE, "Chart error: " + errorMessage);
    
    // Show user notification
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Chart Error");
    alert.setContentText(errorMessage);
    alert.showAndWait();
    
    // Try to recover
    container.refreshChart();
});
```

## Performance Considerations

### Lifecycle Optimization

```java
// Good: Preload container in background thread
Task<ChartContainer> loadTask = new Task<>() {
    @Override
    protected ChartContainer call() {
        return new ChartContainer(exchange, tradePair, true);
    }
};

new Thread(loadTask).start();

loadTask.setOnSucceeded(event -> {
    ChartContainer container = loadTask.getValue();
    // Add to UI on JavaFX thread
    Platform.runLater(() -> myPane.getChildren().add(container));
});
```

### Resource Management

```java
// Always dispose when no longer needed
@Override
public void stop() throws Exception {
    if (container != null) {
        container.dispose();
    }
    super.stop();
}
```

### Smooth Transitions

The component uses fade transitions (180ms out, 220ms in) for chart switching. For better UX:

```java
// Transition is automatic - just let it happen
container.setSecondsPerCandle(7200);  // Smooth fade transition occurs

// For very frequent changes, you might want to skip animation
// This requires direct method access:
// container.recreateChartForTimeframe(durationSeconds); // Direct rebuild
```

## Integration Examples

### In a Trading Application

```java
public class TradingWindow extends BorderPane {
    private final ChartContainer chartContainer;
    private final SymbolSelector symbolSelector;
    
    public TradingWindow(Exchange exchange) {
        chartContainer = new ChartContainer(exchange, 
            symbolSelector.getSelectedPair(), 
            true
        );
        
        chartContainer.setOnChartError(this::showErrorDialog);
        chartContainer.setOnChartCreated(this::updateStatusBar);
        
        // Add toolbar to toolbar area
        HBox toolbar = new HBox();
        toolbar.getChildren().addAll(
            symbolSelector,
            new Separator(),
            chartContainer.getToolbar()
        );
        
        setCenter(chartContainer);
        setTop(toolbar);
    }
    
    private void showErrorDialog(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Chart Error");
        alert.setContentText(error);
        alert.showAndWait();
    }
    
    private void updateStatusBar() {
        statusLabel.setText("Chart ready - " + 
            chartContainer.getSelectedTimeframe());
    }
}
```

### With Tab Pane

```java
TabPane tabPane = new TabPane();

Tab tab1 = new Tab("BTC/USDT", new ChartContainer(exchange, 
    new TradePair("BTC", "USDT"), true));
tab1.setClosable(false);

Tab tab2 = new Tab("ETH/USDT", new ChartContainer(exchange, 
    new TradePair("ETH", "USDT"), true));
tab2.setClosable(false);

tabPane.getTabs().addAll(tab1, tab2);
```

## Best Practices

### 1. Always Set Error Handler

```java
// ✓ Good
container.setOnChartError(error -> {
    LOGGER.log(Level.SEVERE, error);
});

// ✗ Bad
// container without error handling may fail silently
```

### 2. Validate Input

```java
// ✓ Good
if (CandleAggregator.isValidTimeframe(userInput)) {
    container.setSelectedTimeframe(userInput);
}

// ✗ Bad
container.setSelectedTimeframe(userInput);  // May throw
```

### 3. Use Properties for Binding

```java
// ✓ Good
SimpleIntegerProperty selectedSeconds = new SimpleIntegerProperty();
selectedSeconds.bindBidirectional(container.secondsPerCandleProperty());

// ✗ Bad
int seconds = container.getSecondsPerCandle();  // Loses reactivity
```

### 4. Dispose Properly

```java
// ✓ Good
@Override
public void close() {
    if (container != null) {
        container.dispose();
    }
}

// ✗ Bad
// Container left without disposal may leak resources
```

### 5. Handle Callbacks Safely

```java
// ✓ Good
container.setOnChartCreated(() -> {
    try {
        // Update UI
    } catch (Exception e) {
        LOGGER.log(Level.WARNING, e.getMessage(), e);
    }
});

// ✗ Bad
container.setOnChartCreated(() -> {
    // UI update that might throw
});
```

## Logging and Debugging

### Enable Detailed Logging

```java
// Set logger level
Logger.getLogger("org.investpro.ui").setLevel(Level.FINE);

// Add console handler
ConsoleHandler handler = new ConsoleHandler();
handler.setLevel(Level.FINE);
Logger.getLogger("org.investpro.ui").addHandler(handler);
```

### Monitor Lifecycle

```java
container.setOnChartCreated(() -> 
    System.out.println("Chart created at: " + new Date()));

container.setOnChartDisposed(() -> 
    System.out.println("Chart disposed at: " + new Date()));

container.setOnChartError(error -> 
    System.err.println("Error: " + error + " at " + new Date()));
```

## Troubleshooting

### Chart Not Showing

**Problem**: Chart container is empty or shows blank area.

**Solutions**:
1. Check that container is properly added to scene
2. Verify container has positive dimensions
3. Check for errors in error callback
4. Verify exchange and trade pair are valid

### Timeframe Selector Not Working

**Problem**: Changing timeframe doesn't update chart.

**Solutions**:
1. Verify timeframe is in supported list
2. Check error callback for exceptions
3. Ensure exchange supports the timeframe
4. Check that chart data is available

### Slow Chart Switching

**Problem**: Chart transitions take too long.

**Solutions**:
1. Adjust FADE_OUT_DURATION and FADE_IN_DURATION constants
2. Check database query performance
3. Verify exchange API latency
4. Profile data loading time

### Memory Leaks

**Problem**: Memory usage grows after many chart changes.

**Solutions**:
1. Ensure dispose() is called on container
2. Check for circular references in callbacks
3. Verify ChartToolbar callbacks don't hold references
4. Monitor image loading in tooltips

## API Reference

### Getters

```java
CandleStickChart getChart()
ChartToolbar getToolbar()
int getSecondsPerCandle()
SimpleIntegerProperty secondsPerCandleProperty()
String getSelectedTimeframe()
TradePair getTradePair()
Set<Integer> getSupportedGranularities()
```

### Setters

```java
void setSecondsPerCandle(int seconds)
void setSelectedTimeframe(String timeframe)
void setTradePairAndReload(TradePair tradePair)
void setOnChartError(Consumer<String> callback)
void setOnChartCreated(Runnable callback)
void setOnChartDisposed(Runnable callback)
```

### Operations

```java
void refreshChart()
void dispose()
```

### Layout Overrides

```java
private void layoutChildren()
private double computeMinWidth(double height)
protected double computeMinHeight(double width)
protected double computePrefWidth(double height)
protected double computePrefHeight
```

## Version History

### v2.0 (Current)
- Professional refactoring with comprehensive documentation
- Error handling callbacks
- Logging integration
- Lifecycle event callbacks
- Fixed ChartToolbar integration
- Constants extraction
- Enhanced public API

### v1.0
- Initial implementation
- Basic chart management
- Timeframe selection
- Chart switching

## Related Components

- [ChartToolbar](CHARTTOOLBAR_USAGE_GUIDE.md) - Interactive chart controls
- `CandleStickChart` - The underlying chart component
- `CandleAggregator` - Timeframe and candle management
- `Exchange` - Data provider interface

## Future Enhancements

Potential improvements for future versions:
- Multi-chart comparison view
- Chart indicators management
- Save/restore chart state
- Chart export functionality
- Performance metrics dashboard
- Caching layer for chart data
- Theme customization UI
- Chart templates
