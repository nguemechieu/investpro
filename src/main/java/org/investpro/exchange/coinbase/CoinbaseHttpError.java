package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;

/** Safe diagnostics: classify broker errors without exposing arbitrary response bodies. */
final class CoinbaseHttpError {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CODES = Set.of("PERMISSION_DENIED", "UNAUTHENTICATED",
            "INVALID_ARGUMENT", "NOT_FOUND", "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION");

    private CoinbaseHttpError() {}

    static String message(int status, String path, String body) {
        String code = "";
        String detail = "";
        try {
            // Bound parsing of an untrusted error response; never include it in the message.
            if (body != null && body.length() <= 65536) {
                JsonNode error = JSON.readTree(body);
                if (error != null) {
                    String candidate = error.path("error").asText("").toUpperCase(Locale.ROOT);
                    if (CODES.contains(candidate)) code = " (" + candidate + ")";
                    detail = (error.path("message").asText("") + " "
                            + error.path("error_details").asText("")).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) { /* Non-JSON errors still receive actionable status guidance. */ }
        String reason;
        if (status == 403) {
            if (detail.contains("ip") && (detail.contains("allowlist") || detail.contains("whitelist")
                    || detail.contains("ip address"))) {
                reason = "Coinbase denied access from this IP address. Check the API key's IP allowlist.";
            } else if (detail.contains("perpetual") || detail.contains("futures")) {
                reason = "Coinbase denied derivatives access. Check this account's futures/perpetual trading eligibility, "
                        + "API key Trade permission and derivatives portfolio access.";
            } else if (path.startsWith("/api/v3/brokerage/orders")) {
                reason = "Coinbase denied order access. Check API key Trade permission, the key's selected portfolio "
                        + "and account/product trading restrictions. Market-data access does not authorize orders.";
            } else {
                reason = "Coinbase denied access. Check this API key's endpoint permissions, portfolio access "
                        + "and account/product eligibility.";
            }
        } else if (status == 401) {
            reason = "Coinbase rejected authentication. Check the key pair, JWT signing, system clock and IP restrictions.";
        } else {
            reason = "Coinbase rejected the request; review order parameters and account status.";
        }
        return "Coinbase HTTP " + status + " for " + path + code + ": " + reason;
    }
}
