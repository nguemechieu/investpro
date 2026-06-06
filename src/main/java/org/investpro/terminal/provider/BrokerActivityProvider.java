package org.investpro.terminal.provider;

import org.investpro.terminal.domain.BrokerActivityEvent;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface BrokerActivityProvider extends ProviderCapabilities {
    CompletableFuture<List<BrokerActivityEvent>> activitySince(String accountId, Instant since, String cursor);

    default AutoCloseable subscribeActivity(String accountId, Consumer<BrokerActivityEvent> consumer) {
        return () -> { };
    }
}
