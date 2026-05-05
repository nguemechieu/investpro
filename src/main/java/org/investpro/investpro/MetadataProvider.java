package org.investpro.investpro;

import org.investpro.investpro.models.TradePair;

import java.util.List;

public interface MetadataProvider {
    List<TradePair> getTradePairs() throws Exception;

    String getExchangeMessage();
}
