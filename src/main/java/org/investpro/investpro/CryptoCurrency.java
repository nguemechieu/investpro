package org.investpro.investpro;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.Currency;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
public class CryptoCurrency extends Currency {
    private final URI homeUrl;
    private final URI walletUrl;
    /**
     * Time that the genesis block was created.
     */
    private final Instant genesisTime;
    /**
     * After how many blocks is difficulty recalculated.
     */
    private final int difficultyRetarget;
    private final String maxCoinsIssued;
    private Algorithm algorithm;

    protected CryptoCurrency() {
        algorithm = Algorithm.NULL;
        homeUrl = null;
        walletUrl = null;
        genesisTime = Instant.EPOCH;
        difficultyRetarget = -1;
        maxCoinsIssued = "";
    }

    protected CryptoCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
                             String symbol, Algorithm algorithm, String homeUrl, String walletUrl,
                             long genesisTimeInEpochSeconds, int difficultyRetarget, String maxCoinsIssued) {

        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(homeUrl, "homeUrl must not be null");
        Objects.requireNonNull(walletUrl, "walletUrl must not be null");


        super(CurrencyType.CRYPTO.name(), fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, code);

        this.algorithm = algorithm;
        this.homeUrl = URI.create(homeUrl);
        this.walletUrl = URI.create(walletUrl);
        this.genesisTime = Instant.ofEpochSecond(genesisTimeInEpochSeconds);
        this.difficultyRetarget = difficultyRetarget;
        this.maxCoinsIssued = maxCoinsIssued;
    }


}
