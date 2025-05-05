package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.exchanges.Coinbase;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class CoinbaseAccountService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseAccountService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String apiKey;
    private String apiSecret;
    private HttpClient httpClient;

    public CoinbaseAccountService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public List<Account> getAccounts() throws IOException, InterruptedException {
        String url = Coinbase.API_URL + "/accounts";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching accounts: " + response.body());
        }

        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        List<Account> accounts = new ArrayList<>();
        for (JsonNode node : body) {
            Account account = OBJECT_MAPPER.treeToValue(node, Account.class);
            accounts.add(account);
        }
        return accounts;
    }

    public List<Fee> getTradingFee() {
        // Coinbase API does not expose a direct endpoint for trading fees
        logger.warn("getTradingFee not implemented for Coinbase - returning empty list");
        return Collections.emptyList();
    }

    public List<Account> getAccountSummary() {
        // Coinbase does not support a separate account summary endpoint
        logger.warn("getAccountSummary not implemented for Coinbase - returning account list");
        try {
            return getAccounts();
        } catch (IOException | InterruptedException e) {
            logger.error("Error retrieving account summary", e);
            return List.of();
        }
    }

    public List<Position> getPositions() {
        // Coinbase does not expose position tracking via API
        logger.warn("getPositions not implemented for Coinbase - returning empty list");
        return Collections.emptyList();
    }
}
