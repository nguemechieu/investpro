package org.investpro.news;

public final class CryptoNewsIntelligence {

    private static final NewsAggregatorService INSTANCE = new NewsAggregatorService();

    private CryptoNewsIntelligence() {
    }

    public static NewsAggregatorService getInstance() {
        return INSTANCE;
    }

    public static NewsContextService contextService() {
        return INSTANCE.contextService();
    }
}
