package org.investpro.investpro;

import java.util.ArrayList;

public class Instrument {
    public int pipLocation;
    public String displayName;
    public String maximumPositionSize;
    public String type;
    public ArrayList<Tag> tags;
    public String minimumTrailingStopDistance;
    public String marginRate;
    public String minimumTradeSize;
    public int displayPrecision;
    public String guaranteedStopLossOrderMode;
    public String name;
    public int tradeUnitsPrecision;
    public Financing financing;
    public String maximumTrailingStopDistance;
    public String maximumOrderUnits;

    public Instrument() {
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
                ", financing=" + financing +
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

    public ArrayList<Tag> getTags() {
        return tags;
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

    public Financing getFinancing() {
        return financing;
    }

    public String getMaximumTrailingStopDistance() {
        return maximumTrailingStopDistance;
    }

    public String getMaximumOrderUnits() {
        return maximumOrderUnits;
    }
}
