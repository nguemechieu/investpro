package org.investpro.investpro;

import org.investpro.investpro.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface TradeOperations {
    void createOrder(TradePair tradePair, @NotNull Side side, ENUM_ORDER_TYPE orderType,
                     double price, double size, Date timestamp, double stopLoss, double takeProfit)
            throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    void cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    void cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException, InterruptedException;

    List<Order> getOrders() throws IOException, InterruptedException, SQLException, ClassNotFoundException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    List<Order> getPendingOrders() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    List<Position> getPositions() throws IOException, InterruptedException, ExecutionException;
}

