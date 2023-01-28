package org.investpro.investpro;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

public class Mid extends RecursiveTreeObject<Mid> {
    public String c;
    public String h;
    public String l;
    public String o;
     String time;
    int volume;
String symbol;

    public Mid(String symbol, String time, String o,String c, String h, String l, int volume) {
        this.symbol=symbol;

        this.c=c;

        this.h = h;
        this.l = l;
        this.time = time;
        this.o = o;
        this.volume = volume;
    }

    public Mid(String c, String h, String l, String o) {
        this.c = c;
        this.h = h;
        this.l = l;
        this.o = o;
    }

    @Override
    public String toString() {
        return "Mid{" +
                "time='" + time + '\'' +
                ", volume=" + volume +
                ", symbol='" + symbol + '\'' +
                ", c='" + c + '\'' +
                ", h='" + h + '\'' +
                ", l='" + l + '\'' +
                ", o='" + o + '\'' +
                '}';
    }

    public String getTime() {
        return time;
    }

    public int getVolume() {
        return volume;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getC() {
        return c;
    }

    public String getH() {
        return h;
    }

    public String getL() {
        return l;
    }

    public String getO() {
        return o;
    }
}
