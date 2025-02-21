package org.investpro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@Entity
@Table(name = "coin_info") // Defines the table name in the database
public class CoinInfo implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    String id;
    @Column(name = "symbol", nullable = false)
    String symbol;
    @Column(name = "name", nullable = false)
    String name;
    @Column(name = "image")
    String image;
    @Column(name = "current_price")
    double current_price;
    @Column(name = "market_cap")
    long market_cap;
    @Column(name = "market_cap_rank")
    int market_cap_rank;
    @Column(name = "fully_diluted_valuation")
    long fully_diluted_valuation;
    @Column(name = "total_volume")
    long total_volume;
    @Column(name = "high_24h")
    double high_24h;
    @Column(name = "low_24h")
    double low_24h;
    @Column(name = "price_change_24h")
    double price_change_24h;
    @Column(name = "price_change_percentage_24h")
    double price_change_percentage_24h;
    @Column(name = "market_cap_change_24h")
    long market_cap_change_24h;
    @Column(name = "market_cap_change_percentage_24h")
    double market_cap_change_percentage_24h;
    @Column(name = "circulating_supply")
    long circulating_supply;
    @Column(name = "total_supply")
    long total_supply;
    @Column(name = "max_supply")
    long max_supply;
    @Column(name = "ath") // All-Time High
    double ath;
    @Column(name = "ath_change_percentage")
    double ath_change_percentage;
    @Column(name = "ath_date")
    @Temporal(TemporalType.TIMESTAMP)
    Date ath_date;
    @Column(name = "atl") // All-Time Low
    double atl;
    @Column(name = "atl_change_percentage")
    double atl_change_percentage;
    @Column(name = "atl_date")
    @Temporal(TemporalType.TIMESTAMP)
    Date atl_date;
    @Column(name = "roi")
    String roi; // ROI (can be null)
    @Column(name = "last_updated")
    @Temporal(TemporalType.TIMESTAMP)
    Date last_updated;
    int fractional_digits;

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

    // Getters and Setters


    public void setCurrentPrice(Double currentPrice) {
        this.current_price = currentPrice;
    }

    public void setMarketCap(long marketCap) {
        this.market_cap = marketCap;
    }

    public void setMarketCapRank(int marketCapRank) {
        this.market_cap_rank = marketCapRank;
    }

    public void setFractionalDigits(int fractionalDigit) {
        this.fractional_digits = fractionalDigit;
    }

}
