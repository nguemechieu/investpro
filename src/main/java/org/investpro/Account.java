package org.investpro;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@Entity
@Table(name = "Account")
public class Account extends RecursiveTreeObject<Account> implements @NotNull List<Account> {

    private static final Logger logger = LoggerFactory.getLogger(Account.class);
    String uuid;
    //    string
//    Unique identifier for account.
    String name;
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
    double nav = 0.0;
    private String instrument;
    private double initialUnits;
    private double currentUnits;
    private double units;
    private double initialMarginRequired;
    private String state;
    private String openTime;
    private String closeTime;


    public Account(@NotNull JSONObject account) {
        logger.info("Account: " + account);

        // {"account":{"guaranteedStopLossOrderMode":"DISABLED",
        // "hedgingEnabled":false,"id":"001-001-2783446-002",
        // "createdTime":"2019-04-30T02:39:18.895364468Z","currency":"USD","createdByUserID":2783446,"alias":"MT4",
        // "marginRate":"0.02","lastTransactionID":"143166","balance":"51.4613",
        // "openTradeCount":1,"openPositionCount":1,"pendingOrderCount":0,"pl":"-914.0620",
        // "resettablePL":"-914.0620","resettablePLTime":"0","financing":"-13.1795","
        // commission":"0.2672","dividendAdjustment":"0",
        // "guaranteedExecutionFees":"0.0000",
        // "orders":[],"positions":[{"instrument":"EUR_USD","long":{"units":"0","pl":"-99.1666","resettablePL":"-99.1666","financing":"-7.7397","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"-2300","averagePrice":"1.08814","pl":"-133.7496","resettablePL":"-133.7496","financing":"2.0142","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","tradeIDs":["143152"],"unrealizedPL":"8.7400"},"pl":"-232.9162","resettablePL":"-232.9162","financing":"-5.7255","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"8.7400","marginUsed":"49.8741"},{"instrument":"EUR_GBP","long":{"units":"0","pl":"0.3154","resettablePL":"0.3154","financing":"-0.0105","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.5702","resettablePL":"-0.5702","financing":"-0.0024","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.2548","resettablePL":"-0.2548","financing":"-0.0129","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_AUD","long":{"units":"0","pl":"-7.9081","resettablePL":"-7.9081","financing":"-0.0016","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.4026","resettablePL":"0.4026","financing":"0.0018","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-7.5055","resettablePL":"-7.5055","financing":"0.0002","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CAD","long":{"units":"0","pl":"-5.4214","resettablePL":"-5.4214","financing":"-0.5173","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-1.3465","resettablePL":"-1.3465","financing":"-0.0027","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-6.7679","resettablePL":"-6.7679","financing":"-0.5200","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_SGD","long":{"units":"0","pl":"-1.3650","resettablePL":"-1.3650","financing":"-0.0024","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-1.5949","resettablePL":"-1.5949","financing":"-0.0001","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-2.9599","resettablePL":"-2.9599","financing":"-0.0025","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CHF","long":{"units":"0","pl":"2.9766","resettablePL":"2.9766","financing":"-0.1421","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.2801","resettablePL":"0.2801","financing":"-0.0021","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"3.2567","resettablePL":"3.2567","financing":"-0.1442","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_JPY","long":{"units":"0","pl":"-1.0765","resettablePL":"-1.0765","financing":"-0.0975","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-10.0630","resettablePL":"-10.0630","financing":"-0.0131","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-11.1395","resettablePL":"-11.1395","financing":"-0.1106","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_NZD","long":{"units":"0","pl":"2.8772","resettablePL":"2.8772","financing":"-0.2898","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-138.8771","resettablePL":"-138.8771","financing":"0.6645","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-135.9999","resettablePL":"-135.9999","financing":"0.3747","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_HKD","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1659","resettablePL":"-0.1659","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1659","resettablePL":"-0.1659","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CZK","long":{"units":"0","pl":"-2.1723","resettablePL":"-2.1723","financing":"-0.0078","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1557","resettablePL":"-0.1557","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-2.3280","resettablePL":"-2.3280","financing":"-0.0078","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_NOK","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1526","resettablePL":"-0.1526","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1526","resettablePL":"-0.1526","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_SEK","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0180","resettablePL":"-0.0180","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0180","resettablePL":"-0.0180","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_TRY","long":{"units":"0","pl":"-0.0959","resettablePL":"-0.0959","financing":"-0.0001","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0959","resettablePL":"-0.0959","financing":"-0.0001","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_ZAR","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0682","resettablePL":"-0.0682","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0682","resettablePL":"-0.0682","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CAD","long":{"units":"0","pl":"-29.8908","resettablePL":"-29.8908","financing":"-0.9440","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-44.8502","resettablePL":"-44.8502","financing":"-0.1419","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-74.7410","resettablePL":"-74.7410","financing":"-1.0859","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_SGD","long":{"units":"0","pl":"0.6893","resettablePL":"0.6893","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.6893","resettablePL":"0.6893","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CHF","long":{"units":"0","pl":"-12.2150","resettablePL":"-12.2150","financing":"0.3309","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"1.8985","resettablePL":"1.8985","financing":"-0.3812","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-10.3165","resettablePL":"-10.3165","financing":"-0.0503","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_JPY","long":{"units":"0","pl":"-84.0042","resettablePL":"-84.0042","financing":"0.3929","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-35.5903","resettablePL":"-35.5903","financing":"-1.7775","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-119.5945","resettablePL":"-119.5945","financing":"-1.3846","commission":"0.1172","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_HKD","long":{"units":"0","pl":"-0.3687","resettablePL":"-0.3687","financing":"-0.1380","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0547","resettablePL":"0.0547","financing":"-0.0055","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.3140","resettablePL":"-0.3140","financing":"-0.1435","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CZK","long":{"units":"0","pl":"-2.7163","resettablePL":"-2.7163","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-2.7163","resettablePL":"-2.7163","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_DKK","long":{"units":"0","pl":"-0.0077","resettablePL":"-0.0077","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0077","resettablePL":"-0.0077","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_MXN","long":{"units":"0","pl":"-0.0951","resettablePL":"-0.0951","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0564","resettablePL":"-0.0564","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1515","resettablePL":"-0.1515","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_NOK","long":{"units":"0","pl":"-0.0108","resettablePL":"-0.0108","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0108","resettablePL":"-0.0108","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_PLN","long":{"units":"0","pl":"-0.0991","resettablePL":"-0.0991","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0991","resettablePL":"-0.0991","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_SEK","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0132","resettablePL":"-0.0132","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0132","resettablePL":"-0.0132","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_THB","long":{"units":"0","pl":"-0.1990","resettablePL":"-0.1990","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1990","resettablePL":"-0.1990","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CNH","long":{"units":"0","pl":"-1.0486","resettablePL":"-1.0486","financing":"-0.0074","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-10.7225","resettablePL":"-10.7225","financing":"-0.0092","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-11.7711","resettablePL":"-11.7711","financing":"-0.0166","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_USD","long":{"units":"0","pl":"-10.0528","resettablePL":"-10.0528","financing":"-0.8398","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-23.1159","resettablePL":"-23.1159","financing":"-0.0970","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-33.1687","resettablePL":"-33.1687","financing":"-0.9368","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_AUD","long":{"units":"0","pl":"-0.1069","resettablePL":"-0.1069","financing":"-0.0001","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0075","resettablePL":"-0.0075","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1144","resettablePL":"-0.1144","financing":"-0.0001","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_CAD","long":{"units":"0","pl":"-5.2250","resettablePL":"-5.2250","financing":"-0.0355","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-3.0620","resettablePL":"-3.0620","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-8.2870","resettablePL":"-8.2870","financing":"-0.0355","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_SGD","long":{"units":"0","pl":"-0.8250","resettablePL":"-0.8250","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.8250","resettablePL":"-0.8250","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_CHF","long":{"units":"0","pl":"-4.4219","resettablePL":"-4.4219","financing":"0.0005","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1570","resettablePL":"-0.1570","financing":"-0.0687","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-4.5789","resettablePL":"-4.5789","financing":"-0.0682","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_JPY","long":{"units":"0","pl":"-9.7444","resettablePL":"-9.7444","financing":"-0.0043","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.4019","resettablePL":"0.4019","financing":"-0.0653","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-9.3425","resettablePL":"-9.3425","financing":"-0.0696","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_NZD","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.2934","resettablePL":"-0.2934","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.2934","resettablePL":"-0.2934","financing":"0.0000","commission":"0.1500","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_PLN","long":{"units":"0","pl":"-0.5359","resettablePL":"-0.5359","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.2639","resettablePL":"-0.2639","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.7998","resettablePL":"-0.7998","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_ZAR","long":{"units":"0","pl":"-0.0420","resettablePL":"-0.0420","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0168","resettablePL":"-0.0168","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0588","resettablePL":"-0.0588","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_USD","long":{"units":"0","pl":"-26.7262","resettablePL":"-26.7262","financing":"-0.6898","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-43.7439","resettablePL":"-43.7439","financing":"-0.1078","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-70.4701","resettablePL":"-70.4701","financing":"-0.7976","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_CAD","long":{"units":"0","pl":"-2.9320","resettablePL":"-2.9320","financing":"-0.0339","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-18.4886","resettablePL":"-18.4886","financing":"-0.0171","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-21.4206","resettablePL":"-21.4206","financing":"-0.0510","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_SGD","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.6495","resettablePL":"-2.6495","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-2.6495","resettablePL":"-2.6495","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_CHF","long":{"units":"0","pl":"-8.4263","resettablePL":"-8.4263","financing":"0.0082","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.2083","resettablePL":"0.2083","financing":"-0.0090","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-8.2180","resettablePL":"-8.2180","financing":"-0.0008","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_JPY","long":{"units":"0","pl":"-2.4061","resettablePL":"-2.4061","financing":"-0.1419","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-9.0972","resettablePL":"-9.0972","financing":"-0.6077","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-11.5033","resettablePL":"-11.5033","financing":"-0.7496","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_NZD","long":{"units":"0","pl":"-6.1148","resettablePL":"-6.1148","financing":"-0.0231","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-11.3032","resettablePL":"-11.3032","financing":"-0.0377","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-17.4180","resettablePL":"-17.4180","financing":"-0.0608","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_HKD","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1069","resettablePL":"-0.1069","financing":"-0.0008","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1069","resettablePL":"-0.1069","financing":"-0.0008","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_SGD","long":{"units":"0","pl":"0.0036","resettablePL":"0.0036","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.2758","resettablePL":"-0.2758","financing":"-0.0006","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.2722","resettablePL":"-0.2722","financing":"-0.0006","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_CHF","long":{"units":"0","pl":"-2.7829","resettablePL":"-2.7829","financing":"0.0009","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.8108","resettablePL":"-2.8108","financing":"-0.0095","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-5.5937","resettablePL":"-5.5937","financing":"-0.0086","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_JPY","long":{"units":"0","pl":"-2.5502","resettablePL":"-2.5502","financing":"0.0110","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-4.1264","resettablePL":"-4.1264","financing":"-0.0157","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-6.6766","resettablePL":"-6.6766","financing":"-0.0047","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_HKD","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.9540","resettablePL":"-0.9540","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.9540","resettablePL":"-0.9540","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"SGD_CHF","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-1.8765","resettablePL":"-1.8765","financing":"-0.0040","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-1.8765","resettablePL":"-1.8765","financing":"-0.0040","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"SGD_JPY","long":{"units":"0","pl":"-11.6988","resettablePL":"-11.6988","financing":"-0.0266","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.4127","resettablePL":"0.4127","financing":"-0.0052","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-11.2861","resettablePL":"-11.2861","financing":"-0.0318","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CHF_JPY","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0281","resettablePL":"0.0281","financing":"-0.0001","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.0281","resettablePL":"0.0281","financing":"-0.0001","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CHF_ZAR","long":{"units":"0","pl":"-0.0021","resettablePL":"-0.0021","financing":"-0.0002","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0021","resettablePL":"-0.0021","financing":"-0.0002","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_USD","long":{"units":"0","pl":"-33.4490","resettablePL":"-33.4490","financing":"-0.5699","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-35.2630","resettablePL":"-35.2630","financing":"-0.7206","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-68.7120","resettablePL":"-68.7120","financing":"-1.2905","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_CAD","long":{"units":"0","pl":"-7.7127","resettablePL":"-7.7127","financing":"-0.0620","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"3.8895","resettablePL":"3.8895","financing":"-0.1901","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-3.8232","resettablePL":"-3.8232","financing":"-0.2521","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_CHF","long":{"units":"0","pl":"-5.9925","resettablePL":"-5.9925","financing":"0.0123","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.4028","resettablePL":"-0.4028","financing":"-0.0003","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-6.3953","resettablePL":"-6.3953","financing":"0.0120","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_JPY","long":{"units":"0","pl":"-2.0866","resettablePL":"-2.0866","financing":"0.0016","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.3974","resettablePL":"0.3974","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-1.6892","resettablePL":"-1.6892","financing":"0.0016","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_HKD","long":{"units":"0","pl":"0.0232","resettablePL":"0.0232","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0515","resettablePL":"-0.0515","financing":"-0.0001","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0283","resettablePL":"-0.0283","financing":"-0.0001","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"TRY_JPY","long":{"units":"0","pl":"-1.1650","resettablePL":"-1.1650","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-1.1650","resettablePL":"-1.1650","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"}],"trades":[{"id":"143152","instrument":"EUR_USD","price":"1.08814","openTime":"2023-03-31T13:18:05.967088687Z","initialUnits":"-2300","initialMarginRequired":"50.0581","state":"OPEN","currentUnits":"-2300","realizedPL":"0.0000","financing":"0.0619","dividendAdjustment":"0.0000","clientExtensions":{"id":"140953421","tag":"0"},"unrealizedPL":"8.7400","marginUsed":"49.8741"}],"unrealizedPL":"8.7400","NAV":"60.2013","marginUsed":"49.8741","marginAvailable":"10.3272","positionValue":"2493.7060","marginCloseoutUnrealizedPL":"9.0160","marginCloseoutNAV":"60.4773","marginCloseoutMarginUsed":"49.8741","marginCloseoutPositionValue":"2493.7060","marginCloseoutPercent":"0.41234","withdrawalLimit":"10.3272","marginCallMarginUsed":"49.8741","marginCallPercent":"0.82467"},"lastTransactionID":"143166"}account=
        guaranteedStopLossOrderMode = account.getString("guaranteedStopLossOrderMode");
        hedgingEnabled = account.getBoolean("hedgingEnabled");
        id = asset = account.getString("id");
        createdTime = account.getString("createdTime");
        currency = account.getString("currency");
        createdByUserID = (int) account.getLong("createdByUserID");
        alias = account.getString("alias");
        marginRate = account.getDouble("marginRate");
        lastTransactionID = String.valueOf(account.getInt("lastTransactionID"));
        balance = account.getDouble("balance");
        openPositionCount = account.getInt("openPositionCount");
        pendingOrderCount = account.getInt("pendingOrderCount");
        pl = String.valueOf(account.getDouble("pl"));
        resettablePL = account.getDouble("resettablePL");
        resettablePLTime = account.getString("resettablePLTime");
        financing = Double.parseDouble(account.getString("financing"));
        commission = account.getDouble("commission");
        dividendAdjustment = String.valueOf(Double.parseDouble(account.getString("dividendAdjustment")));
        guaranteedExecutionFees = String.valueOf(Double.parseDouble(account.getString("guaranteedExecutionFees")));

    }

