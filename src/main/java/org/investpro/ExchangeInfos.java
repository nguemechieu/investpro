package org.investpro;

import java.util.ArrayList;

public class ExchangeInfos {
    ArrayList<Object> rateLimits = new ArrayList<>();
    ArrayList<Object> exchangeFilters = new ArrayList<>();
    ArrayList<Object> symbols = new ArrayList<>();
    private String timezone;
    private float serverTime;


    // Getter Methods

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    // Setter Methods

    public float getServerTime() {
        return serverTime;
    }

    public void setServerTime(float serverTime) {
        this.serverTime = serverTime;
    }
}