package org.investpro;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
public class Account {
    private final List<Deposit> depositRecords = new CopyOnWriteArrayList<>();
    private final List<Withdrawal> withdrawalRecords = new CopyOnWriteArrayList<>();
    private String id;
    private String alias;
    private String currency;
    private double balance;
    private int openTradeCount;
    private int openPositionCount;
    private int pendingOrderCount;
    private double pl;  // Profit/Loss
    private double resettablePL;
    private long resettablePLTime;
    private double financing;
    private double commission;
    private double dividendAdjustment;
    private double guaranteedExecutionFees;
    private double unrealizedPL;
    @JsonProperty("NAV")
    private double NAV;  // Net Asset Value
    private double marginUsed;
    private double marginAvailable;
    private double positionValue;
    private double marginCloseoutUnrealizedPL;
    private double marginCloseoutNAV;
    private double marginCloseoutMarginUsed;
    private double marginCloseoutPositionValue;
    private double marginCloseoutPercent;
    private double withdrawalLimit;
    private double marginCallMarginUsed;
    private double marginCallPercent;
    private String guaranteedStopLossOrderMode;
    private boolean hedgingEnabled;
    private Date createdTime;
    private int createdByUserID;
    private String lastTransactionID;

    @JsonProperty("marginRate")
    private double marginRate;

    @JsonProperty("created")
    private String created;
    // Exchange-Specific Fields
    @JsonProperty("account_id")
    private Long accountId;

    @JsonProperty("canTrade")
    private boolean canTrade;

    @JsonProperty("canWithdraw")
    private boolean canWithdraw;
    @JsonProperty("mt4_account_id")
    private String mt4AccountId;

    private String permissions;
    private boolean requireSelfTradePrevention;
    private long updateTime;
    private String asset;
    private double total;
    private double free;
    private double locked;
    private double makerCommission;
    @JsonProperty("tags")
    private List<String> tags = new CopyOnWriteArrayList<>();
    private int leverage;
    private double realizedProfitLoss;
    private int positionSize;

    @JsonProperty("totalProfitLoss")
    private double totalProfitLoss;

    @JsonProperty("totalFees")
    private double totalFees;

    private double openPnL;
    private double totalCommissions;
    private double totalNetProfitLoss;
    private String accountType;
    private double closedPnL;
    @JsonProperty("canDeposit")
    private boolean canDeposit;
    @Setter
    private double takerCommission;
    // Exchange-Specific Data Structures
    @JsonProperty("commissionRates")
    private CommissionRates commissionRates = new CommissionRates();
    @JsonProperty("balances")
    private Balance balances = new Balance();
    private String brokered;

    public Account() {
    }

    /**
     * 🔹 Compute total deposits
     **/
    public double getTotalDeposits() {
        return depositRecords.stream().mapToDouble(Deposit::getAmount).sum();
    }

    /** 🔹 Compute total withdrawals **/
    public double getTotalWithdrawals() {
        return withdrawalRecords.stream().mapToDouble(Withdrawal::getAmount).sum();
    }

    /**
     * 🔹 Compute net balance
     **/
    public double getNetBalance() {
        return balance - getTotalLoss();
    }

    /** 🔹 Compute total loss **/
    public double getTotalLoss() {
        return getTotalDeposits() - getTotalWithdrawals();
    }

    /** 🔹 Compute total profit **/
    public double getTotalProfit() {
        return getTotalDeposits() - getTotalWithdrawals() - balance;
    }

    /** 🔹 Compute profitability percentage **/
    public double getProfitability() {
        return (getTotalProfit() / balance) * 100;
    }

    /** 🔹 Compute total trades **/
    public long getTotalTrades() {
        return depositRecords.size() + withdrawalRecords.size();
    }

    /** 🔹 Compute open positions **/
    public long getOpenPositions() {
        return depositRecords.stream().filter(d -> "open".equals(d.getOrderStatus())).count()
                + withdrawalRecords.stream().filter(w -> {
            w.getOrderStatus();
            return false;
        }).count();
    }

    /** 🔹 Compute closed positions **/
    public long getClosedPositions() {
        return depositRecords.stream().filter(d -> "closed".equals(d.getOrderStatus())).count()
                + withdrawalRecords.stream().filter(w -> {
            w.getOrderStatus();
            return false;
        }).count();
    }

    /** 🔹 Check trading status **/
    public String getTradingStatus() {
        if (getOpenPositions() > 0) return "open";
        if (getClosedPositions() > 0) return "closed";
        return "no positions";
    }

    /** 🔹 Compute free margin **/
    public double getFreeMargin() {
        return getEquity() - getBalance();
    }

    /**
     * 🔹 Compute margin level
     **/
    public double getMarginLevel() {
        return (getEquity() / getTotal()) * 100;
    }

    /**
     * 🔹 Compute equity
     **/
    public double getEquity() {
        return balance + balances.getLocked();
    }

    /** 🔹 Compute available balance **/
    public double getAvailableBalance() {
        return balances.getFree() + balances.getLocked();
    }

    /** 🔹 Compute unrealized profit/loss **/
    public double getUnrealizedProfitLoss() {
        return totalProfitLoss - totalFees;
    }

    /** 🔹 Check if under margin call **/
    public boolean isMarginCall() {
        return getMarginLevel() > 100;
    }

    /**
     * 🔹 Add deposit
     **/
    public void addDeposit(Deposit deposit) {
        depositRecords.add(deposit);
    }

    /**
     * 🔹 Add withdrawal
     **/
    public void addWithdrawal(Withdrawal withdrawal) {
        withdrawalRecords.add(withdrawal);
    }

    /** 🔹 Add balance details **/
    public void addBalance(String asset, double free, double locked) {
        balances.setAsset(asset);
        balances.setFree(free);
        balances.setLocked(locked);
        balances.setTotal(free + locked);
    }

    public void setBuyerCommission(double v) {
        this.makerCommission = v;
    }

    public void setSellerCommission(double v) {
        this.takerCommission = v;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId=" + accountId +
                ", alias='" + alias + '\'' +
                ", currency='" + currency + '\'' +
                ", balance=" + balance +
                ", marginRate=" + marginRate +
                ", NAV=" + NAV +
                ", openTradeCount=" + openTradeCount +
                ", totalProfitLoss=" + totalProfitLoss +
                ", totalNetProfitLoss=" + totalNetProfitLoss +
                ", canTrade=" + canTrade +
                ", canWithdraw=" + canWithdraw +
                ", permissions='" + permissions + '\'' +
                ", accountType='" + accountType + '\'' +
                '}';
    }

    /** 🔹 Inner Classes **/
    @Getter
    @Setter
    public static class Balance {
        @JsonProperty("asset")
        private String asset;

        @JsonProperty("free")
        private double free;

        private double total;

        @JsonProperty("locked")
        private double locked;

        @Override
        public String toString() {
            return "Balance{" + "asset='" + asset + '\'' + ", free=" + free + ", locked=" + locked + '}';
        }
    }

    @Getter
    @Setter
    public static class CommissionRates {
        @JsonProperty("maker")
        private double maker;

        @JsonProperty("taker")
        private double taker;

        @JsonProperty("buyer")
        private String buyer;

        @JsonProperty("seller")
        private String seller;

        @Override
        public String toString() {
            return "CommissionRates{" + "maker=" + maker + ", taker=" + taker + ", buyer='" + buyer + "', seller='" + seller + "'}";
        }
    }
}
