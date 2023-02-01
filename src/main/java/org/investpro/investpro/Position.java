package org.investpro.investpro;

public class Position{
    public String dividendAdjustment;
    public String unrealizedPL;
    public String resettablePL;
    public String guaranteedExecutionFees;

    public String getDividendAdjustment() {
        return dividendAdjustment;
    }

    public String getUnrealizedPL() {
        return unrealizedPL;
    }

    public String getResettablePL() {
        return resettablePL;
    }

    public String getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public String getFinancing() {
        return financing;
    }

    public Short getMyshort() {
        return myshort;
    }

    public String getInstrument() {
        return instrument;
    }

    public String getCommission() {
        return commission;
    }

    public String getPl() {
        return pl;
    }

    public Long getMylong() {
        return mylong;
    }

    public String financing;

    public Position() {
    }

    public void setDividendAdjustment(String dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public void setUnrealizedPL(String unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public void setResettablePL(String resettablePL) {
        this.resettablePL = resettablePL;
    }

    public void setGuaranteedExecutionFees(String guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    public void setFinancing(String financing) {
        this.financing = financing;
    }

    public void setMyshort(Short myshort) {
        this.myshort = myshort;
    }

    public Position setCommission(String commission) {
        this.commission = commission;
        return null;
    }

    public Position setPl(String pl) {
        this.pl = pl;
        return null;
    }

    public void setMylong(Long mylong) {
        this.mylong = mylong;
    }

    // @JsonProperty("short")
    public Short myshort = new Short("", "", "", "", "", "", "");
    public String instrument;
    public String commission;
    public String pl;
//    @JsonProperty("long")
    public Long mylong;

    public Position setInstrument(String instrument) {
        this.instrument = instrument;

        return null;
    }
}
