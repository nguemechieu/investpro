package org.investpro.news;

import java.util.Set;

public record NewsAlertRule(
        String id,
        boolean enabled,
        Set<String> symbols,
        Set<NewsCategory> categories,
        Set<NewsUrgency> urgencies,
        Set<NewsImpact> impacts,
        boolean notifyDesktop,
        boolean showInTerminal,
        boolean blockTradingOptional) {

    public NewsAlertRule {
        symbols = symbols == null ? Set.of() : Set.copyOf(symbols);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        urgencies = urgencies == null ? Set.of() : Set.copyOf(urgencies);
        impacts = impacts == null ? Set.of() : Set.copyOf(impacts);
    }
}
