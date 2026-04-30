# ChartToolbar Usage Guide

## Overview
The `ChartToolbar` is a comprehensive toolbar component for the `CandleStickChart` that provides timeframe selection, zoom controls, chart options, and action buttons. It's fully responsive and adapts to container size changes.

## Basic Setup

### Creating the Toolbar
```java
Set<Integer> supportedGranularities = exchange.getCandleDataSupplier(3600, tradePair)
    .getSupportedGranularities();

ChartToolbar toolbar = new ChartToolbar(
    containerWidth,      // ObservableNumberValue
    containerHeight,     // ObservableNumberValue
    supportedGranularities
);
```

### Registering Event Handlers
```java
IntegerProperty secondsPerCandle = new SimpleIntegerProperty(3600);

// Register handlers for granularity selection and zoom
toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);

// Set the chart options pane (optional)
toolbar.setChartOptions(candleStickChart.getChartOptions());
```

### Setting Button Callbacks
```java
// Screenshot button
toolbar.setOnScreenshotAction(() -> {
    // Implement screenshot logic
    System.out.println("Taking screenshot...");
});

// Auto-trade button
toolbar.setOnAutoTradeAction(() -> {
    // Implement auto-trade logic
    System.out.println("Toggling auto-trade...");
});

// Print button
toolbar.setOnPrintAction(() -> {
    // Implement print logic
    System.out.println("Printing chart...");
});
```

## Button Types

### Granularity/Timeframe Buttons
- Text-labeled buttons showing time intervals
- Examples: "1m", "5m", "1h", "4h", "1d", "1w", "1mo"
- Automatically grouped by time scale with visual separators
- Automatically sorted and organized

### Zoom Buttons
- **Zoom In**: Magnifying glass with plus icon
- **Zoom Out**: Magnifying glass with minus icon
- Automatically integrated with chart zoom functionality

### Action Buttons
- **Screenshot**: Captures chart image
- **Auto Trade**: Toggles automatic trading mode

### Chart Options Button
- Settings icon (cog)
- Displays PopOver with chart configuration options
- Includes buffer zone for smooth interaction

## Feature Highlights

### Responsive Design
The toolbar automatically adjusts to container width changes:
- Font sizes scale smoothly
- Button padding adjusts
- Separator spacing adapts
- Delay debouncing prevents excessive recalculations

### PopOver Buffer Zone
The chart options PopOver includes a 10% buffer zone around its edges to prevent flickering when hovering near the boundaries.

### State Management
Active/selected state is tracked through:
- PseudoClass styling for CSS-based appearance
- Active button highlighting for user feedback

### Error Handling
- All null parameters are validated with descriptive error messages
- Image loading failures include specific error information
- Type checking on enum conversions

## Integration Example

```java
public class ChartViewerWindow extends AnchorPane {
    private ChartContainer chartContainer;
    
    private void setupChart() {
        Exchange exchange = ExchangeFactory.createExchange(ExchangeType.BINANCE);
        TradePair tradePair = new TradePair("BTC", "USDT");
        
        chartContainer = new ChartContainer(exchange, tradePair, true);
        
        // Get the toolbar from container and configure callbacks
        ChartToolbar toolbar = chartContainer.getToolbar();
        
        toolbar.setOnScreenshotAction(() -> captureChartScreenshot());
        toolbar.setOnAutoTradeAction(() -> toggleAutoTrading());
        toolbar.setOnPrintAction(() -> printChart());
        
        AnchorPane.setTopAnchor(chartContainer, 0.0);
        AnchorPane.setLeftAnchor(chartContainer, 0.0);
        AnchorPane.setRightAnchor(chartContainer, 0.0);
        AnchorPane.setBottomAnchor(chartContainer, 0.0);
        
        getChildren().add(chartContainer);
    }
    
    private void captureChartScreenshot() {
        // Implementation...
    }
    
    private void toggleAutoTrading() {
        // Implementation...
    }
    
    private void printChart() {
        // Implementation...
    }
}
```

## Styling

The toolbar uses the following CSS classes for customization:

- `.candle-chart-toolbar` - Main toolbar container (HBox)
- `.candle-chart-toolbar-button` - All toolbar buttons
- `.candle-chart-toolbar-button:active` - Active granularity button

### CSS Example
```css
.candle-chart-toolbar {
    -fx-spacing: 5;
    -fx-background-color: #1e293b;
    -fx-padding: 7 10 7 10;
}

.candle-chart-toolbar-button {
    -fx-text-fill: #cbd5e1;
    -fx-border-radius: 3;
}

.candle-chart-toolbar-button:active {
    -fx-background-color: #334155;
    -fx-border-color: #64748b;
}
```

## Constants Reference

All magic numbers are extracted to constants for easy tuning:

| Constant | Value | Purpose |
|----------|-------|---------|
| `FONT_SIZE_THRESHOLD` | 900 | Width breakpoint for responsive sizing |
| `FONT_SIZE_LARGE` | 14 | Text font size for large screens |
| `GLYPH_FONT_SIZE_LARGE` | 22 | Glyph font size for large screens |
| `SIZE_ADJUSTMENT_DELAY_PRIMARY` | 750 | Initial resize debounce delay (ms) |
| `SIZE_ADJUSTMENT_DELAY_SECONDARY` | 300 | Secondary resize debounce delay (ms) |
| `POPOVER_BUFFER_PERCENTAGE` | 10 | PopOver hover buffer zone (%) |

## Performance Notes

- The toolbar uses delayed size change listeners to debounce resize events
- PopOver is shown/hidden with smooth 0.25-second transitions
- Image resources are cached after initial load
- Button state changes are efficient through PseudoClass mechanism

## Troubleshooting

### Buttons Not Responding
Ensure event handlers are registered:
```java
toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
```

### Icons Not Showing
Check that image resources exist in the classpath:
- `/img/search-plus-solid.png` (Zoom In)
- `/img/search-minus-solid.png` (Zoom Out)
- `/img/print-solid.png` (Print)
- `/img/cog-solid.png` (Options)

### PopOver Not Appearing
Verify the scene and window are properly initialized before creating the toolbar.

### Sizing Issues
Ensure container width/height properties are properly bound and fire change events.

## Future Enhancements

Potential improvements for future versions:
- Add keyboard shortcuts for common actions
- Implement toolbar customization (show/hide buttons)
- Add button tooltips
- Support for custom button groups
- Persistence of toolbar state
