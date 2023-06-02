package org.investpro.oanda;

public record OandaTransaction() {
    private static String transactionId = "12";

    public static String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String toString) {
        transactionId = toString;
    }
}
