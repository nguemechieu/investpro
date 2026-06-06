package org.investpro.exchange;

public record PairQuality(
        StellarPairIdentity pair,
        boolean tradeable,
        boolean inverted,
        double bestBid,
        double bestAsk,
        double spreadPercent,
        double bidDepth,
        double askDepth,
        String reason
) {}
