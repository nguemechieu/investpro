package org.investpro.exchange.schwab;

import org.investpro.config.AppConfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;

@lombok.extern.slf4j.Slf4j
final class SchwabAuthorizationFlow {
    private final SchwabApiConfig config;
    private final SchwabOAuthClient oauth;
    private final SchwabTokenManager tokens;
    private final Executor executor;
    private SchwabOAuthCallbackServer callback;
    private CompletableFuture<Void> pending;
    private long generation;
    SchwabAuthorizationFlow(SchwabApiConfig config, SchwabOAuthClient oauth, SchwabTokenManager tokens, Executor executor) {
        this.config = config; this.oauth = oauth; this.tokens = tokens; this.executor = executor;
    }
    synchronized CompletableFuture<Void> start() {
        if (pending != null) return pending.thenApply(_ -> null);
        long expected = ++generation;
        long tokenGeneration = tokens.beginAuthorization();
        var result = new CompletableFuture<Void>(); pending = result;
        log.info("schwab.oauth.authorization.started");
        CompletableFuture<Void> worker;
        try { worker = CompletableFuture.runAsync(() -> {
            char[] password = AppConfig.get("SCHWAB_CALLBACK_KEYSTORE_PASSWORD").toCharArray();
            try {
                if (!config.hasRequiredCredentials() || AppConfig.get("SCHWAB_TOKEN_STORE_PASSWORD").length() < 16)
                    throw new SchwabAuthenticationException("Configure Schwab client credentials and a separate token-store unlock password.");
                config.authorizationUri(); // Validate configuration before binding a callback socket.
                var tls = SchwabOAuthCallbackServer.loadTls(Path.of(AppConfig.get("SCHWAB_CALLBACK_KEYSTORE")), password);
                synchronized (this) {
                    if (generation != expected) throw new SchwabAuthenticationException("Schwab authorization cancelled.");
                    callback = new SchwabOAuthCallbackServer(config.redirectUri(), tls, executor);
                    var server = callback;
                    var code = server.start();
                    code.thenCompose(oauth::exchangeAuthorizationCode).thenAcceptAsync(state -> {
                        try { tokens.authorize(state, tokenGeneration); }
                        catch (java.io.IOException error) { throw new CompletionException(error); }
                    }, executor).whenComplete((ignored, error) -> {
                        server.close();
                        synchronized (this) {
                            if (generation != expected) return;
                            callback = null; pending = null;
                            if (error == null) result.complete(null);
                            else { tokens.invalidate(); result.completeExceptionally(new SchwabAuthenticationException("Schwab authorization failed or timed out. Check the HTTPS callback, app configuration and consent, then retry.")); }
                        }
                    });
                    java.awt.Desktop.getDesktop().browse(oauth.buildAuthorizationUri(server.state()));
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (generation == expected) {
                        if (callback != null) callback.close(); callback = null; pending = null;
                        tokens.invalidate();
                        result.completeExceptionally(new SchwabAuthenticationException("Schwab browser authorization unavailable. Configure the HTTPS redirect, official authorization URL, callback PKCS12 keystore and password."));
                    }
                }
            } finally { Arrays.fill(password, '\0'); }
        }, executor); }
        catch (RejectedExecutionException error) {
            pending = null; tokens.invalidate();
            result.completeExceptionally(new SchwabAuthenticationException("Schwab authorization worker unavailable."));
            return result;
        }
        worker.exceptionally(error -> {
            synchronized (this) {
                if (generation == expected) {
                    pending = null; tokens.invalidate();
                    result.completeExceptionally(new SchwabAuthenticationException("Schwab authorization worker unavailable."));
                }
            }
            return null;
        });
        return result.thenApply(_ -> null);
    }
    synchronized void cancel() {
        generation++;
        if (callback != null) callback.close(); callback = null;
        if (pending != null) pending.completeExceptionally(new SchwabAuthenticationException("Schwab authorization cancelled."));
        pending = null;
    }
}
