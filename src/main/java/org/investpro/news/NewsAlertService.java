package org.investpro.news;

import java.util.ArrayList;
import java.util.List;

public class NewsAlertService {

    private final List<NewsAlertRule> rules = new ArrayList<>();

    public NewsAlertService() {
        rules.add(new NewsAlertRule(
                "critical-negative-default",
                true,
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(NewsUrgency.CRITICAL),
                java.util.Set.of(NewsImpact.NEGATIVE, NewsImpact.VERY_NEGATIVE),
                false,
                true,
                true));
    }

    public void addRule(NewsAlertRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    public List<NewsAlertRule> matchingRules(CryptoNewsItem item) {
        if (item == null) {
            return List.of();
        }
        return rules.stream()
                .filter(NewsAlertRule::enabled)
                .filter(rule -> matches(rule, item))
                .toList();
    }

    public boolean shouldBlockTrading(CryptoNewsItem item) {
        return matchingRules(item).stream().anyMatch(NewsAlertRule::blockTradingOptional);
    }

    private boolean matches(NewsAlertRule rule, CryptoNewsItem item) {
        boolean symbolMatch = rule.symbols().isEmpty()
                || item.mentionedSymbols().stream().anyMatch(symbol -> rule.symbols().contains(symbol));
        boolean categoryMatch = rule.categories().isEmpty() || rule.categories().contains(item.category());
        boolean urgencyMatch = rule.urgencies().isEmpty() || rule.urgencies().contains(item.urgency());
        boolean impactMatch = rule.impacts().isEmpty() || rule.impacts().contains(item.impact());
        return symbolMatch && categoryMatch && urgencyMatch && impactMatch;
    }
}
