package org.investpro.exchange;

import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Top-level facade for the Solana blockchain network integration.
 *
 * <p>Wires together all Solana services and provides a single entry point
 * for the InvestPro application. All network operations are non-blocking
 * and complete on background threads; events are dispatched via
 * {@link EventBusManager}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   SolanaNetworkAdapter solana = SolanaNetworkAdapter.create();
 *   solana.connect().thenAccept(slot -> System.out.println("Connected at slot " + slot));
 *   solana.refreshBalances("YourAddress...");
 * }</pre>
 *
 * <h2>Configuration</h2>
 * Configure via {@code config.properties}:
 * <pre>
 *   solana.enabled=true
 *   solana.network=mainnet
 *   solana.rpcUrl=<a href="https://api.mainnet-beta.solana.com">...</a>
 *   solana.commitment=confirmed
 *   solana.tradingEnabled=false
 *   solana.requestTimeoutSeconds=30
 *   solana.maxRetries=3
 * </pre>
 */
public class SolanaNetworkAdapter {

    private static final Logger log = LoggerFactory.getLogger(SolanaNetworkAdapter.class);
    private static final String SOURCE = "SolanaNetworkAdapter";

    private final SolanaNetworkConfig      config;
    private final SolanaNetworkClient      rpc;
    private final SolanaWalletService      walletService;
    private final SolanaTokenService       tokenService;
    private final SolanaBalanceService     balanceService;
    private final SolanaTransactionService txService;

    // ── Construction ──────────────────────────────────────────────────────────

    public SolanaNetworkAdapter(SolanaNetworkConfig config) {
        this.config         = config;
        this.rpc            = new SolanaNetworkClient(config);
        this.walletService  = new SolanaWalletService(rpc, config);
        this.tokenService   = new SolanaTokenService();
        this.balanceService = new SolanaBalanceService(walletService, tokenService);
        this.txService      = new SolanaTransactionService(rpc, config);
    }

    /**
     * Factory method that loads configuration from {@code config.properties}
     * via {@link SolanaNetworkConfig#fromAppConfig()}.
     *
     * @return a ready-to-use adapter
     * @throws SolanaException.SolanaDisabledException if {@code solana.enabled=false}
     */
    public static SolanaNetworkAdapter create() {
        SolanaNetworkConfig cfg = SolanaNetworkConfig.fromAppConfig();
        if (!cfg.enabled()) {
            throw new SolanaException.SolanaDisabledException(
                    "Solana integration is disabled. Set solana.enabled=true in config.properties.");
        }
        log.info("Solana network adapter created: network={} rpc={} trading={}",
                cfg.network(), cfg.rpcUrl(), cfg.tradingEnabled() ? "ENABLED" : "disabled");
        return new SolanaNetworkAdapter(cfg);
    }

    // ── Connectivity ──────────────────────────────────────────────────────────

    /**
     * Verifies connectivity by fetching the current slot from the RPC node.
     *
     * <p>On success, publishes a {@link AgentEvent#SOLANA_CONNECTED} event.
     *
     * @return future resolving to the current slot number
     */
    public CompletableFuture<Long> connect() {
        return rpc.getSlot()
                .whenComplete((slot, ex) -> {
                    if (ex == null) {
                        log.info("Solana RPC connected: network={} slot={}", config.network(), slot);
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLANA_CONNECTED, SOURCE,
                                        "slot=" + slot));
                    } else {
                        log.warn("Solana RPC connection failed: {}", ex.getMessage());
                    }
                });
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    /**
     * Fetches and publishes a balance update for the given wallet address.
     *
     * <p>On success, publishes a {@link AgentEvent#SOLANA_BALANCE_UPDATED} event
     * carrying the {@link SolanaBalanceService.SolanaAccountSnapshot} as payload.
     *
     * @param address base-58 Solana public key
     * @return future resolving to the account snapshot
     */
    public CompletableFuture<SolanaBalanceService.SolanaAccountSnapshot> refreshBalances(
            String address) {

        // Silently skip unconfigured or invalid addresses (e.g. account username UUIDs)
        if (address == null || address.isBlank() || !walletService.validateAddress(address)) {
            log.debug("Solana balance refresh skipped — not a valid wallet address: '{}'",
                    address == null ? "<null>" : safePrefix(address));
            return CompletableFuture.completedFuture(
                    new SolanaBalanceService.SolanaAccountSnapshot(
                            address == null ? "" : address,
                            java.math.BigDecimal.ZERO,
                            java.util.List.of()));
        }

        return balanceService.getAccountSnapshot(address)
                .whenComplete((snapshot, ex) -> {
                    if (ex == null) {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLANA_BALANCE_UPDATED, SOURCE, snapshot));
                    } else {
                        log.warn("Solana balance refresh failed for {}...: {}",
                                safePrefix(address), ex.getMessage());
                    }
                });
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /**
     * Returns the most recent confirmed transactions for an address and publishes
     * a {@link AgentEvent#SOLANA_TRANSACTION_DETECTED} event for each one.
     *
     * @param address base-58 Solana public key
     * @param limit   max transactions to fetch (1–100)
     * @return future resolving to the list of transactions
     */
    public CompletableFuture<List<SolanaTransaction>> getRecentTransactions(
            String address, int limit) {

        return txService.getRecentTransactions(address, limit)
                .whenComplete((txs, ex) -> {
                    if (ex == null && txs != null) {
                        for (SolanaTransaction tx : txs) {
                            EventBusManager.getInstance().publish(
                                    AgentEvent.of(AgentEvent.SOLANA_TRANSACTION_DETECTED,
                                            SOURCE, tx));
                        }
                    }
                });
    }

    /**
     * Submits a signed transaction to the network.
     *
     * <p>Publishes {@link AgentEvent#SOLANA_TRANSACTION_SUBMITTED} on success
     * or {@link AgentEvent#SOLANA_TRANSACTION_FAILED} on failure.
     *
     * @param signedTransactionBase64 base-64 encoded signed transaction
     * @return future resolving to the transaction signature
     * @throws SolanaException.TradingDisabledException if trading is not enabled
     */
    public CompletableFuture<String> sendTransaction(String signedTransactionBase64) {
        return txService.sendTransaction(signedTransactionBase64)
                .whenComplete((sig, ex) -> {
                    if (ex == null) {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLANA_TRANSACTION_SUBMITTED,
                                        SOURCE, sig));
                    } else {
                        EventBusManager.getInstance().publish(
                                AgentEvent.of(AgentEvent.SOLANA_TRANSACTION_FAILED,
                                        SOURCE, ex.getMessage()));
                    }
                });
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public SolanaNetworkConfig      getConfig()         { return config; }
    public SolanaWalletService      getWalletService()  { return walletService; }
    public SolanaTokenService       getTokenService()   { return tokenService; }
    public SolanaBalanceService     getBalanceService() { return balanceService; }
    public SolanaTransactionService getTxService()      { return txService; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String safePrefix(String address) {
        if (address == null || address.length() < 6) return "???";
        return address.substring(0, 6);
    }
}
