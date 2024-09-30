package org.investpro;

import java.util.List;
import java.util.Objects;

public class Account {
    private String accountId;
    private String currency;
    private double balance;

    private String created;
    private double guaranteedStopLoss;
    private double free_margin;
    private double leverage;
    private double marginCall;
    public String id;
    private double guaranteedExecutionPrice;
    private double marginRate;
    private double unrealizedPL;
    private double lockedBalance;

    public String getId() {
        return id;
    }

    public String getMt4AccountID() {
        return mt4AccountID;
    }

    private double marginUsed;

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    String offset;
    public void setCreated(String created) {
        this.created = created;
    }

    public void setGuaranteedStopLoss(double guaranteedStopLoss) {
        this.guaranteedStopLoss = guaranteedStopLoss;
    }

    public double getFree_margin() {
        return free_margin;
    }

    public void setFree_margin(double free_margin) {
        this.free_margin = free_margin;
    }

    public double getLeverage() {
        return leverage;
    }

    public void setLeverage(double leverage) {
        this.leverage = leverage;
    }

    public void setMarginCall(double marginCall) {
        this.marginCall = marginCall;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    private double profit;


  static String mt4AccountID;


    // Constructors, getters, and setters

    public Account() { }

    public Account(String accountId, String currency, double balance) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = balance;


    }

    // Getters and Setters
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }


    @Override
    public String toString() {
        return "Account{accountId='%s', currency='%s', balance=%s, id='%s', tags='%s'}".formatted(accountId, currency, balance, id, tags);
    }




    public String getCreated() {
        return created;
    }

    public double getGuaranteedStopLoss() {
        return guaranteedStopLoss;
    }

    public double getMarginCall() {
        return marginCall;

    }

    public double getMarginFree() {

        return free_margin;
    }

    public String getMarginLevel() {
        return String.format("%.2f%%", (free_margin / balance) * 100);
    }

    public String getMarginUsed() {

        return String.format("%.2f%%", (1 - (free_margin / balance)) * 100);
    }

    public double getMaxLoss() {
        return balance * (1 - leverage) * 100;
    }

    public double getMaxProfit() {
        return balance * leverage * 100;
    }



    public double getProfit() {

        return  profit;
    }


        private List<String> tags;

        // Constructor


        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        // toString method for easy debugging and display


        // Equals and hashCode methods to compare Account objects
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Account account = (Account) o;
            return Objects.equals(id, account.id) &&
                    Objects.equals(mt4AccountID, account.mt4AccountID) &&
                    Objects.equals(tags, account.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, mt4AccountID, tags);
        }

    public void setGuaranteedExecutionPrice(double guaranteedExecutionPrice) {

            this.guaranteedExecutionPrice = guaranteedExecutionPrice;
    }

    public double getGuaranteedExecutionPrice() {
        return guaranteedExecutionPrice;
    }

    public void setId(String id) {
            this.id = id;
    }

    public void setMarginRate(double marginRate) {
            this.marginRate = marginRate;
    }

    public double getMarginRate() {
        return marginRate;
    }

    public void setUnrealizedPL(double unrealizedPL) {
            this.unrealizedPL=unrealizedPL;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setMarginUsed(double marginUsed) {
            this.marginUsed = marginUsed;
    }

    public void setMt4AccountID(int mt4AccountID) {
            Account.mt4AccountID = String.format("%08d", mt4AccountID);
    }

    public void setLockedBalance(double locked) {
            this.lockedBalance = locked;
    }

    public double getLockedBalance() {
        return lockedBalance;
    }
}
