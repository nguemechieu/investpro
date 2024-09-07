package org.investpro;

import jakarta.persistence.*;

@Entity
@Table(name = "pl_details")
public class PLDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double pl;

    private double resettablePL;

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
