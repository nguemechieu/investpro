package org.investpro;

// Class to hold symbol (trading pair) information
public record SymbolInfo(String symbol, String baseAsset, String quoteAsset, String status) {

    @Override
    public String toString() {
        return STR."SymbolInfo{symbol='\{symbol}', baseAsset='\{baseAsset}', quoteAsset='\{quoteAsset}', status='\{status}'}";
    }
}