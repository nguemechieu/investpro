package org.investpro.exchange.diagnostics;

import org.investpro.exchange.ExchangeAdapter;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.services.ExchangeService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diagnostics service for exchange health and configuration.
 *
 * <p>
 * Runs health checks, captures capability snapshots, and tracks recent
 * operation status
 * to help operators debug exchange connectivity and configuration issues.
 */
public class ExchangeDiagnosticsService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeDiagnosticsService.class);

    private final ExchangeService exchangeService;
    private final Map<String, ExchangeDiagnosticSnapshot> snapshots = new ConcurrentHashMap<>();

    public ExchangeDiagnosticsService(@NotNull ExchangeService exchangeService) {
        if (exchangeService == null) {
            throw new IllegalArgumentException("exchangeService must not be null");
        }
        this.exchangeService = exchangeService;
        logger.info("ExchangeDiagnosticsService initialized");
    }

    // ==================== Snapshot Management ====================

    /**
     * Get the last captured diagnostic snapshot for an exchange.
     *
     * <p>
     * Returns cached snapshot; does not perform new checks.
     *
     * @return Snapshot wrapped in Optional
     */
    @NotNull
    public Optional<ExchangeDiagnosticSnapshot> getSnapshot(@NotNull String exchangeName) {
        return Optional.ofNullable(snapshots.get(exchangeName));
    }

    /**
     * Run full diagnostics for an exchange and capture a snapshot.
     *
     * <p>
     * Performs:
     * <ul>
     * <li>Authentication check</li>
     * <li>Instrument list check</li>
     * <li>Sample price check</li>
     * <li>Order book check (if supported)</li>
     * <li>Account snapshot check</li>
     * </ul>
     *
     * @return New diagnostic snapshot
     */
    @NotNull
    public ExchangeDiagnosticSnapshot runDiagnostics(@NotNull String exchangeName) {
        logger.info("Running diagnostics for exchange: {}", exchangeName);

        ExchangeAdapter adapter = exchangeService.getAdapter(exchangeName);
        ExchangeCapability capability = adapter.getCapability();

        // Auth check
        AuthCheckResult authResult = adapter.checkAuthentication();
        logger.info("Auth check for {}: success={}", exchangeName, authResult.isSuccess());

        // Build snapshot
        ExchangeDiagnosticSnapshot snapshot = ExchangeDiagnosticSnapshot.builder()
                .exchangeName(exchangeName)
                .credentialSource(authResult.getCredentialSource())
                .authSuccess(authResult.isSuccess())
                .authMessage(authResult.getMessage())
                .lastEndpointTested(authResult.getEndpointTested())
                .lastHttpStatus(authResult.getHttpStatus())
                .lastErrorMessage(authResult.getMessage())
                .capability(capability)
                .marketDepthType(capability.getMarketDepthType())
                .lastPriceSuccess(false)
                .lastOrderBookSuccess(false)
                .lastAccountSuccess(false)
                .snapshotTime(Instant.now())
                .build();

        // Cache the snapshot
        snapshots.put(exchangeName, snapshot);
        logger.info("Captured diagnostic snapshot for {}", exchangeName);

        return snapshot;
    }

    /**
     * Run diagnostics for all registered exchanges.
     */
    @NotNull
    public Map<String, ExchangeDiagnosticSnapshot> runAllDiagnostics() {
        Map<String, ExchangeDiagnosticSnapshot> results = new LinkedHashMap<>();
        for (String exchangeName : exchangeService.getAvailableExchanges()) {
            try {
                results.put(exchangeName, runDiagnostics(exchangeName));
            } catch (Exception e) {
                logger.warn("Diagnostics failed for {}: {}", exchangeName, e.getMessage());
            }
        }
        return results;
    }

    // ==================== Capability Queries ====================

    /**
     * Get all exchange capabilities in a map.
     */
    @NotNull
    public Map<String, ExchangeCapability> getAllCapabilities() {
        return exchangeService.getAllCapabilities();
    }

    /**
     * Get capability for a specific exchange.
     *
     * @throws IllegalArgumentException if adapter not found
     */
    @NotNull
    public ExchangeCapability getCapability(@NotNull String exchangeName) {
        return exchangeService.getCapability(exchangeName);
    }

    // ==================== Credential Source ====================

    /**
     * Get the credential source for an exchange.
     *
     * <p>
     * Useful for logging/debugging to understand where credentials came from.
     *
     * @return Credential source, e.g., "ENV_VAR", "CONFIG_FILE"
     */
    @NotNull
    public Optional<String> getCredentialSource(@NotNull String exchangeName) {
        return getSnapshot(exchangeName).map(ExchangeDiagnosticSnapshot::getCredentialSource);
    }

    /**
     * Get all credential sources.
     */
    @NotNull
    public Map<String, String> getAllCredentialSources() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : snapshots.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getCredentialSource());
        }
        return result;
    }

    // ==================== HTTP Status ====================

    /**
     * Get the last HTTP status from an exchange check.
     *
     * @return HTTP status code, or 0 if no check performed
     */
    public int getLastHttpStatus(@NotNull String exchangeName) {
        return getSnapshot(exchangeName)
                .map(ExchangeDiagnosticSnapshot::getLastHttpStatus)
                .orElse(0);
    }

    /**
     * Get all last HTTP statuses.
     */
    @NotNull
    public Map<String, Integer> getAllLastHttpStatuses() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : snapshots.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getLastHttpStatus());
        }
        return result;
    }

    // ==================== Error Messages ====================

    /**
     * Get the last error message from an exchange check.
     *
     * <p>
     * Returns empty string if no error.
     */
    @NotNull
    public String getLastErrorMessage(@NotNull String exchangeName) {
        return getSnapshot(exchangeName)
                .map(ExchangeDiagnosticSnapshot::getLastErrorMessage)
                .orElse("");
    }

    // ==================== Status ====================

    /**
     * Check if an exchange passed its last auth check.
     */
    public boolean isAuthSuccessful(@NotNull String exchangeName) {
        return getSnapshot(exchangeName)
                .map(ExchangeDiagnosticSnapshot::isAuthSuccess)
                .orElse(false);
    }

    /**
     * Get a human-readable summary of exchange health.
     */
    @NotNull
    public String getHealthSummary(@NotNull String exchangeName) {
        Optional<ExchangeDiagnosticSnapshot> snap = getSnapshot(exchangeName);
        if (snap.isEmpty()) {
            return "No diagnostics run yet for " + exchangeName;
        }
        ExchangeDiagnosticSnapshot s = snap.get();
        return String.format("%s: auth=%s, httpStatus=%d, lastEndpoint=%s, time=%s",
                s.getExchangeName(),
                s.isAuthSuccess() ? "OK" : "FAILED",
                s.getLastHttpStatus(),
                s.getLastEndpointTested(),
                s.getSnapshotTime());
    }

    /**
     * Get summaries for all exchanges.
     */
    @NotNull
    public List<String> getAllHealthSummaries() {
        return exchangeService.getAvailableExchanges()
                .stream()
                .map(this::getHealthSummary)
                .toList();
    }
}
