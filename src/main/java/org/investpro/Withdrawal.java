package org.investpro;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public double getTransactionFee() {
            return transactionFee;
        }

        public void setTransactionFee(double transactionFee) {
            this.transactionFee = transactionFee;
        }

        public String getCoin() {
            return coin;
        }

        public void setCoin(String coin) {
            this.coin = coin;
        }

    public String getStatus() {
            return status;
        }

    public void setStatus(String status) {
            this.status = status;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public LocalDateTime getApplyTime() {
            return applyTime;
        }

        public void setApplyTime(LocalDateTime applyTime) {
            this.applyTime = applyTime;
        }

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public int getTransferType() {
            return transferType;
        }

        public void setTransferType(int transferType) {
            this.transferType = transferType;
        }

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



