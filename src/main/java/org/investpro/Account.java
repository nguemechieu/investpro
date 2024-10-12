package org.investpro;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Account {

    private final ArrayList<Deposit> assetLogRecordList = new ArrayList<>();
    private final ArrayList<Withdrawal> withdrawalLogRecordList = new ArrayList<>();
    @JsonProperty("commissionRates")
    CommissionRates commissionRates = new CommissionRates();
    @JsonProperty("balances")
    Balance balance = new Balance();
    @JsonProperty("account_id")
    private Long account_id;
    @JsonProperty("mt4_account_id")
    private String mt4_account_id;
    @JsonProperty("marginRate")
    private double marginRate;
    @JsonProperty("created")
    private String created;
    @JsonProperty("tags")
    private List<String> tags;
    @JsonProperty("canTrade")
    private boolean canTrade;
    @JsonProperty("canWithdraw")
    private boolean canWithdraw;
    private String permissions;
    private boolean requireSelfTradePrevention;
    private long updateTime;
    private String asset;
    private double total;
    private String maker;
    private double free;
    private String taker;
    private double locked;
    private boolean brokered;
    private double sellerCommission;
    private double buyerCommission;
    private double makerCommission;
    private double takerCommission;
    private int leverage;
    private double realizedProfitLoss;
    private String position;
    private int positionSize;
    @JsonProperty("canDeposit")
    private boolean canDeposit;
    @JsonProperty("totalProfitLoss")
    private double totalProfitLoss;
    @JsonProperty("totalFees")
    private double totalFees;
    private double openPnL;
    private double totalCommissions;
    private double totalNetProfitLoss;
    private String accountType;
    private double closedPnL;

    public Account() {

        this.tags = new ArrayList<>();

    }

    public String getMt4_account_id() {
        return mt4_account_id;
    }

    // Add getters and setters

    public Long getAccount_id() {
        return account_id;
    }

    public void setAccount_id(Long account_id) {
        this.account_id = account_id;
    }
    // More getters and setters for other fields...

    public void setMt4_account_id(String mt4_account_id) {
        this.mt4_account_id = mt4_account_id;
    }

    public CommissionRates getCommissionRates() {
        return commissionRates;
    }

    public void setCommissionRates(CommissionRates commissionRates) {
        this.commissionRates = commissionRates;
    }

    public boolean isCanTrade() {
        return canTrade;
    }

    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }

    public boolean isCanDeposit() {
        return canDeposit;
    }

    public void setCanDeposit(boolean canDeposit) {
        this.canDeposit = canDeposit;
    }

    public double getTotalFees() {
        return totalFees;
    }

    public void setTotalFees(double totalFees) {
        this.totalFees = totalFees;
    }

    public double getTotalCommissions() {
        return totalCommissions;
    }

    public void setTotalCommissions(double totalCommissions) {
        this.totalCommissions = totalCommissions;
    }

    public double getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public void setTotalProfitLoss(double totalProfitLoss) {
        this.totalProfitLoss = totalProfitLoss;
    }

    public double getTotalNetProfitLoss() {
        return totalNetProfitLoss;
    }

    public void setTotalNetProfitLoss(double totalNetProfitLoss) {
        this.totalNetProfitLoss = totalNetProfitLoss;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public double getTotalDeposits() {
        double totalDeposits = 0;
        for (Deposit deposit : assetLogRecordList) {
            totalDeposits += deposit.getAmount();
        }
        return totalDeposits;
    }

    public double getTotalWithdrawals() {
        double totalWithdrawals = 0;
        for (Withdrawal withdrawal : withdrawalLogRecordList) {
            totalWithdrawals += withdrawal.getAmount();
        }
        return totalWithdrawals;
    }


    public void setMt4AccountID(String id) {

        this.mt4_account_id = id;
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

    public Date getLastDepositDate() {
        if (assetLogRecordList.isEmpty()) {
            return null;
        }
        return assetLogRecordList.getLast().getCreatedAt();
    }

    public Date getLastWithdrawalDate() {
        if (withdrawalLogRecordList.isEmpty()) {
            return null;
        }
        return withdrawalLogRecordList.getLast().getCreatedAt();
    }

    public String getCurrency() {
        if (assetLogRecordList.isEmpty()) {
            return null;
        }
        return assetLogRecordList.getFirst().getFiatCurrency();
    }

    public double getTotalLoss() {
        return getTotalDeposits() - getTotalWithdrawals();
    }

    @Override
    public String toString() {
        return "Account{" +
                "account_id=" + account_id +
                ", mt4_account_id='" + mt4_account_id + '\'' +
                ", marginRate=" + marginRate +
                ", created='" + created + '\'' +
                ", tags=" + tags +
                ", commissionRates=" + commissionRates +
                ", balance=" + balance +
                ", canTrade=" + canTrade +
                ", canWithdraw=" + canWithdraw +
                ", permissions='" + permissions + '\'' +
                ", requireSelfTradePrevention=" + requireSelfTradePrevention +
                ", updateTime=" + updateTime +
                ", asset='" + asset + '\'' +
                ", total=" + total +
                ", maker='" + maker + '\'' +
                ", free=" + free +
                ", taker='" + taker + '\'' +
                ", locked=" + locked +
                ", brokered=" + brokered +
                ", sellerCommission=" + sellerCommission +
                ", buyerCommission=" + buyerCommission +
                ", makerCommission=" + makerCommission +
                ", takerCommission=" + takerCommission +
                ", canDeposit=" + canDeposit +
                ", totalProfitLoss=" + totalProfitLoss +
                ", totalFees=" + totalFees +
                ", assetLogRecordList=" + assetLogRecordList +
                ", withdrawalLogRecordList=" + withdrawalLogRecordList +
                ", openPnL=" + openPnL +
                ", totalCommissions=" + totalCommissions +
                ", totalNetProfitLoss=" + totalNetProfitLoss +
                ", accountType='" + accountType + '\'' +
                ", closedPnL=" + closedPnL +
                '}';
    }

    public double getNetBalance() {
        return balance.getFree() - getTotalLoss();
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void addDeposit(Deposit deposit) {
        assetLogRecordList.add(deposit);
    }

    public void addWithdrawal(Withdrawal withdrawal) {
        withdrawalLogRecordList.add(withdrawal);
    }

    public double getTotalProfit() {
        return getTotalDeposits() - getTotalWithdrawals() - balance.getFree();
    }

    public double getProfitability() {
        return (getTotalProfit() / balance.getFree() * 100);
    }

    public long getTotalTrades() {
        return assetLogRecordList.size() + withdrawalLogRecordList.size();
    }

    public long getOpenPositions() {
        return assetLogRecordList.stream().filter(deposit -> deposit.getOrderStatus().equals("open")).count() +
                withdrawalLogRecordList.stream().filter(withdrawal -> {
                    withdrawal.getOrderStatus();
                    return false;
                }).count();
    }

    public long getClosedPositions() {
        return assetLogRecordList.stream().filter(deposit -> deposit.getOrderStatus().equals("closed")).count() +
                withdrawalLogRecordList.stream().filter(withdrawal -> {
                    withdrawal.getOrderStatus();
                    return false;
                }).count();
    }

    public double getOpenPnL() {
        return openPnL;
    }

    public void setOpenPnL(double openPnL) {
        this.openPnL = openPnL;
    }

    public double getClosedPnL() {
        return closedPnL;
    }

    public void setClosedPnL(double closedPnL) {
        this.closedPnL = closedPnL;
    }

    public String getTradingStatus() {
        if (getOpenPositions() > 0) {
            return "open";
        } else if (getClosedPositions() > 0) {
            return "closed";
        } else {
            return "no positions";
        }
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public boolean isRequireSelfTradePrevention() {
        return requireSelfTradePrevention;
    }

    public void setRequireSelfTradePrevention(boolean requireSelfTradePrevention) {
        this.requireSelfTradePrevention = requireSelfTradePrevention;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {

        this.updateTime = updateTime;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getMaker() {
        return maker;
    }

    public void setMaker(String maker) {
        this.maker = maker;
    }

    public double getFree() {
        return free;
    }

    public void setFree(double free) {
        this.free = free;
    }

    public String getTaker() {
        return taker;
    }

    public void setTaker(String taker) {
        this.taker = taker;
    }

    public double getLocked() {
        return locked;
    }

    public void setLocked(double locked) {
        this.locked = locked;
    }

    public boolean isBrokered() {
        return brokered;
    }

    public void setBrokered(boolean brokered) {
        this.brokered = brokered;
    }

    public double getSellerCommission() {
        return sellerCommission;
    }

    public void setSellerCommission(double sellerCommission) {
        this.sellerCommission = sellerCommission;
    }

    public double getBuyerCommission() {
        return buyerCommission;
    }

    public void setBuyerCommission(double buyerCommission) {
        this.buyerCommission = buyerCommission;
    }

    public double getMakerCommission() {
        return makerCommission;
    }

    public void setMakerCommission(double makerCommission) {
        this.makerCommission = makerCommission;
    }

    public double getTakerCommission() {
        return takerCommission;
    }

    public void setTakerCommission(double takerCommission) {
        this.takerCommission = takerCommission;
    }

    public double getEquity() {
        return balance.getFree() + balance.getLocked();
    }

    public double getFreeMargin() {
        return getEquity() - getMarginBalance();
    }

    private double getMarginBalance() {
        return (balance.getFree() + balance.getLocked()) * (1 - getTakerCommission() / 100);
    }

    public double getAvailableBalance() {
        return balance.getFree() + balance.getLocked();
    }

    public double getUnrealizedProfitLoss() {
        return totalProfitLoss - totalFees;
    }

    public double getMarginLevel() {
        return (getEquity() / getTotal()) * 100;
    }

    public boolean isMarginCall() {
        return getMarginLevel() > 100;
    }

    public int getLeverage() {

        return leverage;
    }

    public void setLeverage(int leverage) {
        this.leverage = leverage;
    }

    public double getRealizedProfitLoss() {
        return realizedProfitLoss;
    }

    public void setRealizedProfitLoss(double realizedProfitLoss) {
        this.realizedProfitLoss = realizedProfitLoss;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public int getPositionSize() {
        return positionSize;
    }

    public void setPositionSize(int positionSize) {
        this.positionSize = positionSize;
    }

    public void addBalance(String asset, double free, double locked) {
        this.balance.setAsset(asset);
        this.balance.setFree(free);
        this.balance.setLocked(locked);
        this.balance.setTotal(free + locked);
    }

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
            return "Balance{" +
                    "asset='" + asset + '\'' +
                    ", free=" + free +
                    ", locked=" + locked +
                    '}';
        }

        // Getters and setters
        public String getAsset() {
            return asset;
        }

        public void setAsset(String asset) {
            this.asset = asset;
        }

        public double getFree() {
            return free;
        }

        public void setFree(double free) {
            this.free = free;
        }

        public double getLocked() {
            return locked;
        }

        public void setLocked(double locked) {
            this.locked = locked;
        }

        public double getTotal() {
            return total;
        }

        public void setTotal(double v) {
            this.total = v;
        }
    }

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
            return "CommissionRates{" +
                    "maker=" + maker +
                    ", taker=" + taker +
                    ", buyer='" + buyer + '\'' +
                    ", seller='" + seller + '\'' +
                    '}';
        }

        // Getters and setters
        public double getMaker() {
            return maker;
        }

        public void setMaker(double maker) {
            this.maker = maker;
        }

        public String getBuyer() {
            return buyer;
        }

        public void setBuyer(String buyer) {
            this.buyer = buyer;
        }

        public String getSeller() {
            return seller;
        }

        public void setSeller(String seller) {
            this.seller = seller;
        }

        public double getTaker() {
            return taker;
        }

        public void setTaker(double aDouble) {
            this.taker = aDouble;
        }
        // Add other getters and setters...
    }


}
