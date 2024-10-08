package org.investpro;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "account") // Maps this class to the 'account' table in the database
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    long id;


    @Column(name = "currency")
    private String currency;

    @Column(name = "balance")
    double balance;

    @Column(name = "created")
    String created;

    @Column(name = "guaranteed_stop_loss")
    double guaranteedStopLoss;

    @Column(name = "free_margin")
    private double free_margin;

    @Column(name = "leverage")
    private double leverage;

    @Column(name = "margin_call")
    private double marginCall;

    @Column(name = "guaranteed_execution_price")
    double guaranteedExecutionPrice;

    @Column(name = "margin_rate")
    private double marginRate;

    @Column(name = "unrealized_pl")
    private double unrealizedPL;

    @Column(name = "locked_balance")
    double lockedBalance;
    @Column(name = "offset")
    String offset;
    @Column(name = "margin_used")
    private double marginUsed;
    @ElementCollection
    @CollectionTable(name = "account_tags", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "tag")
    List<String> tags = new ArrayList<>();



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

    public String getCreated() {
        return created;
    }
    @Column(name = "profit")
    private double profit;

    public double getGuaranteedStopLoss() {
        return guaranteedStopLoss;
    }
    // Commission fields
    @Column(name = "maker_commission")
    int makerCommission;
    @Column(name = "taker_commission")
    int takerCommission;
    @Column(name = "seller_commission")
    int sellerCommission;
    @Column(name = "buyer_commission")
    private int buyerCommission;
    @Embedded
    CommissionRates commissionRates;
    @ElementCollection
    @CollectionTable(name = "account_permissions", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "permission")
    List<String> permissions = new ArrayList<>();
    // Permissions and trading flags
    @Column(name = "can_trade")
    private boolean canTrade;
    @Column(name = "can_withdraw")
    private boolean canWithdraw;
    @Column(name = "can_deposit")
    private boolean canDeposit;
    @Column(name = "brokered")
    private boolean brokered;
    @Column(name = "require_self_trade_prevention")
    private boolean requireSelfTradePrevention;
    @Column(name = "update_time")
    private long updateTime;
    @Column(name = "account_type")
    private String accountType;
    private String mt4AccountID;

    public long getId() {
        return id;
    }


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

    public double getMarginCall() {
        return marginCall;
    }

    public void setMarginCall(double marginCall) {
        this.marginCall = marginCall;
    }

    public double getGuaranteedExecutionPrice() {
        return guaranteedExecutionPrice;
    }

    public void setGuaranteedExecutionPrice(double guaranteedExecutionPrice) {
        this.guaranteedExecutionPrice = guaranteedExecutionPrice;
    }

    public double getMarginRate() {
        return marginRate;
    }

    public void setMarginRate(double marginRate) {
        this.marginRate = marginRate;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public double getLockedBalance() {
        return lockedBalance;
    }

    public void setLockedBalance(double lockedBalance) {
        this.lockedBalance = lockedBalance;
    }

    public double getMarginUsed() {
        return marginUsed;
    }

    public void setMarginUsed(double marginUsed) {
        this.marginUsed = marginUsed;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getMakerCommission() {
        return makerCommission;
    }

    public void setMakerCommission(int makerCommission) {
        this.makerCommission = makerCommission;
    }

    public int getTakerCommission() {
        return takerCommission;
    }

    public void setTakerCommission(int takerCommission) {
        this.takerCommission = takerCommission;
    }

    public int getBuyerCommission() {
        return buyerCommission;
    }

    public void setBuyerCommission(int buyerCommission) {
        this.buyerCommission = buyerCommission;
    }

    public int getSellerCommission() {
        return sellerCommission;
    }

    public void setSellerCommission(int sellerCommission) {
        this.sellerCommission = sellerCommission;
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

    public boolean isBrokered() {
        return brokered;
    }

    public void setBrokered(boolean brokered) {
        this.brokered = brokered;
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

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    // Getters and Setters...

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(id, account.id) &&
                Objects.equals(tags, account.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tags);
    }

    public String getMt4AccountID() {
        return mt4AccountID;
    }

    public void setMt4AccountID(String mt4AccountID) {
        this.mt4AccountID = mt4AccountID;
    }

    @Embeddable
    public static class CommissionRates {
        @Column(name = "maker_rate")
        String maker;

        @Column(name = "taker_rate")
        String taker;

        @Column(name = "buyer_rate")
        String buyer;

        @Column(name = "seller_rate")
        String seller;

        public String getMaker() {
            return maker;
        }

        public void setMaker(String maker) {
            this.maker = maker;
        }

        public String getTaker() {
            return taker;
        }

        public void setTaker(String taker) {
            this.taker = taker;
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
// Getters and Setters...
    }

    @Embeddable
    public static class Balance {
        @Column(name = "asset")
        String asset;

        @Column(name = "free")
        double free;

        @Column(name = "locked")
        double locked;

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
// Getters and Setters...
    }
}
