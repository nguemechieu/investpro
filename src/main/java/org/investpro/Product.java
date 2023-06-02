package org.investpro;

import java.util.ArrayList;
import java.util.List;


public class Product {

    public String name;
    public String description;
    public String url;
    public String category;
    public String currency;
    private TradePair tradePair;
    private List<Fill> asksList = new ArrayList<>();
    private List<Fill> bidsList = new ArrayList<>();

    public Product(List<Fill> asksList, List<Fill> bidsList, TradePair tradePair) {
        this.asksList = asksList;
        this.bidsList = bidsList;
        this.tradePair = tradePair;
    }

    public Product(String name, String description, String url, String category, String currency) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.category = category;
        this.currency = currency;
    }

    public Product() {
        this.name = "";
        this.description = "";
        this.url = "";
        this.category = "";
        this.currency = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    public List<Fill> getBidsList() {
        return bidsList;
    }

    public List<Fill> getAsksList() {
        return asksList;
    }

    public double getLivePrice() {
        return getAsksList().get(0).getPrice();
    }
}
