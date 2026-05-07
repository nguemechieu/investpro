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
        String openaiOrgId,
        String tradingMode) {
    public String telegramToken() {
        return telegramToken;

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

    public String tradingMode() {
        return tradingMode != null ? tradingMode : "LIVE";
    }

    public boolean isPaperTrading() {
        return "PAPER".equalsIgnoreCase(tradingMode());
    }

    public boolean hasOpenAiConfiguration() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
