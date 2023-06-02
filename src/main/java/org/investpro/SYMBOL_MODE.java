package org.investpro;

public enum SYMBOL_MODE {
    SYMBOL_BID(0),
    SYMBOL_ASK(1);

    private final int i;

    SYMBOL_MODE(int i) {
        this.i = i;
    }


    public int getI() {
        return i;
    }
}