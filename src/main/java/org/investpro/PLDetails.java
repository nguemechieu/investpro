package org.investpro;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PLDetails {


    private Long id;

    private double pl;

    private double resettablePL;

    public PLDetails(Long id, double pl, double resettablePL, double units, double unrealizedPL) {
        this.id = id;
        this.pl = pl;
        this.resettablePL = resettablePL;
        this.units = units;
        this.unrealizedPL = unrealizedPL;
    }

    @Override
    public String toString() {
        return "PLDetails{id=%d, pl=%s, resettablePL=%s, units=%s, unrealizedPL=%s}".formatted(id, pl, resettablePL, units, unrealizedPL);
    }

    private double units;

    private double unrealizedPL;

    // Getters and setters...
}
