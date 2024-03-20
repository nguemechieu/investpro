package org.investpro;

import javafx.scene.paint.Color;

public class Exposure {


    String Asset;
    double volume;
    double rate;
    String graph;
    String Currency;
    private Color color;

    public Exposure(String asset, double volume, double rate, String graph, String currency, Color color) {
        Asset = asset;
        this.volume = volume;
        this.rate = rate;
        this.graph = graph;
        Currency = currency;
        this.color = color;
    }

    public String getAsset() {
        return Asset;
    }

    public void setAsset(String asset) {
        Asset = asset;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public String getGraph() {
        return graph;
    }

    public void setGraph(String graph) {
        this.graph = graph;
    }

    public String getCurrency() {
        return Currency;
    }

    public void setCurrency(String currency) {
        Currency = currency;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
