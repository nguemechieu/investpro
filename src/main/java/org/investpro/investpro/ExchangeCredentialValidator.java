package org.investpro.investpro;

import org.jetbrains.annotations.NotNull;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.services.CoinbaseCredentials;
import org.investpro.investpro.services.CoinbaseExchangeAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import static org.investpro.investpro.BinanceUtils.HmacSHA256;

public final class ExchangeCredentialValidator {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeCredentialValidator.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private ExchangeCredentialValidator() {
    }

    public static @NotNull ValidationResult validate(String exchangeName, String apiKey, String apiSecret) {
        return validate(exchangeName, apiKey, apiSecret, "");
    }

    public static @NotNull ValidationResult validate(String exchangeName, String apiKey, String apiSecret, String passphrase) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        Objects.requireNonNull(apiSecret, "apiSecret must not be null");
        Objects.requireNonNull(passphrase, "passphrase must not be null");

        try {
            return switch (exchangeName.trim().toUpperCase(Locale.ROOT)) {
                case "BINANCE US", "BINANCEUS" -> validateBinanceUs(apiKey.trim(), apiSecret.trim());
                case "OANDA" -> validateOanda(apiKey.trim(), apiSecret.trim());
                case "COINBASE" -> validateCoinbase(apiKey.trim(), apiSecret.trim(), passphrase.trim());
                default -> ValidationResult.connectivityError(
                        "InvestPro cannot verify credentials for " + exchangeName + " yet."
                );
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ValidationResult.connectivityError(
                    "Credential verification was interrupted. Please try again."
            );
        } catch (ConnectException e) {
            return ValidationResult.connectivityError(
                    "Unable to reach the exchange right now. Please check your internet connection and try again."
            );
        } catch (IOException e) {
            logger.warn("Credential verification request failed for {}", exchangeName, e);
            return ValidationResult.connectivityError(
                    "Unable to verify credentials right now. Please check your internet connection and try again."
            );
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            logger.warn("Unable to sign credential verification request for {}", exchangeName, e);
            if ("COINBASE".equalsIgnoreCase(exchangeName.trim())) {
                return ValidationResult.invalidCredentials(
                        "The Coinbase private key format is invalid. Use the Coinbase Advanced Trade key id or key name together with the EC private key PEM, or paste the full Coinbase key JSON."
                );
            }
            return ValidationResult.invalidCredentials(
                    "The provided secret key is not valid for " + exchangeName + "."
            );
        } catch (Exception e) {
            logger.error("Unexpected credential verification failure for {}", exchangeName, e);
            return ValidationResult.connectivityError(
                    "Unable to verify credentials right now. Please try again in a moment."
            );
        }
    }

    private static ValidationResult validateBinanceUs(String apiKey, String apiSecret)
            throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        long timestamp = System.currentTimeMillis();
        String query = "timestamp=" + timestamp + "&recvWindow=10000";
        String signature = HmacSHA256(apiSecret, query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.binance.us/api/v3/account?" + query + "&signature=" + signature))
                .timeout(REQUEST_TIMEOUT)
                .header("X-MBX-APIKEY", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return ValidationResult.valid();
        }

        String body = normalize(response.body());
        if (response.statusCode() == 401
                || response.statusCode() == 403
                || containsAny(body,
                "invalid api-key",
                "invalid api key",
                "\"code\":-2014",
                "\"code\":-2015",
                "\"code\":-1022",
                "signature for this request is not valid")) {
            return ValidationResult.invalidCredentials("Binance US rejected this API key or secret.");
        }

        logger.warn("Unexpected Binance US credential validation response. HTTP {}: {}", response.statusCode(), response.body());
        return ValidationResult.connectivityError(
                "Binance US could not verify credentials right now. Please try again in a moment."
        );
    }

    private static ValidationResult validateOanda(String accountId, String apiSecret)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api-fxtrade.oanda.com/v3/accounts/" + accountId + "/summary"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return ValidationResult.valid();
        }

        String body = normalize(response.body());
        if (response.statusCode() == 401
                || response.statusCode() == 403
                || response.statusCode() == 404
                || containsAny(body,
                "insufficient authorization",
                "unauthorized",
                "invalid account",
                "invalid accountid",
                "forbidden")) {
            return ValidationResult.invalidCredentials(
                    "OANDA rejected this account number or API key. Check both values and confirm you are using the right OANDA environment."
            );
        }

        logger.warn("Unexpected OANDA credential validation response. HTTP {}: {}", response.statusCode(), response.body());
        return ValidationResult.connectivityError(
                "OANDA could not verify credentials right now. Please try again in a moment."
        );
    }

    private static ValidationResult validateCoinbase(String apiKey, String apiSecret, String passphrase)
            throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        String validationError = CoinbaseCredentials.validationError(apiKey, apiSecret, passphrase);
        if (validationError != null) {
            return ValidationResult.invalidCredentials(validationError);
        }

        URI uri = URI.create(Coinbase.BROKERAGE_API_URL + "/accounts");
        CoinbaseExchangeAuth auth = new CoinbaseExchangeAuth(apiKey, apiSecret, passphrase);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT);
        auth.authorize(builder, "GET", uri, "");
        HttpRequest request = builder.GET().build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return ValidationResult.valid();
        }

        String body = normalize(response.body());
        if (containsAny(body, "malformed jwt", "invalid jwt", "jwt")) {
            return ValidationResult.invalidCredentials(
                    "Coinbase rejected the generated JWT. Confirm you are using a Coinbase Advanced Trade key id or key name together with the matching EC private key."
            );
        }

        if (containsAny(body, "invalid api key", "invalid signature", "unauthenticated", "authentication error")) {
            return ValidationResult.invalidCredentials(
                    "Coinbase rejected the API key or private key. Make sure you are using Coinbase Advanced Trade or CDP API credentials, not the retired Coinbase Exchange passphrase credentials."
            );
        }

        if (response.statusCode() == 401
                || response.statusCode() == 403
                || containsAny(body,
                "unauthorized",
                "forbidden")) {
            return ValidationResult.invalidCredentials(
                    "Coinbase rejected the key id or private key. Make sure they come from the same Coinbase Advanced Trade API key bundle."
            );
        }

        logger.warn("Unexpected Coinbase credential validation response. HTTP {}: {}", response.statusCode(), response.body());
        return ValidationResult.connectivityError(
                "Coinbase could not verify credentials right now. Please try again in a moment."
        );
    }

    private static @NotNull String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public record ValidationResult(Status status, String message) {

        public static ValidationResult valid() {
            return new ValidationResult(Status.VALID, "");
        }

        public static ValidationResult invalidCredentials(String message) {
            return new ValidationResult(Status.INVALID_CREDENTIALS, message);
        }

        public static ValidationResult connectivityError(String message) {
            return new ValidationResult(Status.CONNECTIVITY_ERROR, message);
        }

        public boolean isValid() {
            return status == Status.VALID;
        }
    }

    public enum Status {
        VALID,
        INVALID_CREDENTIALS,
        CONNECTIVITY_ERROR
    }
}
