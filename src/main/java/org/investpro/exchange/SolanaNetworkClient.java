package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async HTTP JSON-RPC client for the Solana network.
 *
 * <p>Wraps all Solana RPC calls using the standard JSON-RPC 2.0 protocol.
 * No external Solana SDK is required — all communication uses Java 21's
 * {@link HttpClient} and Jackson for JSON serialisation.
 *
 * <p>Thread-safe. A single instance should be shared per {@link SolanaNetworkConfig}.
 */
@Data
public class SolanaNetworkClient {

    private static final Logger log = LoggerFactory.getLogger(SolanaNetworkClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong REQUEST_ID = new AtomicLong(1);

    private final SolanaNetworkConfig config;
    private final HttpClient httpClient;

    public SolanaNetworkClient(SolanaNetworkConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                .build();
    }

    // ── Health ────────────────────────────────────────────────────────────────

    /**
     * Returns the current slot number (confirms the RPC node is alive).
     *
     * @return slot number as a {@link CompletableFuture}
     */
    public CompletableFuture<Long> getSlot() {
        return call("getSlot")
                .thenApply(result -> result.asLong(-1L));
    }

    // ── Balances ─────────────────────────────────────────────────────────────

    /**
     * Returns the SOL balance in lamports for the given public key.
     *
     * @param address base-58 encoded public key
     * @return lamports (divide by {@link SolanaNetworkConfig#LAMPORTS_PER_SOL} to get SOL)
     */
    public CompletableFuture<Long> getBalance(String address) {
        ArrayNode params = MAPPER.createArrayNode()
                .add(address)
                .add(commitmentObject());
        return call("getBalance", params)
                .thenApply(result -> result.path("value").asLong(0L));
    }

    /**
     * Returns all SPL token accounts owned by {@code ownerAddress}.
     *
     * @param ownerAddress owner's base-58 public key
     * @return raw JSON "value" array node
     */
    public CompletableFuture<JsonNode> getTokenAccountsByOwner(String ownerAddress) {
        ObjectNode programFilter = MAPPER.createObjectNode()
                .put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"); // SPL Token program
        ArrayNode params = MAPPER.createArrayNode()
                .add(ownerAddress)
                .add(programFilter)
                .add(MAPPER.createObjectNode().put("encoding", "jsonParsed").set("commitment",
                        MAPPER.getNodeFactory().textNode(config.commitment())));
        return call("getTokenAccountsByOwner", params)
                .thenApply(result -> result.path("value"));
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /**
     * Returns confirmed signatures for transactions involving {@code address}.
     *
     * @param address base-58 address
     * @param limit   max number of signatures (1–1000)
     * @return JSON array node of signature objects
     */
    public CompletableFuture<JsonNode> getSignaturesForAddress(String address, int limit) {
        ObjectNode opts = MAPPER.createObjectNode()
                .put("limit", Math.min(1000, Math.max(1, limit)));
        ArrayNode params = MAPPER.createArrayNode().add(address).add(opts);
        return call("getSignaturesForAddress", params);
    }

    /**
     * Returns the parsed details of a confirmed transaction.
     *
     * @param signature base-58 transaction signature
     * @return raw JSON result node (may be null/missing if transaction not found)
     */
    public CompletableFuture<JsonNode> getTransaction(String signature) {
        ObjectNode opts = MAPPER.createObjectNode()
                .put("encoding", "jsonParsed")
                .put("commitment", config.commitment())
                .put("maxSupportedTransactionVersion", 0);
        ArrayNode params = MAPPER.createArrayNode().add(signature).add(opts);
        return call("getTransaction", params);
    }

    /**
     * Returns the minimum fee (in lamports) for a message.
     *
     * @param messageBase64 base-64 encoded serialised message
     * @return fee in lamports
     */
    public CompletableFuture<Long> getFeeForMessage(String messageBase64) {
        ArrayNode params = MAPPER.createArrayNode().add(messageBase64).add(commitmentObject());
        return call("getFeeForMessage", params)
                .thenApply(result -> result.path("value").asLong(5000L));
    }

    /**
     * Sends a signed, serialised transaction to the network.
     *
     * <p><b>IMPORTANT:</b> callers must verify {@link SolanaNetworkConfig#isLiveTradingAllowed()}
     * before calling this method.
     *
     * @param signedTransactionBase64 base-64 encoded signed transaction
     * @return transaction signature string
     */
    public CompletableFuture<String> sendTransaction(String signedTransactionBase64) {
        ObjectNode opts = MAPPER.createObjectNode()
                .put("encoding", "base64")
                .put("preflightCommitment", config.commitment());
        ArrayNode params = MAPPER.createArrayNode().add(signedTransactionBase64).add(opts);
        return call("sendTransaction", params)
                .thenApply(JsonNode::asText);
    }

    // ── Core JSON-RPC ─────────────────────────────────────────────────────────

    /**
     * Sends a JSON-RPC request with no extra params.
     */
    public CompletableFuture<JsonNode> call(String method) {
        return call(method, MAPPER.createArrayNode());
    }

    /**
     * Sends a JSON-RPC request and returns the {@code result} field of the response.
     *
     * @param method RPC method name
     * @param params JSON array of parameters
     * @return future resolving to the "result" node, never null
     * @throws SolanaException.RpcException if the RPC response contains an error
     */
    public CompletableFuture<JsonNode> call(String method, ArrayNode params) {
        long id       = REQUEST_ID.getAndIncrement();
        String body   = buildRequest(id, method, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.rpcUrl()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                .build();

        log.debug("Solana RPC → [{}] id={} network={}", method, id, config.network());

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(method, id, response));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildRequest(long id, String method, ArrayNode params) {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        req.set("params", params);
        return req.toString();
    }

    private JsonNode parseResponse(String method, long id, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SolanaException.RpcException(method, response.statusCode(),
                    "HTTP " + response.statusCode());
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new SolanaException("Failed to parse Solana RPC response for " + method, e);
        }

        JsonNode errorNode = root.path("error");
        if (!errorNode.isMissingNode() && !errorNode.isNull()) {
            int    code    = errorNode.path("code").asInt(-1);
            String message = errorNode.path("message").asText("unknown RPC error");
            throw new SolanaException.RpcException(method, code, message);
        }

        JsonNode result = root.path("result");
        log.debug("Solana RPC ← [{}] id={} resultType={}", method, id,
                result.isNull() ? "null" : result.getNodeType().name());
        return result;
    }

    private ObjectNode commitmentObject() {
        return MAPPER.createObjectNode().put("commitment", config.commitment());
    }
}
