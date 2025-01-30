package org.investpro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class Position {

    // Getters and setters
    private String instrument;
    @JsonProperty("long")
    private SubPosition longPosition;
    @JsonProperty("short")
    private SubPosition shortPosition;

    private String commission;
    private String lastTransactionID;

    private int units;
    private double pl;
    private double resettablePL;
    private double financing;
    private double dividendAdjustment;
    private double guaranteedExecutionFees;
    private double unrealizedPL;

    public double getProfitOrLoss() {

        return pl + resettablePL + financing + dividendAdjustment;
    }

    public double getValue() {
        return pl + resettablePL + financing + dividendAdjustment;
    }

    // SubPosition class
    @Setter
    @Getter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubPosition {
        // Getters and setters
        private int units;
        private double pl;
        private double resettablePL;
        private double financing;
        private double dividendAdjustment;
        private double guaranteedExecutionFees;
        private double unrealizedPL;

    }
}
