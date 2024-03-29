package org.investpro;


public class Instrument {
    public int pipLocation;
    public String displayName;
    public String maximumPositionSize;
    public String type;

    public String minimumTrailingStopDistance;
    public String marginRate;
    public String minimumTradeSize;
    public int displayPrecision;
    public String guaranteedStopLossOrderMode;
    public String name;
    public int tradeUnitsPrecision;

    public String maximumTrailingStopDistance;
    public String maximumOrderUnits;
    String tags;

    public Instrument() {
        pipLocation = 0;
        displayName = "";
        maximumPositionSize = "";
        type = "";
        minimumTrailingStopDistance = "";
        marginRate = "";
        minimumTradeSize = "";
        displayPrecision = 0;
        guaranteedStopLossOrderMode = "";
        name = "";
        tradeUnitsPrecision = 0;

        maximumTrailingStopDistance = "";
        maximumOrderUnits = "";
        tags = "";
    }

    public Instrument(String usdThb, String currency, String s, int i, int i1, int i2, int i3, int i4, int i5, double v, double v1, int i6, String s1, String t1) {
    }

    @Override
    public String toString() {

        return "Instrument " +
                "pipLocation=" + pipLocation +
                ", displayName='" + displayName + '\'' +
                ", maximumPositionSize='" + maximumPositionSize + '\'' +
                ", type='" + type + '\'' +
                ", tags=" + tags +
                ", minimumTrailingStopDistance='" + minimumTrailingStopDistance + '\'' +
                ", marginRate='" + marginRate + '\'' +
                ", minimumTradeSize='" + minimumTradeSize + '\'' +
                ", displayPrecision=" + displayPrecision +
                ", guaranteedStopLossOrderMode='" + guaranteedStopLossOrderMode + '\'' +
                ", name='" + name + '\'' +
                ", tradeUnitsPrecision=" + tradeUnitsPrecision +

                ", maximumTrailingStopDistance='" + maximumTrailingStopDistance + '\'' +
                ", maximumOrderUnits='" + maximumOrderUnits;
    }

    public int getPipLocation() {
        return pipLocation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMaximumPositionSize() {
        return maximumPositionSize;
    }

    public String getType() {
        return type;
    }


    public String getMinimumTrailingStopDistance() {
        return minimumTrailingStopDistance;
    }

    public String getMarginRate() {
        return marginRate;
    }

    public String getMinimumTradeSize() {
        return minimumTradeSize;
    }

    public int getDisplayPrecision() {
        return displayPrecision;
    }

    public String getGuaranteedStopLossOrderMode() {
        return guaranteedStopLossOrderMode;
    }

    public String getName() {
        return name;
    }

    public int getTradeUnitsPrecision() {
        return tradeUnitsPrecision;
    }


    public String getMaximumTrailingStopDistance() {
        return maximumTrailingStopDistance;
    }

    public String getMaximumOrderUnits() {
        return maximumOrderUnits;
    }
}