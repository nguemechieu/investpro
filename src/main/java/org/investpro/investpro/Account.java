package org.investpro.investpro;

import java.util.ArrayList;

public class Account{
    public int createdByUserID;
    //@JsonProperty("NAV")
    public String nAV;
    public String marginCloseoutUnrealizedPL;
    public String marginCallMarginUsed;
    public int openPositionCount;
    public String withdrawalLimit;
    public String positionValue;
    public double marginRate;
    public String marginCallPercent;
    public double balance;
    public String lastTransactionID;
    public double resettablePL;
    public double financing;
    public String createdTime;
    public String alias;
    public String currency;
    public double commission;
    public double marginCloseoutPercent;
    public String id;
    public int openTradeCount;
    public int pendingOrderCount;
    public boolean hedgingEnabled;
    public String resettablePLTime;
    public ArrayList<Object> trades;
    public ArrayList<Position> positions;
    public String marginAvailable;
    public String dividendAdjustment;
    public String marginCloseoutPositionValue;
    public String marginCloseoutMarginUsed;
    public String unrealizedPL;
    public String marginCloseoutNAV;
    public String guaranteedStopLossOrderMode;
    public String marginUsed;
    public String guaranteedExecutionFees;
    public ArrayList<Object> orders;
    public String pl;
    public long positionCount;

    public Account() {
    }

    @Override
    public String toString() {
        return "Account{" +
                "createdByUserID=" + createdByUserID +
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
                ", currency='" + currency + '\'' +
                ", commission=" + commission +
                ", marginCloseoutPercent=" + marginCloseoutPercent +
                ", id='" + id + '\'' +
                ", openTradeCount=" + openTradeCount +
                ", pendingOrderCount=" + pendingOrderCount +
                ", hedgingEnabled=" + hedgingEnabled +
                ", resettablePLTime='" + resettablePLTime + '\'' +
                ", trades=" + trades +
                ", positions=" + positions +
                ", marginAvailable='" + marginAvailable + '\'' +
                ", dividendAdjustment='" + dividendAdjustment + '\'' +
                ", marginCloseoutPositionValue='" + marginCloseoutPositionValue + '\'' +
                ", marginCloseoutMarginUsed='" + marginCloseoutMarginUsed + '\'' +
                ", unrealizedPL='" + unrealizedPL + '\'' +
                ", marginCloseoutNAV='" + marginCloseoutNAV + '\'' +
                ", guaranteedStopLossOrderMode='" + guaranteedStopLossOrderMode + '\'' +
                ", marginUsed='" + marginUsed + '\'' +
                ", guaranteedExecutionFees='" + guaranteedExecutionFees + '\'' +
                ", orders=" + orders +
                ", pl='" + pl + '\'' +
                '}';
    }

    public int getCreatedByUserID() {
        return createdByUserID;
    }

    public void setCreatedByUserID(int createdByUserID) {
        this.createdByUserID = createdByUserID;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public ArrayList<Object> getTrades() {
        return trades;
    }

    public void setTrades(ArrayList<Object> trades) {
        this.trades = trades;
    }

    public ArrayList<Position> getPositions() {
        return positions;
    }

    public void setPositions(ArrayList<Position> positions) {
        this.positions = positions;
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

    public ArrayList<Object> getOrders() {
        return orders;
    }

    public void setOrders(ArrayList<Object> orders) {
        this.orders = orders;
    }

    public String getPl() {
        return pl;
    }

    public void setPl(String pl) {
        this.pl = pl;
    }
}
