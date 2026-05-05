package org.investpro.exchange.core;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * NormalizedOrderRequest - A broker-agnostic representation of an order,
 * used as input before conversion to broker-specific payloads.
 */
@Getter
@Setter
public class NormalizedOrderRequest {
    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT
    }
    
    public enum Side {
        BUY, SELL
    }
    
    public enum TimeInForce {
        FOK,  // Fill or Kill
        IOC,  // Immediate or Cancel
        GTC,  // Good Till Cancel
        GTD   // Good Till Date
    }
    
    private final String productId;
    private final Side side;
    private final OrderType orderType;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;     // For limit orders
    private final BigDecimal stopPrice;      // For stop orders
    private final BigDecimal stopLossPrice;  // For bracket orders
    private final BigDecimal takeProfitPrice; // For bracket orders
    private final TimeInForce timeInForce;
    private final boolean postOnly;
    private final BigDecimal leverage;       // For leveraged orders
    
    public NormalizedOrderRequest(String productId, Side side, OrderType orderType,
                                  BigDecimal quantity, BigDecimal limitPrice) {
        this(productId, side, orderType, quantity, limitPrice, null, null, null, TimeInForce.GTC, false, BigDecimal.ONE);
    }
    
    private NormalizedOrderRequest(String productId, Side side, OrderType orderType,
                                   BigDecimal quantity, BigDecimal limitPrice,
                                   BigDecimal stopPrice, BigDecimal stopLossPrice,
                                   BigDecimal takeProfitPrice, TimeInForce timeInForce,
                                   boolean postOnly, BigDecimal leverage) {
        this.productId = Objects.requireNonNull(productId, "productId required");
        this.side = Objects.requireNonNull(side, "side required");
        this.orderType = Objects.requireNonNull(orderType, "orderType required");
        this.quantity = Objects.requireNonNull(quantity, "quantity required");
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.stopLossPrice = stopLossPrice;
        this.takeProfitPrice = takeProfitPrice;
        this.timeInForce = timeInForce != null ? timeInForce : TimeInForce.GTC;
        this.postOnly = postOnly;
        this.leverage = leverage != null ? leverage : BigDecimal.ONE;
    }
    
    // Builder pattern
    public static Builder builder(String productId, Side side, OrderType orderType, BigDecimal quantity) {
        return new Builder(productId, side, orderType, quantity);
    }
    
    public static class Builder {
        private final String productId;
        private final Side side;
        private final OrderType orderType;
        private final BigDecimal quantity;
        private BigDecimal limitPrice;
        private BigDecimal stopPrice;
        private BigDecimal stopLossPrice;
        private BigDecimal takeProfitPrice;
        private TimeInForce timeInForce = TimeInForce.GTC;
        private boolean postOnly = false;
        private BigDecimal leverage = BigDecimal.ONE;
        
        public Builder(String productId, Side side, OrderType orderType, BigDecimal quantity) {
            this.productId = productId;
            this.side = side;
            this.orderType = orderType;
            this.quantity = quantity;
        }
        
        public Builder limitPrice(BigDecimal limitPrice) {
            this.limitPrice = limitPrice;
            return this;
        }
        
        public Builder stopPrice(BigDecimal stopPrice) {
            this.stopPrice = stopPrice;
            return this;
        }
        
        public Builder stopLossPrice(BigDecimal stopLossPrice) {
            this.stopLossPrice = stopLossPrice;
            return this;
        }
        
        public Builder takeProfitPrice(BigDecimal takeProfitPrice) {
            this.takeProfitPrice = takeProfitPrice;
            return this;
        }
        
        public Builder timeInForce(TimeInForce timeInForce) {
            this.timeInForce = timeInForce;
            return this;
        }
        
        public Builder postOnly(boolean postOnly) {
            this.postOnly = postOnly;
            return this;
        }
        
        public Builder leverage(BigDecimal leverage) {
            this.leverage = leverage;
            return this;
        }
        
        public NormalizedOrderRequest build() {
            return new NormalizedOrderRequest(productId, side, orderType, quantity,
                    limitPrice, stopPrice, stopLossPrice, takeProfitPrice,
                    timeInForce, postOnly, leverage);
        }
    }
    
    // Getters
    public String getProductId() { return productId; }
    public Side getSide() { return side; }
    public OrderType getOrderType() { return orderType; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public BigDecimal getTakeProfitPrice() { return takeProfitPrice; }
    public TimeInForce getTimeInForce() { return timeInForce; }
    public boolean isPostOnly() { return postOnly; }
    public BigDecimal getLeverage() { return leverage; }
    
    public boolean hasStopLoss() { return stopLossPrice != null && stopLossPrice.signum() > 0; }
    public boolean hasTakeProfit() { return takeProfitPrice != null && takeProfitPrice.signum() > 0; }
    public boolean isBracketOrder() { return hasStopLoss() || hasTakeProfit(); }
    
    @Override
    public String toString() {
        return "NormalizedOrderRequest{" +
                "productId='" + productId + '\'' +
                ", side=" + side +
                ", orderType=" + orderType +
                ", quantity=" + quantity +
                ", limitPrice=" + limitPrice +
                '}';
    }
}
