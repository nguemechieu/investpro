package org.investpro.exchange.schwab;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived loopback HTTPS listener. TLS identity is provisioned independently of OAuth tokens. */
@lombok.extern.slf4j.Slf4j
public final class SchwabOAuthCallbackServer implements AutoCloseable {
    private final HttpsServer server;
    private final URI redirect;
    private final String state;
    private final java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean();
    private final CompletableFuture<String> code = new CompletableFuture<>();

    public SchwabOAuthCallbackServer(URI redirect, SSLContext tls, Executor executor) throws java.io.IOException {
        this.redirect = SchwabApiConfig.requireHttps(redirect.toString());
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(redirect.getHost()))
            throw new SchwabAuthenticationException("Use a registered loopback HTTPS callback for the desktop listener.");
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName(redirect.getHost()),
                redirect.getPort() < 0 ? 443 : redirect.getPort()), 8);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.setExecutor(executor);
        server.createContext(path(), this::handle);
    }
    String state() { return state; }
    int boundPort() { return server.getAddress().getPort(); }
    CompletableFuture<String> start() { server.start(); return code.orTimeout(3, TimeUnit.MINUTES); }
    private String path() { return redirect.getPath().isEmpty() ? "/" : redirect.getPath(); }
    private void handle(HttpExchange exchange) throws java.io.IOException {
        boolean success = false;
        String value = null;
        try {
            if (!"GET".equals(exchange.getRequestMethod()) || !path().equals(exchange.getRequestURI().getPath())) throw new IllegalArgumentException();
            value = validateCallback(exchange.getRequestURI().getRawQuery());
            success = true;
        } catch (Exception error) {
            success = false;
        }
        byte[] response = (success ? "Authorization received. Return to InvestPro to check completion."
                : "Authorization failed. Return to InvestPro and restart authorization.").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(success ? 200 : 400, response.length);
        try (var output = exchange.getResponseBody()) { output.write(response); }
        finally {
            exchange.close();
            if (success) { log.info("schwab.oauth.callback.received"); code.complete(value); }
            else code.completeExceptionally(new SchwabAuthenticationException("Schwab authorization callback rejected. Restart authorization."));
        }
    }
    String validateCallback(String query) throws SchwabAuthenticationException {
        try {
            if (query == null || query.length() > 16384) throw new IllegalArgumentException();
            Map<String, String> params = new HashMap<>();
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2 || params.putIfAbsent(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException();
            }
            String returnedState = params.get("state");
            if (returnedState == null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8))
                    || params.containsKey("error") || params.getOrDefault("code", "").isBlank()
                    || !consumed.compareAndSet(false, true)) throw new IllegalArgumentException();
            return params.get("code");
        } catch (Exception error) { throw new SchwabAuthenticationException("Invalid or denied Schwab callback. Restart authorization."); }
    }
    static SSLContext loadTls(Path file, char[] password) throws Exception {
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(file)) { keys.load(input, password); }
        var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, password);
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(manager.getKeyManagers(), null, null); return tls;
    }
    @Override public void close() { server.stop(0); code.completeExceptionally(new SchwabAuthenticationException("Schwab authorization cancelled.")); }
}
