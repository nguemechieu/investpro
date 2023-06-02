package org.investpro;


public class Accounts {
    double balance;
    private String tags;
    private double marginPercent;
    private double profit;
    private double low;
    private double high;
    private double resettableBalance;
    private double pnl;
    private double previousUrgeLoss;
    private double open;
    private String name;
    private String currency;
    private String accountType;
    private String accountStatus;
    private String tradingTimeZone;
    private String tradingTime;
    private String tradingMode;
    private String tradingStatus;
    private String tradingSession;
    private String accountID;

    public Accounts() {
        super();
    }

    @Override
    public String toString() {
        return "Accounts{" +
                "balance=" + balance +
                ", tags='" + tags + '\'' +
                ", name='" + name + '\'' +
                ", currency='" + currency + '\'' +
                ", accountType='" + accountType + '\'' +
                ", accountStatus='" + accountStatus + '\'' +
                ", tradingTimeZone='" + tradingTimeZone + '\'' +
                ", tradingTime='" + tradingTime + '\'' +
                ", tradingMode='" + tradingMode + '\'' +
                ", tradingStatus='" + tradingStatus + '\'' +
                ", tradingSession='" + tradingSession + '\'' +
                ", accountID='" + accountID + '\'' +
                '}';
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

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getTradingTimeZone() {
        return tradingTimeZone;
    }

    public void setTradingTimeZone(String tradingTimeZone) {
        this.tradingTimeZone = tradingTimeZone;
    }

    public String getTradingTime() {
        return tradingTime;
    }

    public void setTradingTime(String tradingTime) {
        this.tradingTime = tradingTime;
    }

    public String getTradingMode() {
        return tradingMode;
    }

    public void setTradingMode(String tradingMode) {
        this.tradingMode = tradingMode;
    }

    public String getTradingStatus() {
        return tradingStatus;
    }

    public void setTradingStatus(String tradingStatus) {
        this.tradingStatus = tradingStatus;
    }

    public String getTradingSession() {
        return tradingSession;
    }

    public void setTradingSession(String tradingSession) {
        this.tradingSession = tradingSession;
    }

    public String getAccountID() {
        return accountID;
    }

    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    public double getMarginPercent() {
        return marginPercent;
    }

    public void setMarginPercent(double marginPercent) {
        this.marginPercent = marginPercent;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getFreeMargin() {
        return balance;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public double getReset() {
        return resettableBalance;
    }

    public void setResettableBalance(double resettableBalance) {
        this.resettableBalance = resettableBalance;
    }

    public double getPNL() {
        return pnl;
    }

    public void setPnl(double pnl) {
        this.pnl = pnl;
    }

    public double getPreviousUrgeLoss() {
        return previousUrgeLoss;
    }

    public double getMarketAnalysis() {
        return marginPercent;
    }

    public double getTradeSignal() {
        return profit;
    }

    public double getTradeHistory() {
        return profit;
    }


}
