package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class TradePair {
    public String symbol;
    public String pair;
    public String base;
    public String quote;
    public double price;
    public double amount;
    public double fee;
    public double profit;
    public double profitPercent;
    public double feePercent;


    @Contract(pure = true)
    public TradePair(@NotNull Currency base, @NotNull Currency quote){
        this.symbol = base.symbol +"_"+ quote.symbol;
        this.pair = base.symbol + quote.symbol;
        this.base = base.symbol;
        this.quote = quote.symbol;
        this.price = 0;
        this.amount = 0;
        this.fee = 0;
        this.profit = 0;
        this.profitPercent = 0;
        this.feePercent = 0;
    }

    @Contract(pure = true)
    private @NotNull String Separator() {
        if (this.base.equals(this.quote)) {
            return "";
        }


        else {
            return "_";
        }
    }
public String toString(){
        if (this.base.equals(this.quote)) {
            return this.symbol;
        }
        else {
            return this.base+ this.Separator() + this.quote;
        }
     }

}
