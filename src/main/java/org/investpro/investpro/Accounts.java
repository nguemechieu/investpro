package org.investpro.investpro;

public class Accounts {
    private String tags;
    private String name;
    double balance;
    private String currency;
    private String accountType;
    private String accountStatus;
    private String tradingTimeZone;
    private String tradingTime;
    private String tradingMode;
    private String tradingStatus;


    public Accounts() {
        super();
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addTag(String name) {
        if (this.tags == null) {
            this.tags = "";
        } else this.tags = this.tags + "," + name;
    }

    private String tradingSession;

    public double getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public String getTradingTimeZone() {
        return tradingTimeZone;
    }

    public String getTradingTime() {
        return tradingTime;
    }

    public String getTradingMode() {
        return tradingMode;
    }

    public String getTradingStatus() {
        return tradingStatus;
    }

    public String getTradingSession() {
        return tradingSession;
    }
}
