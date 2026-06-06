package org.investpro.trading.tradability.session;

import java.util.Locale;

public record ProviderSessionContext(
        String providerId,
        String providerName,
        boolean connected,
        boolean canSubmitOrders
) {
    public ProviderSessionContext {
        providerId = providerId == null ? "" : providerId.trim();
        providerName = providerName == null ? "" : providerName.trim();
    }

    public boolean providerContains(String text) {
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return providerId.toLowerCase(Locale.ROOT).contains(needle)
                || providerName.toLowerCase(Locale.ROOT).contains(needle);
    }
}
