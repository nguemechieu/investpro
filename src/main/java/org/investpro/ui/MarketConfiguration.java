package org.investpro.ui;

public record MarketConfiguration(
    String username,
    String marketType,
    String venue,
    String exchange,
    String apiKey,
    String apiSecret,
    String accountId,
    String telegramToken,
    String openaiApiKey,
    String openaiModel,
    String openaiOrgId
) {
    public String telegramToken() {
        return  telegramToken;

    }

    public String openaiApiKey() {
        return openaiApiKey;
    }

    public String openaiModel() {
        return openaiModel;
    }

    public String openaiOrgId() {
        return openaiOrgId;
    }

    public boolean hasOpenAiConfiguration() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
