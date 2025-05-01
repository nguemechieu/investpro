package org.investpro.investpro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "coin_info")
@Access(AccessType.FIELD)
public class CoinInfo implements Serializable {

    private static final long serialVersionUID = 1L;

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

    @Column(name = "ath")
    private double ath;

    @Column(name = "ath_change_percentage")
    private double ath_change_percentage;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ath_date")
    private Date ath_date;

    @Column(name = "atl")
    private double atl;

    @Column(name = "atl_change_percentage")
    private double atl_change_percentage;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "atl_date")
    private Date atl_date;
    @Convert(converter = StringArrayConverter.class)
    @Column(name = "roi")
    private String[] roi;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_updated")
    private Date last_updated;

    @Column(name = "fractional_digits")
    private int fractional_digits;

    @Override
    public String toString() {
        return "CoinInfo{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", image='" + image + '\'' +
                ", current_price=" + current_price +
                ", market_cap=" + market_cap +
                ", market_cap_rank=" + market_cap_rank +
                ", fully_diluted_valuation=" + fully_diluted_valuation +
                ", total_volume=" + total_volume +
                ", high_24h=" + high_24h +
                ", low_24h=" + low_24h +
                ", price_change_24h=" + price_change_24h +
                ", price_change_percentage_24h=" + price_change_percentage_24h +
                ", market_cap_change_24h=" + market_cap_change_24h +
                ", market_cap_change_percentage_24h=" + market_cap_change_percentage_24h +
                ", circulating_supply=" + circulating_supply +
                ", total_supply=" + total_supply +
                ", max_supply=" + max_supply +
                ", ath=" + ath +
                ", ath_change_percentage=" + ath_change_percentage +
                ", ath_date=" + ath_date +
                ", atl=" + atl +
                ", atl_change_percentage=" + atl_change_percentage +
                ", atl_date=" + atl_date +
                ", roi='" + roi + '\'' +
                ", last_updated=" + last_updated +
                ", fractional_digits=" + fractional_digits +
                '}';
    }



}
