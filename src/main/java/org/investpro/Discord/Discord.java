package org.investpro.Discord;


import org.investpro.Chat;
import org.investpro.Coinbase.Coinbase;

public class Discord {
    private String apiUrl;
    private String apiVersion;
    private String clientSecret;
    private String clientId;
    private String accessToken;
    private String refreshToken;

    public Discord(
            final String clientId,
            final String clientSecret,
            final String accessToken,
            final String refreshToken) {

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.apiUrl = Coinbase.API_URL;
        this.apiVersion = Coinbase.API_VERSION;

        if (this.clientId == null || this.clientSecret == null) {
            throw new IllegalArgumentException("You must provide clientId and clientSecret");

            // this.clientId = clientId;
            // this.clientSecret = clientSecret;
            // this.apiUrl = API_URL;
            // this.apiVersion = API_VERSION;
        }

        if (this.accessToken == null || this.refreshToken == null) {
            throw new IllegalArgumentException("You must provide accessToken and refreshToken");

            // this.accessToken = accessToken;
            // this.refreshToken = refreshToken;
            // this.apiUrl = API_URL;
            // this.apiVersion = API_VERSION;
        }


    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    void sendMessage(Chat chat, String message) {
    }

    void sendMessage(Chat chat, String message, String file) {

        sendMessage(chat, message);
        sendMessage(chat, file);

    }

    void sendPhoto(
            Chat chat,
            String message,
            String file) {
    }
}