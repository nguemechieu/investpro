package org.investpro.investpro;


public  enum PRICE_MODE {
        PRICE_CLOSE(0),
        PRICE_LOW(1),
        PRICE_MEDIUM(2),
        PRICE_HIGH(3),
        PRICE_OPEN(4);

        private final int value;



    PRICE_MODE(int i) {
        value = i;
    }

    public int getValue() {
        return value;
    }
}
