package org.investpro.exchange;

public record StellarTradabilityDetails(
        String baseCode,
        String baseIssuer,
        String quoteCode,
        String quoteIssuer,
        boolean baseNative,
        boolean quoteNative,
        boolean baseResolved,
        boolean quoteResolved,
        boolean baseTrustlineRequired,
        boolean quoteTrustlineRequired,
        boolean baseTrustlineExists,
        boolean quoteTrustlineExists,
        boolean directOrderBookAvailable,
        boolean invertedOrderBookAvailable,
        boolean usingInvertedOrderBook,
        double bestBid,
        double bestAsk,
        double spreadPercent,
        double bidDepth,
        double askDepth,
        String liquidityReason
) {}
