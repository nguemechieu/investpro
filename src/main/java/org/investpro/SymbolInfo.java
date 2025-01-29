package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

// Class to hold symbol (trading pair) information
public record SymbolInfo(String symbol, String baseAsset, String quoteAsset, String status) {

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return "SymbolInfo{" +
                "symbol='" + symbol + '\'' +
                ", baseAsset='" + baseAsset + '\'' +
                ", quoteAsset='" + quoteAsset + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}