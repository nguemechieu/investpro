package org.investpro;

public class Fill {

     double price;

    public Fill(int liquidity, double price) {
        this.price = price;
    }

    public double getPrice() {
        return price;
    }
}
