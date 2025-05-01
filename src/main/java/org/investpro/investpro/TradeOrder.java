package org.investpro.investpro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.investpro.investpro.model.TradePair;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Getter
@Setter
@ToString
public class TradeOrder {

    double stopLoss;
    double takeProfit;
    private Long orderId;
    private ENUM_ORDER_STATUS orderStatus;
    private Exchange exchange;
    private TradePair tradePair;
    private Side side;
    private ENUM_ORDER_TYPE orderType;
    private BigDecimal price;
    private BigDecimal amount;
    private Instant timestamp;
    public TradeOrder(Exchange exchange, TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType, BigDecimal price, BigDecimal amount, Instant timestamp) {
        // Constructor
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public void createOrder() {
        // Logic to create the order on the exchange

        CompletableFuture.runAsync(() ->
        {
            try {
                exchange.createOrder(tradePair, side, orderType, price.byteValueExact(), amount.byteValueExact(), Date.from(timestamp), stopLoss, takeProfit);
            } catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeyException |
                     ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
