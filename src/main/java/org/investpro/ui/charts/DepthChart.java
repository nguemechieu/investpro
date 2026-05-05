package org.investpro.ui.charts;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.investpro.models.trading.OrderBook;
import java.util.*;

/**
 * Visual depth chart for order book visualization
 * Shows cumulative buy (green) and sell (red) volumes at different price levels
 */
public class DepthChart extends VBox {
    
    private final Canvas canvas;
    private OrderBook orderBook;
    private final double bidColor_r = 0.18;
    private final double bidColor_g = 0.80;
    private final double bidColor_b = 0.44;  // Green
    private final double askColor_r = 0.91;
    private final double askColor_g = 0.30;
    private final double askColor_b = 0.24;  // Red
    
    private double minPrice, maxPrice, maxVolume;
    private final double padding = 60;
    private final double topPadding = 40;
    private final double bottomPadding = 40;
    
    public DepthChart() {
        this.canvas = new Canvas(800, 400);  // Set default dimensions
        canvas.setStyle("-fx-padding: 10;");
        
        // Set minimum dimensions to prevent zero-sized canvas
        this.setMinWidth(300);
        this.setMinHeight(250);
        
        this.getChildren().add(canvas);
        VBox.setVgrow(canvas, Priority.ALWAYS);
        this.setStyle("-fx-border-color: #333333; -fx-border-width: 1;");
        
        // Resize canvas when container resizes with a delayed redraw
        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            double newWidth = Math.max(100, newVal.doubleValue());
            canvas.setWidth(newWidth);
            Platform.runLater(this::redraw);
        });
        
        this.heightProperty().addListener((obs, oldVal, newVal) -> {
            double newHeight = Math.max(100, newVal.doubleValue());
            canvas.setHeight(newHeight);
            Platform.runLater(this::redraw);
        });
    }
    
    /**
     * Update depth chart with new order book data
     */
    public void update(OrderBook orderBook) {
        this.orderBook = orderBook;
        Platform.runLater(this::redraw);
    }
    
    /**
     * Redraw the depth chart
     */
    private void redraw() {
        // Safety check: don't render if canvas has invalid dimensions
        if (canvas.getWidth() < 100 || canvas.getHeight() < 100) {
            return;
        }
        
        if (orderBook == null || orderBook.getBids().isEmpty() || orderBook.getAsks().isEmpty()) {
            drawEmpty();
            return;
        }
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Calculate price range
        OrderBook.PriceLevel bestBid = orderBook.getBestBid();
        OrderBook.PriceLevel bestAsk = orderBook.getBestAsk();
        
        double spread = bestAsk.getPrice() - bestBid.getPrice();
        double margin = spread == 0 ? bestBid.getPrice() * 0.05 : spread * 1.5;
        
        minPrice = bestBid.getPrice() - margin;
        maxPrice = bestAsk.getPrice() + margin;
        
        // Get cumulative volumes
        List<DepthPoint> bidDepth = calculateBidDepth(orderBook.getBids());
        List<DepthPoint> askDepth = calculateAskDepth(orderBook.getAsks());
        
        // Calculate max volume
        maxVolume = 0;
        for (DepthPoint point : bidDepth) {
            maxVolume = Math.max(maxVolume, point.cumulativeVolume);
        }
        for (DepthPoint point : askDepth) {
            maxVolume = Math.max(maxVolume, point.cumulativeVolume);
        }
        
        if (maxVolume == 0) maxVolume = 1;
        
        // Draw grid and axes
        drawAxes(gc);
        
        // Draw bid depth (green area)
        drawBidArea(gc, bidDepth);
        
        // Draw ask depth (red area)
        drawAskArea(gc, askDepth);
        
        // Draw mid price line
        drawMidPrice(gc);
        
        // Draw legend
        drawLegend(gc);
    }
    
    private void drawEmpty() {
        // Safety check
        if (canvas.getWidth() < 50 || canvas.getHeight() < 50) {
            return;
        }
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        gc.setFill(Color.gray(0.7));
        gc.setFont(new Font(14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("No order book data available", 
                    canvas.getWidth() / 2, canvas.getHeight() / 2);
    }
    
    private void drawAxes(GraphicsContext gc) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        // Safety check
        if (width < 100 || height < 100) {
            return;
        }
        
        double chartHeight = height - topPadding - bottomPadding;
        double chartWidth = width - padding * 2;
        
        // Background
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(padding, topPadding, chartWidth, chartHeight);
        
        // Border
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1);
        gc.strokeRect(padding, topPadding, chartWidth, chartHeight);
        
        // Grid lines and labels
        gc.setStroke(Color.web("#404040"));
        gc.setLineWidth(0.5);
        gc.setFill(Color.web("#999999"));
        gc.setFont(new Font(10));
        gc.setTextAlign(TextAlignment.RIGHT);
        
        // Price axis (left)
        int priceSteps = 5;
        for (int i = 0; i <= priceSteps; i++) {
            double price = minPrice + (maxPrice - minPrice) * i / priceSteps;
            double y = topPadding + chartHeight * (1 - i / (double) priceSteps);
            
            gc.strokeLine(padding - 5, y, padding + chartWidth, y);
            gc.fillText(String.format("%.6f", price), padding - 10, y + 4);
        }
        
        // Volume axis (bottom)
        gc.setTextAlign(TextAlignment.CENTER);
        int volumeSteps = 5;
        for (int i = 0; i <= volumeSteps; i++) {
            double volume = maxVolume * i / volumeSteps;
            double x = padding + chartWidth * i / volumeSteps;
            
            gc.strokeLine(x, topPadding + chartHeight, x, topPadding + chartHeight + 5);
            gc.fillText(formatVolume(volume), x, topPadding + chartHeight + 20);
        }
        
        // Axis labels
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#bbbbbb"));
        gc.fillText("Price", 20, height / 2);
        gc.fillText("Volume", width / 2, height - 5);
    }
    
    private void drawBidArea(GraphicsContext gc, List<DepthPoint> bidDepth) {
        if (bidDepth.isEmpty()) return;
        
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double chartHeight = height - topPadding - bottomPadding;
        double chartWidth = width - padding * 2;
        
        // Create path for bid area
        double[] xPoints = new double[bidDepth.size() + 2];
        double[] yPoints = new double[bidDepth.size() + 2];
        
        // Start from bottom-right
        xPoints[0] = padding;
        yPoints[0] = topPadding + chartHeight;
        
        // Add bid points (reversed order - highest price first)
        for (int i = 0; i < bidDepth.size(); i++) {
            DepthPoint point = bidDepth.get(i);
            double x = padding + (point.cumulativeVolume / maxVolume) * chartWidth;
            double y = topPadding + chartHeight * (1 - (point.price - minPrice) / (maxPrice - minPrice));
            
            xPoints[i + 1] = x;
            yPoints[i + 1] = y;
        }
        
        // End at bottom
        xPoints[bidDepth.size() + 1] = padding;
        yPoints[bidDepth.size() + 1] = topPadding + chartHeight;
        
        // Draw filled area
        gc.setFill(Color.color(bidColor_r, bidColor_g, bidColor_b, 0.3));
        gc.fillPolygon(xPoints, yPoints, xPoints.length);
        
        // Draw outline
        gc.setStroke(Color.color(bidColor_r, bidColor_g, bidColor_b, 0.8));
        gc.setLineWidth(2);
        for (int i = 0; i < xPoints.length - 1; i++) {
            gc.strokeLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }
    
    private void drawAskArea(GraphicsContext gc, List<DepthPoint> askDepth) {
        if (askDepth.isEmpty()) return;
        
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double chartHeight = height - topPadding - bottomPadding;
        double chartWidth = width - padding * 2;
        
        // Create path for ask area
        double[] xPoints = new double[askDepth.size() + 2];
        double[] yPoints = new double[askDepth.size() + 2];
        
        // Start from bottom-left
        xPoints[0] = padding;
        yPoints[0] = topPadding + chartHeight;
        
        // Add ask points
        for (int i = 0; i < askDepth.size(); i++) {
            DepthPoint point = askDepth.get(i);
            double x = padding + (point.cumulativeVolume / maxVolume) * chartWidth;
            double y = topPadding + chartHeight * (1 - (point.price - minPrice) / (maxPrice - minPrice));
            
            xPoints[i + 1] = x;
            yPoints[i + 1] = y;
        }
        
        // End at bottom
        xPoints[askDepth.size() + 1] = padding;
        yPoints[askDepth.size() + 1] = topPadding + chartHeight;
        
        // Draw filled area
        gc.setFill(Color.color(askColor_r, askColor_g, askColor_b, 0.3));
        gc.fillPolygon(xPoints, yPoints, xPoints.length);
        
        // Draw outline
        gc.setStroke(Color.color(askColor_r, askColor_g, askColor_b, 0.8));
        gc.setLineWidth(2);
        for (int i = 0; i < xPoints.length - 1; i++) {
            gc.strokeLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }
    
    private void drawMidPrice(GraphicsContext gc) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double chartHeight = height - topPadding - bottomPadding;
        double chartWidth = width - padding * 2;
        
        double midPrice = orderBook.getMidPrice();
        double y = topPadding + chartHeight * (1 - (midPrice - minPrice) / (maxPrice - minPrice));
        
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5);
        gc.strokeLine(padding, y, padding + chartWidth, y);
        gc.setLineDashes(0);
        
        // Mid price label
        gc.setFill(Color.YELLOW);
        gc.setFont(new Font(10));
        gc.fillText(String.format("Mid: %.6f", midPrice), padding + chartWidth - 100, y - 5);
    }
    
    private void drawLegend(GraphicsContext gc) {
        double x = padding + 20;
        double y = topPadding + 20;
        
        // Bid legend
        gc.setFill(Color.color(bidColor_r, bidColor_g, bidColor_b, 0.8));
        gc.fillRect(x, y, 12, 12);
        gc.setFill(Color.WHITE);
        gc.setFont(new Font(11));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Bids", x + 20, y + 10);
        
        // Ask legend
        gc.setFill(Color.color(askColor_r, askColor_g, askColor_b, 0.8));
        gc.fillRect(x + 80, y, 12, 12);
        gc.setFill(Color.WHITE);
        gc.fillText("Asks", x + 100, y + 10);
    }
    
    private List<DepthPoint> calculateBidDepth(List<OrderBook.PriceLevel> bids) {
        List<DepthPoint> depth = new ArrayList<>();
        double cumulativeVolume = 0;
        
        // Bids are ordered highest to lowest, so iterate normally
        for (OrderBook.PriceLevel bid : bids) {
            cumulativeVolume += bid.getSize();
            depth.add(new DepthPoint(bid.getPrice(), cumulativeVolume));
        }
        
        return depth;
    }
    
    private List<DepthPoint> calculateAskDepth(List<OrderBook.PriceLevel> asks) {
        List<DepthPoint> depth = new ArrayList<>();
        double cumulativeVolume = 0;
        
        // Asks are ordered lowest to highest, so iterate in reverse for visual consistency
        for (int i = asks.size() - 1; i >= 0; i--) {
            OrderBook.PriceLevel ask = asks.get(i);
            cumulativeVolume += ask.getSize();
            depth.add(new DepthPoint(ask.getPrice(), cumulativeVolume));
        }
        
        return depth;
    }
    
    private String formatVolume(double volume) {
        if (volume >= 1_000_000) {
            return String.format("%.1fM", volume / 1_000_000);
        } else if (volume >= 1_000) {
            return String.format("%.1fK", volume / 1_000);
        } else {
            return String.format("%.0f", volume);
        }
    }
    
    /**
     * Represents a single point in the depth chart
     */
    private static class DepthPoint {
        double price;
        double cumulativeVolume;
        
        DepthPoint(double price, double cumulativeVolume) {
            this.price = price;
            this.cumulativeVolume = cumulativeVolume;
        }
    }
}
