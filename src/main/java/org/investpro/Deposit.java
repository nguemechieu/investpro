package org.investpro;

import java.util.Date;
import java.util.List;

public class Deposit {

    private String orderId;
    private String paymentAccount;
    private String paymentChannel;
    private String paymentMethod;
    private String orderStatus;
    private String fiatCurrency;
    private double amount;
    private double transactionFee;
    private double platformFee;

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentAccount() {
        return paymentAccount;
    }

    public void setPaymentAccount(String paymentAccount) {
        this.paymentAccount = paymentAccount;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
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

    public double getPlatformFee() {
        return platformFee;
    }

    public void setPlatformFee(double platformFee) {
        this.platformFee = platformFee;
    }

    // toString method for better debugging and logging
    @Override
    public String toString() {
        return "AssetLogRecord{" +
                "orderId='" + orderId + '\'' +
                ", paymentAccount='" + paymentAccount + '\'' +
                ", paymentChannel='" + paymentChannel + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", fiatCurrency='" + fiatCurrency + '\'' +
                ", amount=" + amount +
                ", transactionFee=" + transactionFee +
                ", platformFee=" + platformFee +
                '}';
    }

    public Date getCreatedAt() {

        return new Date();
    }

    // Inner class to represent a list of AssetLogRecords
    public static class AssetLogRecordList {
        private List<Deposit> assetLogRecordList;

        public List<Deposit> getAssetLogRecordList() {
            return assetLogRecordList;
        }

        public void setAssetLogRecordList(List<Deposit> assetLogRecordList) {
            this.assetLogRecordList = assetLogRecordList;
        }
    }
}
