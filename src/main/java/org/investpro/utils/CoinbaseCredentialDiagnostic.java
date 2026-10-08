package org.investpro.utils;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.coinbase.CoinbaseCredentialInput;
import org.investpro.exchange.coinbase.CoinbaseJwtSigner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Read-only Coinbase REST diagnostic. Never prints credentials, JWTs or response bodies. */
@Slf4j
public final class CoinbaseCredentialDiagnostic {
    private static final String BASE = "https://api.coinbase.com/api/v3/brokerage";
    private CoinbaseCredentialDiagnostic() {}

    public record EndpointResult(String step, int statusCode, boolean passed) {}

     static void main() {
        validateCredentials(setting("COINBASE_KEY_NAME"), setting("COINBASE_PRIVATE_KEY"));
    }

    private static String setting(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? System.getenv(name) : value;
    }

    public static void validateCredentials(String keyName, String privateKey) {
        diagnose(keyName, privateKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    public static @NonNull @Unmodifiable List<EndpointResult> diagnose(String keyName, String privateKey, HttpClient client) {
        CoinbaseJwtSigner signer = null;
        try {
            var normalized = CoinbaseCredentialInput.normalize(keyName, privateKey);
            signer = new CoinbaseJwtSigner(normalized.keyName(), normalized.privateKey());
            log.info("Coinbase credentials parsed; local JWT generation is not proof of REST authentication.");
        } catch (IllegalArgumentException invalidCredentials) {
            log.warn("Coinbase credential parsing failed. Use the matching CDP key name and a supported ECDSA/ES256 PEM or Ed25519 private key.");
        }
        List<EndpointResult> results = new ArrayList<>();
        results.add(test(client, null, BASE + "/market/products", "public products"));
        if (signer == null) {
            results.add(new EndpointResult("credential parsing", 0, false));
            return List.copyOf(results);
        }
        String[] paths = {"/accounts", "/products", "/products?product_type=SPOT&get_tradability_status=true"};
        String[] steps = {"accounts without query", "products without query", "products with query"};
        for (int i = 0; i < paths.length; i++) {
            EndpointResult result = test(client, signer, BASE + paths[i], steps[i]);
            results.add(result);
            if (!result.passed()) break;
        }
        return List.copyOf(results);
    }

    @Contract("_, _, _, _ -> new")
    private static @NonNull EndpointResult test(HttpClient client, CoinbaseJwtSigner signer, String url, String step) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                    .header("User-Agent", "InvestPro/1.0").GET();
            if (signer != null) builder.header("Authorization", signer.buildAuthorizationHeaderForUrl("GET", url));
            int status = client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            boolean passed = status >= 200 && status < 300;
            log.info("Coinbase REST diagnostic {}: {} (HTTP {})", step, passed ? "PASS" : "FAIL", status);
            if (status == 401 || status == 403) {
                log.warn("Check REST JWT URI, Authorization header, permissions, key activation/revocation, secret loading and derivative region/eligibility. WebSocket success does not prove REST JWT correctness.");
            }
            return new EndpointResult(step, status, passed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Coinbase REST diagnostic {}: interrupted", step);
            return new EndpointResult(step, 0, false);
        } catch (Exception failure) {
            log.warn("Coinbase REST diagnostic {}: request failed; sensitive details omitted", step);
            return new EndpointResult(step, 0, false);
        }
    }
}
