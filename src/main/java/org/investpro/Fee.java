package org.investpro;

import java.util.List;

public class Fee {
    private String symbol;
    private double makerCommission;
    private double takerCommission;

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getMakerCommission() {
        return makerCommission;
    }

    public void setMakerCommission(double makerCommission) {
        this.makerCommission = makerCommission;
    }

    public double getTakerCommission() {
        return takerCommission;
    }

    public void setTakerCommission(double takerCommission) {
        this.takerCommission = takerCommission;
    }

    // toString method for better visualization
    @Override
    public String toString() {
        return "Fee{" +
                "symbol='" + symbol + '\'' +
                ", makerCommission=" + makerCommission +
                ", takerCommission=" + takerCommission +
                '}';
    }

    // Method to parse list of fees
    public static class FeeList {
        private List<Fee> fees;

        public List<Fee> getFees() {
            return fees;
        }

        public void setFees(List<Fee> fees) {
            this.fees = fees;
        }
    }
}
