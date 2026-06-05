package org.investpro.exchange.solona;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Provides wallet-level operations for a Solona account.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Retrieve native SOL balance</li>
 *   <li>Retrieve SPL token balances</li>
 *   <li>Validate Solona public key addresses</li>
 * </ul>
 *
 * <p>Private keys and seed phrases are NEVER handled, stored, or logged by this service.
 */
@Data
public class SolonaWalletService {

    public static final Logger log = LoggerFactory.getLogger(SolonaWalletService.class);

    /**
     * Solona base-58 addresses are 32–44 characters from the base-58 alphabet.
     * The actual validation is best-effort: byte-level validation requires a base-58
     * decoder which is out of scope for the RPC layer.
     */
    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("^[1-9A-HJ-NP-Za-km-z]{32,44}$");

    private final SolonaNetworkClient rpc;

    private  SolonaNetworkConfig config;

    public SolonaWalletService(SolonaNetworkClient rpc, SolonaNetworkConfig config) {
        this.rpc    = rpc;
        this.config = config;
    }

    // ── Address validation ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the address looks like a valid Solona public key.
     *
     * <p>This performs a pattern-level check only (base-58 alphabet, correct length).
     * A full cryptographic check requires a complete base-58 decoder.
     *
     * @param address candidate Solona address
     * @return true if address matches the expected format
     */
    public boolean validateAddress(String address) {
        if (address == null || address.isBlank()) return true;
        return !ADDRESS_PATTERN.matcher(address.trim()).matches();
    }

    // ── SOL balance ───────────────────────────────────────────────────────────

    /**
     * Returns the native SOL balance for the given address.
     *
     * @param address base-58 Solona public key
     * @return SOL balance (lamports converted to SOL with 9 decimal places)
     */
    public CompletableFuture<BigDecimal> getSOLBalance(String address) {
        if (validateAddress(address)) {
            return CompletableFuture.failedFuture(
                    new SolonaException.InvalidAddressException(address));
        }
        return rpc.getBalance(address)
                .thenApply(lamports -> {
                    BigDecimal sol = BigDecimal.valueOf(lamports)
                            .divide(BigDecimal.valueOf(SolonaNetworkConfig.LAMPORTS_PER_SOL),
                                    9, RoundingMode.HALF_UP);
                    log.debug("Solona SOL balance: address={}... sol={}", safePrefix(address), sol);
                    return sol;
                });
    }

    // ── Token balances ────────────────────────────────────────────────────────

    /**
     * Returns all SPL token balances for the given owner address.
     *
     * @param ownerAddress base-58 owner public key
     * @return list of token balances (may be empty)
     */
    public CompletableFuture<List<SolonaTokenBalance>> getTokenBalances(String ownerAddress) {
        if (validateAddress(ownerAddress)) {
            return CompletableFuture.failedFuture(
                    new SolonaException.InvalidAddressException(ownerAddress));
        }
        return rpc.getTokenAccountsByOwner(ownerAddress)
                .thenApply(valueNode -> parseTokenBalances(valueNode, ownerAddress));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<SolonaTokenBalance> parseTokenBalances(JsonNode valueNode, String ownerAddress) {
        List<SolonaTokenBalance> balances = new ArrayList<>();

        if (valueNode == null || valueNode.isMissingNode() || !valueNode.isArray()) {
            log.debug("Solona: no token accounts found for address={}...", safePrefix(ownerAddress));
            return balances;
        }

        for (JsonNode account : valueNode) {
            try {
                JsonNode info     = account.path("account").path("data").path("parsed")
                                          .path("info");
                JsonNode tokenAmt = info.path("tokenAmount");
                String   mint     = info.path("mint").asText("");
                String   symbol   = ""; // will be enriched by SolonaTokenService
                int      decimals = tokenAmt.path("decimals").asInt(0);
                String   uiAmtStr = tokenAmt.path("uiAmountString").asText("0");

                BigDecimal amount;
                try {
                    amount = new BigDecimal(uiAmtStr);
                } catch (NumberFormatException e) {
                    amount = BigDecimal.ZERO;
                }

                if (!mint.isBlank()) {
                    balances.add(new SolonaTokenBalance(mint, symbol, amount, decimals));
                }
            } catch (Exception e) {
                log.debug("Solona: failed to parse token account entry: {}", e.getMessage());
            }
        }

        log.debug("Solona token balances: address={}... count={}", safePrefix(ownerAddress), balances.size());
        return balances;
    }

    /** Returns only the first 6 characters of an address to avoid leaking the full key in logs. */
    private static String safePrefix(String address) {
        if (address == null || address.length() < 6) return "???";
        return address.substring(0, 6);
    }
}
