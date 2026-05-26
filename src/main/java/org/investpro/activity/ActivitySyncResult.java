package org.investpro.activity;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class ActivitySyncResult {
    @NonNull String exchangeId;
    String accountId;
    @Builder.Default Instant startedAt = Instant.now();
    @Builder.Default Instant finishedAt = Instant.now();
    String previousCursor;
    String latestCursor;
    int eventsFetched;
    int eventsProcessed;
    int eventsSkipped;
    int eventsFailed;
    @Singular List<String> warnings;
    @Singular List<String> errors;
    boolean successful;
}
