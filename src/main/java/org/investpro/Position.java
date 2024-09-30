package org.investpro;

import java.util.List;

public class Position {
    private String lastTransactionID;
    private List<PositionDetails> positions;


    String instrument;
    int units;
    double pl;

    public Position() {
    }

    public String getLongPosition() {
        return longPosition;
    }

    public void setLongPosition(String longPosition) {
        this.longPosition = longPosition;
    }

    public String getShortPosition() {
        return shortPosition;
    }

    public void setShortPosition(String shortPosition) {
        this.shortPosition = shortPosition;
    }

    @Override
    public String toString() {
        return "Position{lastTransactionID='%s', positions=%s, instrument='%s', units=%d, pl=%s, financing=%s, dividendAdjustment=%s, guaranteedExecutionFees=%s, longPosition='%s', shortPosition='%s'}".formatted(lastTransactionID, positions, instrument, units, pl, financing, dividendAdjustment, guaranteedExecutionFees, longPosition, shortPosition);
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public double getPl() {
        return pl;
    }

    public void setPl(double pl) {
        this.pl = pl;
    }

    public double getDividendAdjustment() {
        return dividendAdjustment;
    }

    public void setDividendAdjustment(double dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public double getGuaranteedExecutionFees() {
        return guaranteedExecutionFees;
    }

    public void setGuaranteedExecutionFees(double guaranteedExecutionFees) {
        this.guaranteedExecutionFees = guaranteedExecutionFees;
    }

    double financing;
    double dividendAdjustment;
    double guaranteedExecutionFees;

    // Getters and Setters
    public String getLastTransactionID() {
        return lastTransactionID;
    }

    public void setLastTransactionID(String lastTransactionID) {
        this.lastTransactionID = lastTransactionID;
    }

    String longPosition;
    String shortPosition;

    public List<PositionDetails> getPositions() {
        return positions;
    }

    public void setPositions(List<PositionDetails> positions) {
        this.positions = positions;
    }

    // Inner class to represent position details
    public static class PositionDetails {
        private String instrument;
        private PLDetails longPosition;
        private PLDetails shortPosition;
        private double pl;
        private double resettablePL;
        private double unrealizedPL;

        // Getters and Setters
        public String getInstrument() {
            return instrument;
        }

        public void setInstrument(String instrument) {
            this.instrument = instrument;
        }

        public PLDetails getLongPosition() {
            return longPosition;
        }

        public void setLongPosition(PLDetails longPosition) {
            this.longPosition = longPosition;
        }

        public PLDetails getShortPosition() {
            return shortPosition;
        }

        public void setShortPosition(PLDetails shortPosition) {
            this.shortPosition = shortPosition;
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

        public double getUnrealizedPL() {
            return unrealizedPL;
        }

        public void setUnrealizedPL(double unrealizedPL) {
            this.unrealizedPL = unrealizedPL;
        }
    }
}