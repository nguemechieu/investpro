package org.investpro;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "coin_info") // Defines the table name in the database
public class CoinInfo {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "image")
    private String image;

    @Column(name = "current_price")
    private double current_price;

    @Column(name = "market_cap")
    private long market_cap;

    @Column(name = "market_cap_rank")
    private int market_cap_rank;

    @Column(name = "fully_diluted_valuation")
    private long fully_diluted_valuation;

    @Column(name = "total_volume")
    private long total_volume;

    @Column(name = "high_24h")
    private double high_24h;

    @Column(name = "low_24h")
    private double low_24h;

    @Column(name = "price_change_24h")
    private double price_change_24h;

    @Column(name = "price_change_percentage_24h")
    private double price_change_percentage_24h;

    @Column(name = "market_cap_change_24h")
    private long market_cap_change_24h;

    @Column(name = "market_cap_change_percentage_24h")
    private double market_cap_change_percentage_24h;

    @Column(name = "circulating_supply")
    private long circulating_supply;

    @Column(name = "total_supply")
    private long total_supply;

    @Column(name = "max_supply")
    private long max_supply;

    @Column(name = "ath") // All-Time High
    private double ath;

    @Column(name = "ath_change_percentage")
    private double ath_change_percentage;

    @Column(name = "ath_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date ath_date;

    @Column(name = "atl") // All-Time Low
    private double atl;

    @Column(name = "atl_change_percentage")
    private double atl_change_percentage;

    @Column(name = "atl_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date atl_date;

    @Column(name = "roi")
    private String roi; // ROI (can be null)

    @Column(name = "last_updated")
    @Temporal(TemporalType.TIMESTAMP)
    private Date last_updated;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public double getCurrent_price() {
        return current_price;
    }

    public void setCurrent_price(double current_price) {
        this.current_price = current_price;
    }

    public long getMarket_cap() {
        return market_cap;
    }

    public void setMarket_cap(long market_cap) {
        this.market_cap = market_cap;
    }

    public int getMarket_cap_rank() {
        return market_cap_rank;
    }

    public void setMarket_cap_rank(int market_cap_rank) {
        this.market_cap_rank = market_cap_rank;
    }

    public long getFully_diluted_valuation() {
        return fully_diluted_valuation;
    }

    public void setFully_diluted_valuation(long fully_diluted_valuation) {
        this.fully_diluted_valuation = fully_diluted_valuation;
    }

    public long getTotal_volume() {
        return total_volume;
    }

    public void setTotal_volume(long total_volume) {
        this.total_volume = total_volume;
    }

    public double getHigh_24h() {
        return high_24h;
    }

    public void setHigh_24h(double high_24h) {
        this.high_24h = high_24h;
    }

    public double getLow_24h() {
        return low_24h;
    }

    public void setLow_24h(double low_24h) {
        this.low_24h = low_24h;
    }

    public double getPrice_change_24h() {
        return price_change_24h;
    }

    public void setPrice_change_24h(double price_change_24h) {
        this.price_change_24h = price_change_24h;
    }

    public double getPrice_change_percentage_24h() {
        return price_change_percentage_24h;
    }

    public void setPrice_change_percentage_24h(double price_change_percentage_24h) {
        this.price_change_percentage_24h = price_change_percentage_24h;
    }

    public long getMarket_cap_change_24h() {
        return market_cap_change_24h;
    }

    public void setMarket_cap_change_24h(long market_cap_change_24h) {
        this.market_cap_change_24h = market_cap_change_24h;
    }

    public double getMarket_cap_change_percentage_24h() {
        return market_cap_change_percentage_24h;
    }

    public void setMarket_cap_change_percentage_24h(double market_cap_change_percentage_24h) {
        this.market_cap_change_percentage_24h = market_cap_change_percentage_24h;
    }

    public long getCirculating_supply() {
        return circulating_supply;
    }

    public void setCirculating_supply(long circulating_supply) {
        this.circulating_supply = circulating_supply;
    }

    public long getTotal_supply() {
        return total_supply;
    }

    public void setTotal_supply(long total_supply) {
        this.total_supply = total_supply;
    }

    public long getMax_supply() {
        return max_supply;
    }

    public void setMax_supply(long max_supply) {
        this.max_supply = max_supply;
    }

    public double getAth() {
        return ath;
    }

    public void setAth(double ath) {
        this.ath = ath;
    }

    public double getAth_change_percentage() {
        return ath_change_percentage;
    }

    public void setAth_change_percentage(double ath_change_percentage) {
        this.ath_change_percentage = ath_change_percentage;
    }

    public Date getAth_date() {
        return ath_date;
    }

    public void setAth_date(Date ath_date) {
        this.ath_date = ath_date;
    }

    public double getAtl() {
        return atl;
    }

    public void setAtl(double atl) {
        this.atl = atl;
    }

    public double getAtl_change_percentage() {
        return atl_change_percentage;
    }

    public void setAtl_change_percentage(double atl_change_percentage) {
        this.atl_change_percentage = atl_change_percentage;
    }

    public Date getAtl_date() {
        return atl_date;
    }

    public void setAtl_date(Date atl_date) {
        this.atl_date = atl_date;
    }


    public Date getLast_updated() {
        return last_updated;
    }

    public void setLast_updated(Date last_updated) {
        this.last_updated = last_updated;
    }
}
