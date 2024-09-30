package org.investpro;

import java.time.LocalDateTime;

public class CryptoWithdraw {


        private String id;
        private double amount;
        private double transactionFee;
        private String coin;
        private int status;
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

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
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
            return "Transaction{id='%s', amount=%s, transactionFee=%s, coin='%s', status=%d, address='%s', applyTime=%s, network='%s', transferType=%d}".formatted(id, amount, transactionFee, coin, status, address, applyTime, network, transferType);
        }
    }


