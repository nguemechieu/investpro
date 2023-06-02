package org.investpro;

import cryptoinvestor.cryptoinvestor.Account;
import org.json.JSONObject;

public class BinanceUsAccount extends Account {
    String balances;

    public BinanceUsAccount(JSONObject jsonObject) {
        super(jsonObject
        );
        if (jsonObject.has("balances")) {
            balances = jsonObject.getJSONObject("balances").toString();

        } else {
            balances = "0";
        }

    }

    public String getBalances() {
        return balances;
    }

    public void setBalances(String balances) {
        this.balances = balances;
    }


}
