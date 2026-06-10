package org.investpro.exchange;

import org.investpro.exchange.stellar.StellarPairIdentity;

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
