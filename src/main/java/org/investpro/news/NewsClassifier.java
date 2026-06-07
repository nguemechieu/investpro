package org.investpro.news;

import java.util.Locale;

public class NewsClassifier {

    public NewsCategory classify(CryptoNewsItem item) {
        String text = text(item);
        if (contains(text, "delisting", "delist", "suspend trading")) return NewsCategory.DELISTING;
        if (contains(text, "hack", "exploit", "breach", "stolen funds")) return NewsCategory.HACK;
        if (contains(text, "security", "vulnerability", "phishing")) return NewsCategory.SECURITY;
        if (contains(text, "lawsuit", "sues", "sued", "court", "charges")) return NewsCategory.LAWSUIT;
        if (contains(text, "sec", "cftc", "regulator", "regulation", "enforcement", "probe")) return NewsCategory.REGULATION;
        if (contains(text, "spot etf", "etf", "approval", "approved")) return NewsCategory.ETF;
        if (contains(text, "stablecoin", "usdc", "usdt", "depeg", "tether")) return NewsCategory.STABLECOIN;
        if (contains(text, "perpetual", "perps")) return NewsCategory.PERPETUALS;
        if (contains(text, "futures", "derivatives", "cde")) return NewsCategory.FUTURES;
        if (contains(text, "listing", "listed", "adds support", "launches trading")) return NewsCategory.LISTING;
        if (contains(text, "airdrop")) return NewsCategory.AIRDROP;
        if (contains(text, "partnership", "partners with")) return NewsCategory.PARTNERSHIP;
        if (contains(text, "funding", "raises", "investment", "venture")) return NewsCategory.FUNDING;
        if (contains(text, "defi", "dex", "yield", "lending protocol")) return NewsCategory.DEFI;
        if (contains(text, "fed", "federal reserve", "inflation", "cpi", "macro")) return NewsCategory.MACRO;
        if (contains(text, "upgrade", "mainnet", "hard fork", "roadmap")) return NewsCategory.PROJECT_UPDATE;
        if (contains(text, "exchange", "coinbase", "binance", "kraken")) return NewsCategory.EXCHANGE;
        if (contains(text, "bitcoin", "ethereum", "crypto", "market")) return NewsCategory.MARKET;
        return NewsCategory.UNKNOWN;
    }

    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String text(CryptoNewsItem item) {
        return ((item == null ? "" : item.title()) + " " + (item == null ? "" : item.summary())).toLowerCase(Locale.ROOT);
    }
}
