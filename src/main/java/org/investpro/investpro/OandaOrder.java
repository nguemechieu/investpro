package org.investpro.investpro;

public class OandaOrder {
    private String id;
    private String status;
    private String currency;
    private String amount;
    private String amountInCents;
    private String date;

    public OandaOrder(String toString, String toString1, String toString2, String toString3) {
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getAmountInCents() {
        return amountInCents;
    }

    public void setAmountInCents(String amountInCents) {
        this.amountInCents = amountInCents;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
        if (id!= null) {
            this.status = "open";
            this.currency = "USD";
            this.amount = "0.00";
            this.amountInCents = "0.00";
            this.date = new java.util.Date().toString();
        }}
    
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;}

    public void setOrderId(Object o) {
    }

    public void setPrice(Object o) {
    }

    public void setSide(Object o) {
    }

    public void setUnits(Object o) {
    }
}
