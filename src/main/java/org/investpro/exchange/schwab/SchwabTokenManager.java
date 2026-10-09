package org.investpro.exchange.schwab;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.*;
import java.util.concurrent.*;

@Slf4j
public final class SchwabTokenManager {
    private final SchwabOAuthClient oauth;
    private final SchwabTokenStore store;
    private final Clock clock;
    private SchwabTokenState current;
    private CompletableFuture<SchwabTokenState> refresh;
    private boolean loaded, suspended, reauthorizationRequired;
    private long generation;

    SchwabTokenManager(SchwabOAuthClient oauth, SchwabTokenStore store, Clock clock) {
        this.oauth = oauth; this.store = store; this.clock = clock;
    }
    public synchronized CompletableFuture<String> getValidAccessToken() {
        try {
            if (suspended || reauthorizationRequired) throw new SchwabAuthenticationException("Schwab authorization required. Connect or reauthorize in the Schwab panel.");
            if (!loaded) { current = store.load().orElse(null); loaded = true; }
            if (current == null) {
                reauthorizationRequired = true;
                throw new SchwabAuthenticationException("Schwab authorization required. Connect or reauthorize in the Schwab panel.");
            }
            if (current.accessValid(clock.instant())) return CompletableFuture.completedFuture(current.accessToken());
            if (!current.refreshValid(clock.instant())) {
                invalidate();
                throw new SchwabAuthenticationException("Schwab refresh authorization expired. Reauthorize in the browser.");
            }
            return refreshAccessToken().thenApply(SchwabTokenState::accessToken);
        } catch (IOException error) {
            if (error instanceof SchwabAuthenticationException) invalidate();
            return CompletableFuture.failedFuture(error);
        }
    }
    public synchronized CompletableFuture<SchwabTokenState> refreshAccessToken() {
        if (refresh != null) return refresh.thenApply(state -> state);
        if (current == null || suspended || reauthorizationRequired || !current.refreshValid(clock.instant()))
            return CompletableFuture.failedFuture(new SchwabAuthenticationException("Schwab browser authorization required."));
        long expected = generation;
        var shared = new CompletableFuture<SchwabTokenState>();
        refresh = shared;
        log.info("schwab.oauth.token.refresh.started");
        CompletableFuture<SchwabTokenState> request;
        try { request = oauth.refresh(current); }
        catch (RuntimeException error) {
            refresh = null;
            shared.completeExceptionally(new SchwabAuthenticationException("Schwab token request could not start. Check OAuth configuration."));
            return shared.thenApply(state -> state);
        }
        request.whenComplete((state, failure) -> {
            synchronized (SchwabTokenManager.this) {
                if (expected != generation) { shared.completeExceptionally(new SchwabAuthenticationException("Schwab session changed.")); return; }
                try {
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                        if (cause instanceof SchwabAuthenticationException) invalidate();
                        shared.completeExceptionally(cause);
                    } else {
                        store.save(state); current = state;
                        log.info("schwab.oauth.token.refresh.completed");
                        shared.complete(state);
                    }
                } catch (IOException error) { invalidate(); shared.completeExceptionally(error); }
                finally { if (refresh == shared) refresh = null; }
            }
        });
        return shared.thenApply(state -> state);
    }
    synchronized CompletableFuture<String> refreshAfterUnauthorized(String rejectedToken) {
        if (current != null && !current.accessToken().equals(rejectedToken) && current.accessValid(clock.instant()))
            return CompletableFuture.completedFuture(current.accessToken());
        return refreshAccessToken().thenApply(SchwabTokenState::accessToken);
    }
    synchronized void authorize(SchwabTokenState state, long expectedGeneration) throws IOException {
        if (generation != expectedGeneration || suspended) throw new SchwabAuthenticationException("Schwab session changed.");
        store.save(state); current = state; loaded = true; reauthorizationRequired = false;
        log.info("schwab.oauth.authorization.completed");
    }
    synchronized long beginAuthorization() { disconnect(); suspended = false; reauthorizationRequired = false; loaded = true; return generation; }
    synchronized void connect() {
        if (reauthorizationRequired && !suspended) return;
        if (!suspended && loaded && !reauthorizationRequired) return;
        suspended = false; reauthorizationRequired = false; loaded = false;
    }
    public synchronized void invalidate() { current = null; reauthorizationRequired = true; log.info("schwab.oauth.reauthorization.required"); }
    public synchronized void disconnect() {
        generation++; suspended = true; current = null; loaded = false;
        if (refresh != null) { refresh.completeExceptionally(new SchwabAuthenticationException("Schwab disconnected.")); refresh = null; }
    }
    public synchronized void unlink() throws IOException { disconnect(); store.clear(); reauthorizationRequired = true; }
    public synchronized String status() {
        if (suspended) return "Disconnected";
        if (reauthorizationRequired || current == null) return "Authorization Required";
        if (refresh != null) return "Token Refreshing";
        return current.accessValid(clock.instant()) ? "Connected" : "Connecting";
    }
    String getAccessToken() throws IOException, InterruptedException {
        try { return getValidAccessToken().get(35, TimeUnit.SECONDS); }
        catch (ExecutionException | TimeoutException error) {
            Throwable cause = error.getCause();
            if (cause instanceof SchwabAuthenticationException auth) throw auth;
            throw new IOException("Schwab authentication unavailable. Check connectivity or reauthorize.");
        }
    }
}
