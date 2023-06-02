package org.investpro;

public class MYSHORT {
    public double dividendAdjustment;
    public double unrealizedPL;
    public double resettablePL;
    public int units;
    public double financing;
    public double guaranteedExecutionFees;
    public double pl;

    public double getDividendAdjustment() {
        return dividendAdjustment;
    }

    public void setDividendAdjustment(double dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public double getResettablePL() {
        return resettablePL;
    }

    public void setResettablePL(double resettablePL) {
        this.resettablePL = resettablePL;
    }

    @Override
    public String toString() {
        return "MYSHORT{" +
                "dividendAdjustment=" + dividendAdjustment +
                ", unrealizedPL=" + unrealizedPL +
                ", resettablePL=" + resettablePL +
                ", units=" + units +
                ", financing=" + financing +
                ", guaranteedExecutionFees=" + guaranteedExecutionFees +
                ", pl=" + pl +
                '}';
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public double getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public void setGuaranteedExecutionFees(double guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    public double getPl() {
        return pl;
    }

    public void setPl(double pl) {
        this.pl = pl;
    }
}
