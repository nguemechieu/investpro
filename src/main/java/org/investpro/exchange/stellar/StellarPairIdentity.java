package org.investpro.exchange.stellar;

import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.util.Locale;

public record StellarPairIdentity(
        StellarAssetIdentity base,
        StellarAssetIdentity quote
) {
    public StellarPairIdentity {
        if (base == null || quote == null) {
            throw new IllegalArgumentException("Stellar pair assets must not be null");
        }
        if (base.canonicalKey().equals(quote.canonicalKey())) {
            throw new IllegalArgumentException("Stellar pair base and quote must differ");
        }
    }

    @Contract(" -> new")
    public @NonNull TradePair toTradePair() {
        try {
            return new TradePair(base.code(), quote.code());
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to create TradePair for " + displaySymbol(), exception);
        }
    }

    public StellarPairIdentity inverted() {
        return new StellarPairIdentity(quote, base);
    }

    public String displaySymbol() {
        return base.code().toUpperCase(Locale.ROOT) + "/" + quote.code().toUpperCase(Locale.ROOT);
    }

    public String canonicalKey() {
        return base.canonicalKey() + "/" + quote.canonicalKey();
    }
}
