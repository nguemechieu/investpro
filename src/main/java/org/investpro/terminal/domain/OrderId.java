package org.investpro.terminal.domain;

public record OrderId(String providerId, String accountId, String clientOrderId, String externalOrderId) {
    public OrderId {
        providerId = providerId == null ? "" : providerId.trim();
        accountId = accountId == null ? "" : accountId.trim();
        clientOrderId = clientOrderId == null ? "" : clientOrderId.trim();
        externalOrderId = externalOrderId == null ? "" : externalOrderId.trim();
    }

    public String stableKey() {
        return providerId + ":" + accountId + ":" + (!externalOrderId.isBlank() ? externalOrderId : clientOrderId);
    }
}
