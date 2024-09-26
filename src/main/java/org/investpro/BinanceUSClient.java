package org.investpro;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

public class BinanceUSClient {

    private static final String API_URL = "https://api.binance.us";
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static void main(String[] args) throws Exception {
        String apiKey = "<your_api_key>";  // Set your API key here
        String secretKey = "<your_secret_key>";  // Set your Secret key here

        String uriPath = "/api/v3/account";
        Map<String, String> data = new HashMap<>();
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        String accountResult = binanceUsRequest(uriPath, data, apiKey, secretKey);
        System.out.printf("GET %s: %s%n", uriPath, accountResult);
    }

    // Get Binance US signature
    private static @NotNull String getBinanceUsSignature(@NotNull Map<String, String> data, @NotNull String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {

        String postData = data.entrySet().stream()
                .map(entry -> "%s=%s".formatted(entry.getKey(), urlEncode(entry.getValue())))
                .collect(Collectors.joining("&"));

        Mac hmacSHA256 = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        hmacSHA256.init(secretKeySpec);

        byte[] hmacData = hmacSHA256.doFinal(postData.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacData);
    }

    // Make GET request with signature
    private static String binanceUsRequest(String uriPath, Map<String, String> data, String apiKey, String apiSec)
            throws Exception {

        String signature = getBinanceUsSignature(data, apiSec);
        data.put("signature", signature);

        String queryParams = data.entrySet().stream()
                .map(entry -> "%s=%s".formatted(entry.getKey(), urlEncode(entry.getValue())))
                .collect(Collectors.joining("&"));

        String fullUrl = "%s%s?%s".formatted(API_URL, uriPath, queryParams);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(fullUrl))
                .header("X-MBX-APIKEY", apiKey)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    // Helper methods
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static @NotNull String bytesToHex(byte @NotNull [] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
