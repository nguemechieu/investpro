package org.investpro.news;

public class NewsUrgencyService {

    public NewsUrgency urgency(NewsCategory category, NewsImpact impact) {
        if (category == NewsCategory.HACK
                || category == NewsCategory.DELISTING
                || (category == NewsCategory.SECURITY && negative(impact))) {
            return NewsUrgency.CRITICAL;
        }
        if (category == NewsCategory.LISTING
                || category == NewsCategory.ETF
                || category == NewsCategory.LAWSUIT
                || category == NewsCategory.REGULATION
                || category == NewsCategory.MACRO) {
            return NewsUrgency.HIGH;
        }
        if (category == NewsCategory.PROJECT_UPDATE
                || category == NewsCategory.PARTNERSHIP
                || category == NewsCategory.FUNDING
                || category == NewsCategory.FUTURES
                || category == NewsCategory.PERPETUALS) {
            return NewsUrgency.MEDIUM;
        }
        return NewsUrgency.LOW;
    }

    private boolean negative(NewsImpact impact) {
        return impact == NewsImpact.NEGATIVE || impact == NewsImpact.VERY_NEGATIVE;
    }
}
