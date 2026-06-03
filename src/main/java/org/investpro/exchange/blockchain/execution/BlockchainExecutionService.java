package org.investpro.exchange.blockchain.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;
import org.investpro.exchange.blockchain.events.BlockchainTransactionConfirmedEvent;
import org.investpro.exchange.blockchain.events.BlockchainTransactionFailedEvent;
import org.investpro.exchange.blockchain.events.BlockchainTransactionSubmittedEvent;
import org.investpro.exchange.blockchain.events.BlockchainTransactionTimeoutEvent;
import org.investpro.risk.RiskDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Routes blockchain requests, submits transactions, tracks status, and manages
 * retries.
 */
@Slf4j
public class BlockchainExecutionService {

    private final Map<String, BlockchainExecutionProvider> providers;
    private final BlockchainTransactionRepository repository;
    private final BlockchainTransactionTracker tracker;
    private final BlockchainPortfolioIntegrationService portfolioIntegration;
    private final BlockchainExecutionTelemetry telemetry;
    private final ConfirmationPolicy confirmationPolicy;
    private final BlockchainPreflightRiskValidator riskValidator;
    private final AgentEventBus eventBus;
    private final int maxRetries;

    public BlockchainExecutionService(
            List<BlockchainExecutionProvider> providers,
            BlockchainTransactionRepository repository,
            BlockchainTransactionTracker tracker,
            BlockchainPortfolioIntegrationService portfolioIntegration,
            BlockchainExecutionTelemetry telemetry,
            ConfirmationPolicy confirmationPolicy,
            BlockchainPreflightRiskValidator riskValidator,
            AgentEventBus eventBus) {
        this(providers, repository, tracker, portfolioIntegration, telemetry,
                confirmationPolicy, riskValidator, eventBus, 2);
    }

    public BlockchainExecutionService(
            List<BlockchainExecutionProvider> providers,
            BlockchainTransactionRepository repository,
            BlockchainTransactionTracker tracker,
            BlockchainPortfolioIntegrationService portfolioIntegration,
            BlockchainExecutionTelemetry telemetry,
            ConfirmationPolicy confirmationPolicy,
            BlockchainPreflightRiskValidator riskValidator,
            AgentEventBus eventBus,
            int maxRetries) {
        Objects.requireNonNull(providers, "providers");
        this.providers = new HashMap<>();
        for (BlockchainExecutionProvider provider : providers) {
            this.providers.put(provider.networkId().toUpperCase(Locale.ROOT), provider);
        }
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.portfolioIntegration = Objects.requireNonNull(portfolioIntegration, "portfolioIntegration");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.confirmationPolicy = Objects.requireNonNull(confirmationPolicy, "confirmationPolicy");
        this.riskValidator = Objects.requireNonNull(riskValidator, "riskValidator");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.maxRetries = Math.max(0, maxRetries);
    }