    public Account() {

    }

    public Account(@NotNull Exchange exchange) throws IOException, InterruptedException {
        this.exchange = exchange;

        new Message("Account", this);
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

    private double available;

    public void setAsset(String asText) {
        this.asset = asText;
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

    public void setExchange(Object exchange) {
        this.exchange = exchange;
    }

    //            string
//    Name for the account.
String   currency;
//            string
   String symbol;// for the account.
Object    available_balance;
//            object
//    required
Object          value;
//    string
//    Amount of currency that this object represents.
//    currency
//            string
//    Denomination of the currency.
//    default
//    boolean
//    Whether or not this account is the user's primary account
boolean   active;
//    boolean
//    Whether or not this account is active and okay to use.
Date           created_at;
//   date_time;
//    Time at which this account was created.
Date updated_at;
//    date-time
//    Time at which this account was updated.
Date            deleted_at;
//    date-time
//    Time at which this account was deleted.
String            type;
//            string
  Object Possible_values;//: //[ACCOUNT_TYPE_UNSPECIFIED, ACCOUNT_TYPE_CRYPTO, ACCOUNT_TYPE_FIAT, ACCOUNT_TYPE_VAULT]
  boolean  ready;
//    boolean
//    Whether or not this account is ready to trade.
Object           hold;
//            object
//    required

//    string
//    Amount of currency that this object represents.
//            string
//    Denomination of the currency.
    @JsonProperty("createdByUserID")
    public int createdByUserID;
    @JsonProperty("NAV")
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

    public double commission;
    public double marginCloseoutPercent;
    @Id
    public String id;
    public int openTradeCount;
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
    public Object exchange;


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

    public void setInstrument(String instrument) {
        this.instrument = instrument;
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

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @NotNull
    @Override
    public Iterator<Account> iterator() {
        return null;
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return null;
    }

    @Override
    public boolean add(Account account) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Account> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends Account> c) {
        return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public Account get(int index) {
        return null;
    }

    @Override
    public Account set(int index, Account element) {
        return null;
    }

    @Override
    public void add(int index, Account element) {

    }

    @Override
    public Account remove(int index) {
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return 0;
    }

    @NotNull
    @Override
    public ListIterator<Account> listIterator() {
        return null;
    }

    @NotNull
    @Override
    public ListIterator<Account> listIterator(int index) {
        return null;
    }

    @NotNull
    @Override
    public List<Account> subList(int fromIndex, int toIndex) {
        return null;
    }
}
