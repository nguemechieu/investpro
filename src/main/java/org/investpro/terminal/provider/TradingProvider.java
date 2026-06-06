package org.investpro.terminal.provider;

import org.investpro.terminal.domain.ExecutionPlan;
import org.investpro.terminal.domain.OrderId;
import org.investpro.terminal.domain.OrderRequest;
import org.investpro.terminal.domain.OrderState;

import java.util.concurrent.CompletableFuture;

public interface TradingProvider extends ProviderCapabilities {
    CompletableFuture<OrderId> submitOrder(ExecutionPlan executionPlan);

    CompletableFuture<OrderState> orderStatus(OrderId orderId);

    default CompletableFuture<OrderId> previewOrder(OrderRequest request) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(providerId() + " does not preview orders"));
    }

    default CompletableFuture<Boolean> cancelOrder(OrderId orderId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(providerId() + " does not cancel orders"));
    }
}
