package org.investpro.investpro.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
public class Candle {

    @Id
    private Long id;

    private Instant time;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;

    public Candle() {
        // Default constructor for JPA
    }

    public Candle(Instant time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Candle(long id, double open, double high, double low, double close, long volume) {
        this.id = id;
        this.time = Instant.now(); // or another time source
        this.open = BigDecimal.valueOf(open);
        this.high = BigDecimal.valueOf(high);
        this.low = BigDecimal.valueOf(low);
        this.close = BigDecimal.valueOf(close);
        this.volume = BigDecimal.valueOf(volume);
    }
}
