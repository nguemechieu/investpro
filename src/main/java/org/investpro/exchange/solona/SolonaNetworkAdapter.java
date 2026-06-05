package org.investpro.exchange.solona;

import lombok.Data;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Top-level facade for the Solona blockchain network integration.
 *
 * <p>Wires together all Solona services and provides a single entry point
 * for the InvestPro application. All network operations are non-blocking
 * and complete on background threads; events are dispatched via
 * {@link EventBusManager}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   SolonaNetworkAdapter solona = SolonaNetworkAdapter.create();
 *   solona.connect().thenAccept(slot -> System.out.println("Connected at slot " + slot));
 *   solona.refreshBalances("YourAddress...");
 * }</pre>
 *
 * <h2>Configuration</h2>
 * Configure via {@code config.properties}:
 * <pre>
 *   solona.enabled=true
 *   solona.network=mainnet
 *   solona.rpcUrl=<a href="https://api.mainnet-beta.solona.com">...</a>
 *   solona.commitment=confirmed
 *   solona.tradingEnabled=false
 *   solona.requestTimeoutSeconds=30
 *   solona.maxRetries=3
 * </pre>
 */
@Data
public class SolonaNetworkAdapter {

    private static final Logger log = LoggerFactory.getLogger(SolonaNetworkAdapter.class);
    private static final String SOURCE = "SolonaNetworkAdapter";

    private final SolonaNetworkConfig      config;
    private final SolonaNetworkClient      rpc;
    private final SolonaWalletService      walletService;
    private final SolonaTokenService       tokenService;
    private final SolonaBalanceService     balanceService;
    private final SolonaTransactionService txService;

    // ── Construction ──────────────────────────────────────────────────────────

    public SolonaNetworkAdapter(SolonaNetworkConfig config) {
        this.config         = config;
        this.rpc            = new SolonaNetworkClient(config);
        this.walletService  = new SolonaWalletService(rpc, config);
        this.tokenService   = new SolonaTokenService();
        this.balanceService = new SolonaBalanceService(walletService, tokenService);
        this.txService      = new SolonaTransactionService(rpc, config);
    }

    /**
     * Factory method that loads configuration from {@code config.properties}
     * via {@link SolonaNetworkConfig#fromAppConfig()}.
     *
     * @return a ready-to-use adapter
     * @throws SolonaException.SolonaDisabledException if {@code solona.enabled=false}
     */
    @Contract(" -> new")
    public static @NonNull SolonaNetworkAdapter create() {
        SolonaNetworkConfig cfg = SolonaNetworkConfig.fromAppConfig();
        if (!cfg.enabled()) {
            throw new SolonaException.SolonaDisabledException(
                    "Solona integration is disabled. Set solona.enabled=true in config.properties.");
        }
        log.info("Solona network adapter created: network={} rpc={} trading={}",
                cfg.network(), cfg.rpcUrl(), cfg.tradingEnabled() ? "ENABLED" : "disabled");
        return new SolonaNetworkAdapter(cfg);
    }

    // ── Connectivity ──────────────────────────────────────────────────────────

    /**
     * Verifies connectivity by fetching the current slot from the RPC node.
     *
     * <p>On success, publishes a {@link AgentEvent#SOLONA_CONNECTED} event.
     *
     * @return future resolving to the current slot number
     */
    public CompletableFuture<Long> connect() {
        return rpc.getSlot()
                .whenComplete((slot, ex) -> {
                    if (ex == null) {
                        log.info("Solona RPC connected: network={} slot={}", config.network(), slot);
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLONA_CONNECTED, SOURCE,
                                        "slot=" + slot));
                    } else {
                        log.warn("Solona RPC connection failed: {}", ex.getMessage());
                    }
                });
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    /**
     * Fetches and publishes a balance update for the given wallet address.
     *
     * <p>On success, publishes a {@link AgentEvent#SOLONA_BALANCE_UPDATED} event
     * carrying the {@link SolonaBalanceService.SolonaAccountSnapshot} as payload.
     *
     * @param address base-58 Solona public key
     * @return future resolving to the account snapshot
     */
    public CompletableFuture<SolonaBalanceService.SolonaAccountSnapshot> refreshBalances(
            String address) {

        // Silently skip unconfigured or invalid addresses (e.g. account username UUIDs)
        if (address == null || address.isBlank() || walletService.validateAddress(address)) {
            log.debug("Solona balance refresh skipped — not a valid wallet address: '{}'",
                    address == null ? "<null>" : safePrefix(address));
            return CompletableFuture.completedFuture(
                    new SolonaBalanceService.SolonaAccountSnapshot(
                            address == null ? "" : address,
                            java.math.BigDecimal.ZERO,
                            java.util.List.of()));
        }

        return balanceService.getAccountSnapshot(address)
                .whenComplete((snapshot, ex) -> {
                    if (ex == null) {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLONA_BALANCE_UPDATED, SOURCE, snapshot));
                    } else {
                        log.warn("Solona balance refresh failed for {}...: {}",
                                safePrefix(address), ex.getMessage());
                    }
                });
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /**
     * Returns the most recent confirmed transactions for an address and publishes
     * a {@link AgentEvent#SOLONA_TRANSACTION_DETECTED} event for each one.
     *
     * @param address base-58 Solona public key
     * @param limit   max transactions to fetch (1–100)
     * @return future resolving to the list of transactions
     */
    public CompletableFuture<List<SolonaTransaction>> getRecentTransactions(
            String address, int limit) {

        return txService.getRecentTransactions(address, limit)
                .whenComplete((txs, ex) -> {
                    if (ex == null && txs != null) {
                        for (SolonaTransaction tx : txs) {
                            EventBusManager.getInstance().publish(
                                    AgentEvent.of(AgentEvent.SOLONA_TRANSACTION_DETECTED,
                                            SOURCE, tx));
                        }
                    }
                });
    }

    /**
     * Submits a signed transaction to the network.
     *
     * <p>Publishes {@link AgentEvent#SOLONA_TRANSACTION_SUBMITTED} on success
     * or {@link AgentEvent#SOLONA_TRANSACTION_FAILED} on failure.
     *
     * @param signedTransactionBase64 base-64 encoded signed transaction
     * @return future resolving to the transaction signature
     * @throws SolonaException.TradingDisabledException if trading is not enabled
     */
    public CompletableFuture<String> sendTransaction(String signedTransactionBase64) {
        return txService.sendTransaction(signedTransactionBase64)
                .whenComplete((sig, ex) -> {
                    if (ex == null) {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLONA_TRANSACTION_SUBMITTED,
                                        SOURCE, sig));
                    } else {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLONA_TRANSACTION_FAILED,
                                        SOURCE, ex.getMessage()));
                    }
                });
    }



    // ── Helpers ───────────────────────────────────────────────────────────────

    private static @NonNull String safePrefix(String address) {
        if (address == null || address.length() < 6) return "???";
        return address.substring(0, 6);
    }
}
