package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;

public class Account {
    private static final Logger logger = LoggerFactory.getLogger(Account.class);
    public String nAV;


    private String account;
    private String password;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String city;
    public String marginCloseoutUnrealizedPL;
    public String marginCallMarginUsed;
    public int openPositionCount;
    public String withdrawalLimit;
    public String positionValue;
    public double marginRate;
    public double balance;
    public String lastTransactionID;
    public double resettablePL;


    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
    public double financing;
    public String createdTime;
    //    string
//    Unique identifier for account.
    public String alias;
    public double commission;
    public double marginCloseoutPercent;
    public String id;
    public int pendingOrderCount;
    public boolean hedgingEnabled;
    public String resettablePLTime;
    public String marginAvailable;
    public String dividendAdjustment;
    public String marginCloseoutPositionValue;
    public String marginCloseoutMarginUsed;
    public String unrealizedPL;
    public String marginCloseoutNAV;
    public String guaranteedStopLossOrderMode;
    public String marginUsed;
    public String guaranteedExecutionFees;
    public String pl;
    String uuid;
    double nav = 0.0;
    double frozen;
    String permissions;
    //            string
    String symbol;// for the account.
    Object available_balance;
    //            object
//    required
    Object value;
    //    string
//    Amount of currency that this object represents.
//    currency
//            string
//    Denomination of the currency.
//    default
//    boolean
//    Whether or not this account is the user's primary account
    boolean active;
    //    boolean
//    Whether or not this account is active and okay to use.
    Date created_at;
    //   date_time;
//    Time at which this account was created.
    Date updated_at;
    //    date-time
//    Time at which this account was updated.
    Date deleted_at;
    //            string
    Object Possible_values;//: //[ACCOUNT_TYPE_UNSPECIFIED, ACCOUNT_TYPE_CRYPTO, ACCOUNT_TYPE_FIAT, ACCOUNT_TYPE_VAULT]
    boolean ready;
    //    boolean
//    Whether or not this account is ready to trade.
    Object hold;
    String createdByUserID;
    private Exchange exchange;
    private String currency;
    private String instrument;
    private String type;
    private String name;
    private double initialUnits;
    private double units;
    private int openTradeCount;
    private String marginCallPercent;
    private String asset;
    private boolean canTrade;
    private boolean canWithdraw;
    private boolean canDeposit;
    private boolean brokered;
    private boolean requireSelfTradePrevention;
    private long updateTime;
    private String accountType;
    private double[] commissionRates;
    private String accountID;
    private double margin;
    private double currentUnits;
    private double initialMarginRequired;
    private String state;
    private String openTime;
    private String closeTime;
    private double available;

    public Account(Exchange exchange, String username, String password) {

        this.password = password;
        this.account = username;
        logger.info("account.created", username);

        this.exchange = exchange;


    }

    public Account() {

    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public double getMargin() {
        return margin;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asText) {
        this.asset = asText;
    }

    public boolean isCanTrade() {
        return canTrade;
    }
    //    date-time
//    Time at which this account was deleted.

    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }
//            object
//    required

    //    string
//    Amount of currency that this object represents.
//            string
//    Denomination of the currency.

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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Object getAvailable_balance() {
        return available_balance;
    }

    public void setAvailable_balance(Object available_balance) {
        this.available_balance = available_balance;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Date created_at) {
        this.created_at = created_at;
    }

    public Date getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(Date updated_at) {
        this.updated_at = updated_at;
    }

    public Date getDeleted_at() {
        return deleted_at;
    }

