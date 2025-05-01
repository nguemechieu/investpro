package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.Fee;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OandaAccountService {

    private static final Logger logger = LoggerFactory.getLogger(OandaAccountService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String accountId;
    private final String apiSecret;
    private final HttpClient client;
    private final HttpRequest.Builder baseRequestBuilder;

    public OandaAccountService(String accountId, String apiSecret, HttpClient client) {
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.client = client;
        this.baseRequestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    public List<Fee> getTradingFee() throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/accounts/%s/trades/fee", Oanda.API_URL, accountId));
        HttpRequest request = baseRequestBuilder.uri(uri).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch trading fees: " + response.body());
        }
        Fee fee = OBJECT_MAPPER.readValue(response.body(), Fee.class);
        return List.of(fee);
    }

    public List<Account> getAccounts() throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/accounts", Oanda.API_URL));
        HttpRequest request = baseRequestBuilder.uri(uri).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch accounts: " + response.body());
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        List<Account> accounts = new ArrayList<>();
        for (JsonNode node : root.get("accounts")) {
            accounts.add(OBJECT_MAPPER.treeToValue(node, Account.class));
        }
        return accounts;
    }

    public List<Account> getAccountSummary() {
        URI uri = URI.create(String.format("%s/accounts/%s/summary", Oanda.API_URL, accountId));
        try {
            HttpRequest request = baseRequestBuilder.uri(uri).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch account summary: " + response.body());
            }

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            JsonNode accountJson = json.get("account");
            Account account = OBJECT_MAPPER.treeToValue(accountJson, Account.class);
            return List.of(account);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch account summary", e);
        }
    }

    public List<Position> getPositions() throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/accounts/%s/positions", Oanda.API_URL, accountId));
        HttpRequest request = baseRequestBuilder.uri(uri).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch positions: " + response.body());
        }

        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        List<Position> positions = new ArrayList<>();
        for (JsonNode node : json.get("positions")) {
            positions.add(OBJECT_MAPPER.treeToValue(node, Position.class));
        }
        return positions;
    }
}
