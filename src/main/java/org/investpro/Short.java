package org.investpro;

public class Short {
    public String dividendAdjustment;
    public String unrealizedPL;
    public String resettablePL;
    public String guaranteedExecutionFees;
    public String financing;
    public String units;
    public String pl;

    public Short(String dividendAdjustment, String unrealizedPL, String resettablePL, String guaranteedExecutionFees, String financing, String units, String pl) {
        this.dividendAdjustment = dividendAdjustment;
        this.unrealizedPL = unrealizedPL;
        this.resettablePL = resettablePL;
        this.guaranteedExecutionFees = guaranteedExecutionFees;
        this.financing = financing;
        this.units = units;
        this.pl = pl;
    }

    public String getDividendAdjustment() {
        return dividendAdjustment;
    }

    public void setDividendAdjustment(String dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public String getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(String unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public String getResettablePL() {
        return resettablePL;
    }

    public void setResettablePL(String resettablePL) {
        this.resettablePL = resettablePL;
    }

    public String getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public void setGuaranteedExecutionFees(String guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    public String getFinancing() {
        return financing;
    }

    public void setFinancing(String financing) {
        this.financing = financing;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getPl() {
        return pl;
    }

    public void setPl(String pl) {
        this.pl = pl;
    }
}
