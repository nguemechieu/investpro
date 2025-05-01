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
            logger.error("Failed to fetch BinanceUS accounts: {}", response.body());
            throw new RuntimeException("BinanceUS account fetch failed");
        }

        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        Account account = new Account();

        account.setCanTrade(json.path("canTrade").asBoolean());
        account.setCanWithdraw(json.path("canWithdraw").asBoolean());
        account.setCanDeposit(json.path("canDeposit").asBoolean());
        account.setUpdateTime(json.path("updateTime").asLong());

        List<Account> accounts = new ArrayList<>();
        accounts.add(account);
        return accounts;
    }

    public List<Account> getAccountSummary() {
        // Binance US doesn't provide a separate summary endpoint
        try {
            return getAccounts();
        } catch (Exception e) {
            logger.error("Error in getAccountSummary: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Fee> getTradingFee() {
        return new ArrayList<>();
    }

    public List<Position> getPositions() {
        return new ArrayList<>();
    }
}
