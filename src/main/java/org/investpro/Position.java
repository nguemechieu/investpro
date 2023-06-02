package org.investpro;

public class Position {
    public String dividendAdjustment;
    public String unrealizedPL;
    public String resettablePL;
    public String guaranteedExecutionFees;
    public String financing;
    // @JsonProperty("short")
    public Short myshort = new Short("", "", "", "", "", "", "");
    public String instrument;
    public String commission;
    public String pl;
    //    @JsonProperty("long")
    public Long mylong;

    public Position() {
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

    public Short getMyshort() {
        return myshort;
    }

    public void setMyshort(Short myshort) {
        this.myshort = myshort;
    }

    public String getInstrument() {
        return instrument;
    }

    public Position setInstrument(String instrument) {
        this.instrument = instrument;

        return null;
    }

    public String getCommission() {
        return commission;
    }

    public Position setCommission(String commission) {
        this.commission = commission;
        return null;
    }

    public String getPl() {
        return pl;
    }

    public Position setPl(String pl) {
        this.pl = pl;
        return null;
    }

    public Long getMylong() {
        return mylong;
    }

    public void setMylong(Long mylong) {
        this.mylong = mylong;
    }
}