    public void setDeleted_at(Date deleted_at) {
        this.deleted_at = deleted_at;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPossible_values() {
        return Possible_values;
    }

    public void setPossible_values(Object possible_values) {
        Possible_values = possible_values;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public Object getHold() {
        return hold;
    }

    public void setHold(Object hold) {
        this.hold = hold;
    }

    public Object getExchange() {
        return exchange;
    }

    public String getnAV() {
        return nAV;
    }

    public void setnAV(String nAV) {
        this.nAV = nAV;
    }

    public String getMarginCloseoutUnrealizedPL() {
        return marginCloseoutUnrealizedPL;
    }

    public void setMarginCloseoutUnrealizedPL(String marginCloseoutUnrealizedPL) {
        this.marginCloseoutUnrealizedPL = marginCloseoutUnrealizedPL;
    }

    public String getMarginCallMarginUsed() {
        return marginCallMarginUsed;
    }

    public void setMarginCallMarginUsed(String marginCallMarginUsed) {
        this.marginCallMarginUsed = marginCallMarginUsed;
    }

    public int getOpenPositionCount() {
        return openPositionCount;
    }

    public void setOpenPositionCount(int openPositionCount) {
        this.openPositionCount = openPositionCount;
    }

    public String getWithdrawalLimit() {
        return withdrawalLimit;
    }

    public void setWithdrawalLimit(String withdrawalLimit) {
        this.withdrawalLimit = withdrawalLimit;
    }

    public String getPositionValue() {
        return positionValue;
    }

    public void setPositionValue(String positionValue) {
        this.positionValue = positionValue;
    }

    public double getMarginRate() {
        return marginRate;
    }

    public void setMarginRate(double marginRate) {
        this.marginRate = marginRate;
    }

    public String getMarginCallPercent() {
        return marginCallPercent;
    }

    public void setMarginCallPercent(String marginCallPercent) {
        this.marginCallPercent = marginCallPercent;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getLastTransactionID() {
        return lastTransactionID;
    }

    public void setLastTransactionID(String lastTransactionID) {
        this.lastTransactionID = lastTransactionID;
    }

    public double getResettablePL() {
        return resettablePL;
    }

    public void setResettablePL(double resettablePL) {
        this.resettablePL = resettablePL;
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double commission) {
        this.commission = commission;
    }

    public double getMarginCloseoutPercent() {
        return marginCloseoutPercent;
    }

    public void setMarginCloseoutPercent(double marginCloseoutPercent) {
        this.marginCloseoutPercent = marginCloseoutPercent;
    }

    public int getOpenTradeCount() {
        return openTradeCount;
    }

    public void setOpenTradeCount(int openTradeCount) {
        this.openTradeCount = openTradeCount;
    }

    public int getPendingOrderCount() {
        return pendingOrderCount;
    }

    public void setPendingOrderCount(int pendingOrderCount) {
        this.pendingOrderCount = pendingOrderCount;
    }

    public boolean isHedgingEnabled() {
        return hedgingEnabled;
    }

    public void setHedgingEnabled(boolean hedgingEnabled) {
        this.hedgingEnabled = hedgingEnabled;
    }

    public String getResettablePLTime() {
        return resettablePLTime;
    }

    public void setResettablePLTime(String resettablePLTime) {
        this.resettablePLTime = resettablePLTime;
    }

    public String getMarginAvailable() {
        return marginAvailable;
    }

    public void setMarginAvailable(String marginAvailable) {
        this.marginAvailable = marginAvailable;
    }

    public String getDividendAdjustment() {
        return dividendAdjustment;
    }

    public void setDividendAdjustment(String dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public String getMarginCloseoutPositionValue() {
        return marginCloseoutPositionValue;
    }

    public void setMarginCloseoutPositionValue(String marginCloseoutPositionValue) {
        this.marginCloseoutPositionValue = marginCloseoutPositionValue;

    }

    public String getMarginCloseoutMarginUsed() {
        return marginCloseoutMarginUsed;
    }

    public void setMarginCloseoutMarginUsed(String marginCloseoutMarginUsed) {
        this.marginCloseoutMarginUsed = marginCloseoutMarginUsed;
    }

    public String getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(String unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public String getMarginCloseoutNAV() {
        return marginCloseoutNAV;
    }

    public void setMarginCloseoutNAV(String marginCloseoutNAV) {
        this.marginCloseoutNAV = marginCloseoutNAV;
    }

    public String getGuaranteedStopLossOrderMode() {
        return guaranteedStopLossOrderMode;
    }

    public void setGuaranteedStopLossOrderMode(String guaranteedStopLossOrderMode) {
        this.guaranteedStopLossOrderMode = guaranteedStopLossOrderMode;
    }

    public String getMarginUsed() {
        return marginUsed;
    }

    public void setMarginUsed(String marginUsed) {
        this.marginUsed = marginUsed;
    }

    public String getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public void setGuaranteedExecutionFees(String guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    public String getPl() {
        return pl;
    }

    public void setPl(String pl) {
        this.pl = pl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setId(Long id) {
        this.id = String.valueOf(id);
    }

    public double getAvailable() {
        return available;
    }

    public void setAvailable(double available) {
        this.available = available;
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

    public double[] getCommissionRates() {
        return commissionRates;
    }

    public void setCommissionRates(double[] commissionRates) {
        this.commissionRates = commissionRates;
    }

    public void setCommissionRates(double asDouble, double asDouble1, double asDouble2, double asDouble3) {
        this.commissionRates = new double[]{asDouble, asDouble1, asDouble2, asDouble3};
    }

    public String getAccountID() {
        return accountID;
    }

    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    @Override
    public String toString() {

        return "Account{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", asset='" + asset + '\'' +
                ", canTrade=" + canTrade +
                ", canWithdraw=" + canWithdraw +
                ", canDeposit=" + canDeposit +
                ", brokered=" + brokered +
                ", requireSelfTradePrevention=" + requireSelfTradePrevention +
                ", updateTime=" + updateTime +
                ", accountType='" + accountType + '\'' +
                ", commissionRates=" + Arrays.toString(commissionRates) +
                ", accountID='" + accountID + '\'' +
                ", margin=" + margin +
                ", instrument='" + instrument + '\'' +
                ", initialUnits=" + initialUnits +
                ", currentUnits=" + currentUnits +
                ", units=" + units +
                ", initialMarginRequired=" + initialMarginRequired +
                ", state='" + state + '\'' +
                ", openTime='" + openTime + '\'' +
                ", closeTime='" + closeTime + '\'' +
                ", available=" + available +
                ", currency='" + currency + '\'' +
                ", symbol='" + symbol + '\'' +
                ", available_balance=" + available_balance +
                ", value=" + value +
                ", active=" + active +
                ", created_at=" + created_at +
                ", updated_at=" + updated_at +
                ", deleted_at=" + deleted_at +
                ", type='" + type + '\'' +
                ", Possible_values=" + Possible_values +
                ", ready=" + ready +
                ", hold=" + hold +
                ", createdByUserID=" + createdByUserID +
                ", nAV='" + nAV + '\'' +
                ", marginCloseoutUnrealizedPL='" + marginCloseoutUnrealizedPL + '\'' +
                ", marginCallMarginUsed='" + marginCallMarginUsed + '\'' +
                ", openPositionCount=" + openPositionCount +
                ", withdrawalLimit='" + withdrawalLimit + '\'' +
                ", positionValue='" + positionValue + '\'' +
                ", marginRate=" + marginRate +
                ", marginCallPercent='" + marginCallPercent + '\'' +
                ", balance=" + balance +
                ", lastTransactionID='" + lastTransactionID + '\'' +
                ", resettablePL=" + resettablePL +
                ", financing=" + financing +
                ", createdTime='" + createdTime + '\'' +
                ", alias='" + alias + '\'' +
                ", commission=" + commission +
                ", marginCloseoutPercent=" + marginCloseoutPercent +
                ", id='" + id + '\'' +
                ", openTradeCount=" + openTradeCount +
                ", pendingOrderCount=" + pendingOrderCount +
                ", hedgingEnabled=" + hedgingEnabled +
                ", resettablePLTime='" + resettablePLTime + '\'' +
                ", marginAvailable='" + marginAvailable + '\'' +
                ", dividendAdjustment='" + dividendAdjustment + '\'' +
                ", marginCloseoutPositionValue='" + marginCloseoutPositionValue + '\'' +
                ", marginCloseoutMarginUsed='" + marginCloseoutMarginUsed + '\'' +
                ", unrealizedPL='" + unrealizedPL + '\'' +
                ", marginCloseoutNAV='" + marginCloseoutNAV + '\'' +
                ", guaranteedStopLossOrderMode='" + guaranteedStopLossOrderMode + '\'' +
                ", marginUsed='" + marginUsed + '\'' +
                ", guaranteedExecutionFees='" + guaranteedExecutionFees + '\'' +
                ", pl='" + pl + '\'' +
                ", exchange=" + exchange +
                ", nav=" + nav +
                '}';
    }

    public void setNAV(double nav) {
        this.nav = nav;
    }

    public void setInitialUnits(double initialUnits) {
        this.initialUnits = initialUnits;
    }

    public void setCurrentUnits(double currentUnits) {
        this.currentUnits = currentUnits;
    }

    public void setUnits(double units) {
        this.units = units;
    }

    public void setInitialMarginRequired(double initialMarginRequired) {
        this.initialMarginRequired = initialMarginRequired;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public void setFrozen(double frozen) {
        this.frozen = frozen;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
