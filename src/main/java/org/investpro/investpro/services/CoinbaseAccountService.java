package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.models.Account;
import org.investpro.investpro.models.Fee;
import org.investpro.investpro.models.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class CoinbaseAccountService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseAccountService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicBoolean POSITIONS_WARNING_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean TRADING_FEE_WARNING_LOGGED = new AtomicBoolean(false);

    private String apiKey;
    private String apiSecret;
    private String passphrase;
    private HttpClient httpClient;
    private final CoinbaseExchangeAuth auth;

    public CoinbaseAccountService(String apiKey, String apiSecret, String passphrase, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.passphrase = passphrase;
        this.httpClient = httpClient;
        this.auth = new CoinbaseExchangeAuth(apiKey, apiSecret, passphrase);
    }

    public List<Account> getAccounts() throws IOException, InterruptedException {
        URI uri = URI.create(Coinbase.BROKERAGE_API_URL + "/accounts");
        HttpRequest request = signedRequest("GET", uri, "");

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.warn("Unable to fetch Coinbase accounts. HTTP {}: {}", response.statusCode(), response.body());
            return List.of();
        }

        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        JsonNode accountsNode = body.isArray() ? body : body.path("accounts");
        if (!accountsNode.isArray()) {
            return List.of();
        }

        List<Account> accounts = new ArrayList<>();
        for (JsonNode node : accountsNode) {
            Account account = new Account();
            String id = textOrEmpty(node, "uuid", "account_uuid", "id");
            String alias = textOrEmpty(node, "name", "display_name");
            String currency = textOrEmpty(node, "currency");
            if (currency.isBlank()) {
                currency = textOrEmpty(node.path("available_balance"), "currency");
            }
            if (currency.isBlank()) {
                currency = textOrEmpty(node.path("hold"), "currency");
            }

            double free = parseDouble(node.path("available_balance").path("value"));
            double locked = parseDouble(node.path("hold").path("value"));
            double balance = free + locked;

            account.setId(id);
            account.setAlias(alias.isBlank() ? id : alias);
            account.setCurrency(currency);
            account.setBalance(balance);
            account.setFree(free);
            account.setLocked(locked);
            account.setTotal(balance);
            account.setCanTrade(node.path("trading_enabled").asBoolean(
                    node.path("ready").asBoolean(node.path("active").asBoolean(true))
            ));
            account.setCanWithdraw(node.path("allow_withdrawals").asBoolean(
                    node.path("ready").asBoolean(node.path("active").asBoolean(true))
            ));
            account.setAccountType(textOrEmpty(node, "type", "platform"));
            if (!currency.isBlank()) {
                account.addBalance(currency, free, locked);
            }
            accounts.add(account);
        }
        return accounts;
    }

    private HttpRequest signedRequest(String method, URI uri, String body) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
            auth.authorize(builder, method, uri, body);
            return builder.method(method, body == null || body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build();
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Coinbase credentials are not in the expected Advanced Trade format.", e);
        }
    }

    public List<Fee> getTradingFee() {
        if (TRADING_FEE_WARNING_LOGGED.compareAndSet(false, true)) {
            logger.info("Coinbase trading fees are not exposed by this integration yet; returning an empty list.");
        }
        return Collections.emptyList();
    }

    public List<Account> getAccountSummary() {
        try {
            return getAccounts();
        } catch (IOException | InterruptedException | RuntimeException e) {
            logger.error("Error retrieving Coinbase account summary", e);
            return List.of();
        }
    }

    public List<Position> getPositions() {
        if (POSITIONS_WARNING_LOGGED.compareAndSet(false, true)) {
            logger.info("Coinbase positions are not exposed by this integration yet; returning an empty list.");
        }
        return Collections.emptyList();
    }

    private static String textOrEmpty(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static double parseDouble(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return 0.0;
        }
        String text = valueNode.asText("");
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
