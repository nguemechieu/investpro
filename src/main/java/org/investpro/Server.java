package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Server {

    HttpRequest.Builder requests = HttpRequest.newBuilder();
    HttpClient client = HttpClient.newHttpClient();

    public Server() {

    }

    CompletableFuture<String> getServerData(String url, @NotNull String method, Objects headers) throws ExecutionException, InterruptedException, TimeoutException {


        requests.headers(String.valueOf(headers));
        return switch (method) {
            case "GET" ->
                    client.sendAsync(requests.uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenApply(response -> {

                                if (response.contains("error")) {
                                    throw new RuntimeException(response);
                                }
                                return response;
                            });
            case "POST" ->
                    client.sendAsync(requests.uri(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenApply(response -> {

                                if (response.contains("error")) {
                                    throw new RuntimeException(response);
                                }
                                return response;
                            });
            case "PUT" ->
                    client.sendAsync(requests.uri(URI.create(url)).PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body);
            case "DELETE" ->
                    client.sendAsync(requests.uri(URI.create(url)).DELETE().build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body);
            default -> throw new RuntimeException(method);
        };

    }

    public CompletableFuture<String> getData(String url, String method, HttpRequest.@NotNull Builder request) {
        return client.sendAsync(request.uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(method)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {

                    if (response.contains("error")) {
                        throw new RuntimeException(response);
                    }
                    return response;
                });
    }
}
