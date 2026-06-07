package org.investpro.news;

import java.util.Locale;

public class NewsSentimentService {

    public double score(CryptoNewsItem item, NewsCategory category) {
        String text = ((item == null ? "" : item.title()) + " " + (item == null ? "" : item.summary())).toLowerCase(Locale.ROOT);
        double score = 0.0;
        if (category == NewsCategory.HACK || category == NewsCategory.DELISTING || category == NewsCategory.LAWSUIT) score -= 0.75;
        if (category == NewsCategory.SECURITY || category == NewsCategory.REGULATION) score -= 0.35;
        if (category == NewsCategory.LISTING || category == NewsCategory.PARTNERSHIP || category == NewsCategory.FUNDING) score += 0.45;
        if (category == NewsCategory.ETF && contains(text, "approved", "approval", "inflows")) score += 0.55;
        if (contains(text, "hack", "exploit", "breach", "delist", "lawsuit", "charges", "depeg", "outage", "crackdown")) score -= 0.45;
        if (contains(text, "approved", "listing", "partnership", "funding", "launch", "inflows", "adoption", "upgrade")) score += 0.35;
        return Math.max(-1.0, Math.min(1.0, score));
    }

    public NewsImpact impact(double score) {
        if (score <= -0.70) return NewsImpact.VERY_NEGATIVE;
        if (score < -0.15) return NewsImpact.NEGATIVE;
        if (score >= 0.70) return NewsImpact.VERY_POSITIVE;
        if (score > 0.15) return NewsImpact.POSITIVE;
        return NewsImpact.NEUTRAL;
    }

    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
