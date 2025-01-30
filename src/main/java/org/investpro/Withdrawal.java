package org.investpro;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Setter
@Getter
public class Withdrawal {


        private String id;
        private double amount;
        private double transactionFee;
        private String coin;
    private String status;
        private String address;
        private LocalDateTime applyTime;
        private String network;
        private int transferType;

        // Getters and Setters

    // toString method for easier debugging

        @Override
        public String toString() {
            return "Transaction{id='%s', amount=%s, transactionFee=%s, coin='%s', status=%s, address='%s', applyTime=%s, network='%s', transferType=%d}".formatted(id, amount, transactionFee, coin, status, address, applyTime, network, transferType);
        }

    public Date getCreatedAt() {
        return Date.from(applyTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public ENUM_ORDER_STATUS getOrderStatus() {

        // Convert status to ENUM_ORDER_STATUS enum
        return switch (status) {
            case "NEW" -> ENUM_ORDER_STATUS.NEW;
            case "OPEN" -> ENUM_ORDER_STATUS.OPEN;
            case "PARTIALLY FILLED" -> ENUM_ORDER_STATUS.PARTIALLY_FILLED;
            case "FILLED" -> ENUM_ORDER_STATUS.FILLED;
            case "CANCELED" -> ENUM_ORDER_STATUS.CANCELED;
            case "REJECTED" -> ENUM_ORDER_STATUS.REJECTED;
            default -> ENUM_ORDER_STATUS.UNKNOWN;

        };
    }

}



