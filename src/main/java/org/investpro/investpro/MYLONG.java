package org.investpro.investpro;

public class MYLONG {
    public double dividendAdjustment;
    public double pl;
    public int units;
    public  double unrealizedPL,resettablePL,guaranteedExecutionFees,financing;

    public MYLONG() {
    }

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

    public double getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public void setGuaranteedExecutionFees(double guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }
}
