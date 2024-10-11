package org.investpro;

import java.util.List;

public class Account {
    private double balance;
    private Long account_id;
    private String mt4_account_id;
    private double marginRate;
    private String created;
    private List<String> tags;

    public double getBalance() {
        return balance;
    }

    public Account() {
    }

    public Long getAccount_id() {
        return account_id;
    }

    public void setAccount_id(Long account_id) {
        this.account_id = account_id;
    }

    public void setMt4AccountID(String id) {

        this.mt4_account_id = id;
    }

    public String getMt4_account_id() {
        return mt4_account_id;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void setMarginRate(double marginRate) {
        this.marginRate = marginRate;
    }

    public double getMarginRate() {
        return marginRate;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String createdTime) {

        this.created = createdTime;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTags() {
        return tags;
    }
}
