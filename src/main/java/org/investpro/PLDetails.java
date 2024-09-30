package org.investpro;


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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getPl() {
        return pl;
    }

    public void setPl(double pl) {
        this.pl = pl;
    }

    public double getResettablePL() {
        return resettablePL;
    }

    public void setResettablePL(double resettablePL) {
        this.resettablePL = resettablePL;
    }

    public double getUnits() {
        return units;
    }

    public void setUnits(double units) {
        this.units = units;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }
// Getters and setters...
}