    public CompletableFuture<BlockchainTransactionResult> executeTransfer(
            BlockchainExecutionRequests.TransferRequest request) {
        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).submitTransfer(request),
                result -> portfolioIntegration.applyTransfer(request, result));
    }

    public CompletableFuture<BlockchainTransactionResult> executeSwap(
            BlockchainExecutionRequests.SwapRequest request) {
        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).submitSwap(request),
                result -> portfolioIntegration.applySwap(request, result));
    }

    public CompletableFuture<BlockchainTransactionResult> executeOrder(
            BlockchainExecutionRequests.OrderRequest request,
            RiskDecision riskDecision) {
        BlockchainPreflightRiskValidator.ValidationResult validation = riskValidator.validateOrder(request,
                riskDecision);
        if (!validation.allowed()) {
            BlockchainTransactionResult blocked = new BlockchainTransactionResult(
                    request.transactionId(),
                    request.networkId(),
                    BlockchainTransactionResult.TransactionOutcome.SIMULATION_FAILED,
                    null,
                    null,
                    0,
                    validation.errorCode(),
                    validation.errorMessage(),
                    Instant.now(),
                    null);
            persistAndTrack(blocked);
            publishOutcome(blocked);
            telemetry.recordResult(blocked);
            return CompletableFuture.completedFuture(blocked);
        }

        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).submitOrder(request),
                result -> portfolioIntegration.applyOrder(request, result));
    }

    public CompletableFuture<BlockchainTransactionResult> cancelOrder(
            BlockchainExecutionRequests.CancelOrderRequest request) {
        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).cancelOrder(request),
                result -> {
                });
    }

    public CompletableFuture<BlockchainTransactionResult> stake(
            BlockchainExecutionRequests.StakeRequest request) {
        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).stake(request),
                result -> portfolioIntegration.applyStake(request, result));
    }

    public CompletableFuture<BlockchainTransactionResult> unstake(
            BlockchainExecutionRequests.UnstakeRequest request) {
        return executeWithRetry(
                request.networkId(),
                request.transactionId(),
                () -> provider(request.networkId()).unstake(request),
                result -> portfolioIntegration.applyUnstake(request, result));
    }

    public CompletableFuture<BlockchainTransactionResult> trackTransaction(
            BlockchainExecutionRequests.TransactionStatusRequest request) {
        return provider(request.networkId()).getTransactionStatus(request)
                .thenApply(this::normalizeDepthAndPersist);
    }

    public CompletableFuture<BlockchainTransactionResult> awaitConfirmation(
            String networkId,
            String transactionId,
            String signature,
            Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(20) : timeout;

        return CompletableFuture.supplyAsync(() -> {
            Instant deadline = Instant.now().plus(effectiveTimeout);
            BlockchainExecutionProvider provider = provider(networkId);

            while (Instant.now().isBefore(deadline)) {
                BlockchainTransactionResult status = provider.getTransactionStatus(
                        BlockchainExecutionRequests.TransactionStatusRequest.of(networkId, transactionId, signature))
                        .join();

                BlockchainTransactionResult persisted = normalizeDepthAndPersist(status);
                if (persisted.isConfirmed() || persisted.isFailed()) {
                    return persisted;
                }

                try {
                    Thread.sleep(500L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(interruptedException);
                }
            }

            BlockchainTransactionResult timeoutResult = new BlockchainTransactionResult(
                    transactionId,
                    networkId,
                    BlockchainTransactionResult.TransactionOutcome.TIMEOUT,
                    signature,
                    null,
                    0,
                    "CONFIRMATION_TIMEOUT",
                    "Timed out waiting for confirmation",
                    Instant.now().minus(effectiveTimeout),
                    null);
            persistAndTrack(timeoutResult);
            publishOutcome(timeoutResult);
            telemetry.recordResult(timeoutResult);
            return timeoutResult;
        });
    }

    private CompletableFuture<BlockchainTransactionResult> executeWithRetry(
            String networkId,
            String transactionId,
            Supplier<CompletableFuture<BlockchainTransactionResult>> submit,
            Consumer<BlockchainTransactionResult> onConfirmed) {
        telemetry.recordSubmitted();

        CompletableFuture<BlockchainTransactionResult> chain = submitOnce(networkId, transactionId, submit,
                onConfirmed);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            final int retryAttempt = attempt;
            chain = chain.thenCompose(result -> {
                if (!shouldRetry(result)) {
                    return CompletableFuture.completedFuture(result);
                }
                log.warn("Retrying blockchain submission tx={} network={} attempt={}", transactionId, networkId,
                        retryAttempt);
                return submitOnce(networkId, transactionId, submit, onConfirmed);
            });
        }

        return chain;
    }

    private CompletableFuture<BlockchainTransactionResult> submitOnce(
            String networkId,
            String transactionId,
            Supplier<CompletableFuture<BlockchainTransactionResult>> submit,
            Consumer<BlockchainTransactionResult> onConfirmed) {
        try {
            return submit.get()
                    .thenApply(this::normalizeDepthAndPersist)
                    .thenApply(result -> {
                        if (result.isConfirmed()) {
                            onConfirmed.accept(result);
                        }
                        return result;
                    });
        } catch (Exception exception) {
            BlockchainTransactionResult failed = BlockchainTransactionResult.failed(
                    transactionId,
                    networkId,
                    "SUBMISSION_EXCEPTION",
                    exception.getMessage());
            BlockchainTransactionResult persisted = normalizeDepthAndPersist(failed);
            return CompletableFuture.completedFuture(persisted);
        }
    }

    private BlockchainTransactionResult normalizeDepthAndPersist(BlockchainTransactionResult result) {
        int required = confirmationPolicy.requiredConfirmations(result.networkId());
        int normalizedDepth = Math.max(result.confirmationDepth(), result.isConfirmed() ? required : 0);

        BlockchainTransactionResult normalized = new BlockchainTransactionResult(
                result.transactionId(),
                result.networkId(),
                result.outcome(),
                result.signature(),
                result.feeUnitsConsumed(),
                normalizedDepth,
                result.errorCode(),
                result.errorMessage(),
                result.submittedAt(),
                result.confirmedAt());

        persistAndTrack(normalized);
        publishOutcome(normalized);
        telemetry.recordResult(normalized);
        return normalized;
    }

    private void persistAndTrack(BlockchainTransactionResult result) {
        repository.save(result);
        tracker.update(result);
    }

    private boolean shouldRetry(BlockchainTransactionResult result) {
        return result.outcome() == BlockchainTransactionResult.TransactionOutcome.FAILED
                || result.outcome() == BlockchainTransactionResult.TransactionOutcome.TIMEOUT;
    }

    private BlockchainExecutionProvider provider(String networkId) {
        if (networkId == null || networkId.isBlank()) {
            throw new IllegalArgumentException("networkId must not be blank");
        }

        BlockchainExecutionProvider provider = providers.get(networkId.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("No blockchain provider registered for networkId=" + networkId);
        }
        return provider;
    }

    private void publishOutcome(BlockchainTransactionResult result) {
        eventBus.publish(AgentEvent.execution(
                AgentEvent.BLOCKCHAIN_TRANSACTION_SUBMITTED,
                "BlockchainExecutionService",
                new BlockchainTransactionSubmittedEvent(result)));

        if (result.outcome() == BlockchainTransactionResult.TransactionOutcome.CONFIRMED) {
            eventBus.publish(AgentEvent.execution(
                    AgentEvent.BLOCKCHAIN_TRANSACTION_CONFIRMED,
                    "BlockchainExecutionService",
                    new BlockchainTransactionConfirmedEvent(result)));
            return;
        }

        if (result.outcome() == BlockchainTransactionResult.TransactionOutcome.TIMEOUT) {
            eventBus.publish(AgentEvent.execution(
                    AgentEvent.BLOCKCHAIN_TRANSACTION_TIMEOUT,
                    "BlockchainExecutionService",
                    new BlockchainTransactionTimeoutEvent(result)));
            return;
        }

        if (result.isFailed()) {
            eventBus.publish(AgentEvent.execution(
                    AgentEvent.BLOCKCHAIN_TRANSACTION_FAILED,
                    "BlockchainExecutionService",
                    new BlockchainTransactionFailedEvent(result)));
        }
    }

    public BlockchainTransactionRepository repository() {
        return repository;
    }

    public BlockchainTransactionTracker tracker() {
        return tracker;
    }

    public BlockchainExecutionTelemetry telemetry() {
        return telemetry;
    }

    public BlockchainPortfolioIntegrationService portfolioIntegration() {
        return portfolioIntegration;
    }
}
