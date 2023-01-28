package org.investpro.investpro;



import java.util.ArrayList;
import java.util.Date;

public class OandaAccount {
    private final String name;
    private final String email;
    private final String password;
   long lastTransactionID;
    long createdByUserID;
    double NAV;
    double marginCloseoutUnrealizedPL;
    double marginCallMarginUsed;
    double openPositionCount;
    double withdrawalLimit;
    double positionValue;
    double marginRate;
    double marginCallPercent;
    double balance;
    double resettablePL ,financing;
    Date createdTime;
    String alias;
    String currency;
    double commission;
    double marginCloseoutPercent;
    long id;
    long openTradeCount;
    long pendingOrderCount;
    boolean hedgingEnabled;
   int resettablePLTime;
   ArrayList<Trade>trades;
    public OandaAccount(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;}


    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }
}
