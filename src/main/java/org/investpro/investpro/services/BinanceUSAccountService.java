package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.model.Fee;
import org.investpro.investpro.model.Position;
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
import java.util.List;

import static org.investpro.investpro.BinanceUtils.HmacSHA256;

public class BinanceUSAccountService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSAccountService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_URL = "https://api.binance.us";
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    public BinanceUSAccountService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public List<Account> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        JsonNode json = fetchAccountPayload();
        if (json == null) {
            return List.of();
        }

        JsonNode balancesNode = json.path("balances");
        if (!balancesNode.isArray()) {
            return List.of();
        }

        List<Account> accounts = new ArrayList<>();
        for (JsonNode balanceNode : balancesNode) {
            double free = balanceNode.path("free").asDouble(0.0);
            double locked = balanceNode.path("locked").asDouble(0.0);
            if (free <= 0 && locked <= 0) {
                continue;
            }

            Account account = new Account();
            account.setId("binance-us-spot");
            account.setAlias("Binance US Spot");
            account.setCurrency(balanceNode.path("asset").asText(""));
            account.setAsset(balanceNode.path("asset").asText(""));
            account.setBalance(free + locked);
            account.addBalance(balanceNode.path("asset").asText(""), free, locked);
            populateCommonFields(account, json);
            accounts.add(account);
        }

        return accounts;
    }

    public List<Account> getAccountSummary() {
        try {
            JsonNode json = fetchAccountPayload();
            if (json == null) {
                return List.of();
            }

            Account summary = new Account();
            summary.setId("binance-us-spot");
            summary.setAlias("Binance US Spot");
            summary.setCurrency("MULTI");
            summary.setBalance(sumBalances(json.path("balances")));
            populateCommonFields(summary, json);
            return List.of(summary);
        } catch (Exception e) {
            logger.error("Error in getAccountSummary", e);
            return List.of();
        }
    }

    public List<Fee> getTradingFee() {
        try {
            JsonNode json = fetchAccountPayload();
            if (json == null) {
                return List.of();
            }

            Fee fee = new Fee();
            fee.setSymbol("Binance US Spot");
            fee.setMakerCommission(json.path("makerCommission").asDouble(0.0));
            fee.setTakerCommission(json.path("takerCommission").asDouble(0.0));
            return List.of(fee);
        } catch (Exception e) {
            logger.warn("Unable to fetch Binance US trading fees", e);
            return List.of();
        }
    }

    public List<Position> getPositions() {
        return new ArrayList<>();
    }

    private JsonNode fetchAccountPayload() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        long timestamp = System.currentTimeMillis();
        String query = String.format("timestamp=%d&recvWindow=10000", timestamp);
        String signature = HmacSHA256(apiSecret, query);
        String url = String.format("%s/api/v3/account?%s&signature=%s", API_URL, query, signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MBX-APIKEY", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.warn("Failed to fetch Binance US account payload. HTTP {}: {}", response.statusCode(), response.body());
            return null;
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private void populateCommonFields(Account account, JsonNode json) {
        account.setCanTrade(json.path("canTrade").asBoolean());
        account.setCanWithdraw(json.path("canWithdraw").asBoolean());
        account.setCanDeposit(json.path("canDeposit").asBoolean());
        account.setUpdateTime(json.path("updateTime").asLong());
        account.setMakerCommission(json.path("makerCommission").asDouble(0.0));
        account.setTakerCommission(json.path("takerCommission").asDouble(0.0));
    }

    private double sumBalances(JsonNode balancesNode) {
        if (balancesNode == null || !balancesNode.isArray()) {
            return 0.0;
        }

        double total = 0.0;
        for (JsonNode balanceNode : balancesNode) {
            total += balanceNode.path("free").asDouble(0.0);
            total += balanceNode.path("locked").asDouble(0.0);
        }
        return total;
    }
}
