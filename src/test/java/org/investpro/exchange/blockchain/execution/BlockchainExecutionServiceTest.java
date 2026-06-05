package org.investpro.exchange.blockchain.execution;

import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;
import org.investpro.risk.RiskDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BlockchainExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void confirmedTransactionUpdatesPortfolio() {
        TestContext context = withProviders(new SolonaExecutionProvider(), new StellarExecutionProvider());

        BlockchainExecutionRequests.TransferRequest request = BlockchainExecutionRequests.TransferRequest.create(
                "SOLONA", "wallet-a", "wallet-b", "SOL", 2.0);

        BlockchainTransactionResult result = context.service.executeTransfer(request).join();

        assertThat(result.outcome()).isEqualTo(BlockchainTransactionResult.TransactionOutcome.CONFIRMED);
        assertThat(context.portfolio.balanceOf("SOL")).isEqualTo(2.0);
    }

    @Test
    void failedTransactionDoesNotUpdateBalances() {
        BlockchainExecutionProvider failingProvider = new AbstractBlockchainExecutionProvider() {
            @Override
            public String networkId() {
                return "SOLONA";
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitTransfer(
                    BlockchainExecutionRequests.TransferRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitSwap(
                    BlockchainExecutionRequests.SwapRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitOrder(
                    BlockchainExecutionRequests.OrderRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> cancelOrder(
                    BlockchainExecutionRequests.CancelOrderRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> stake(
                    BlockchainExecutionRequests.StakeRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> unstake(
                    BlockchainExecutionRequests.UnstakeRequest request) {
                return CompletableFuture.completedFuture(BlockchainTransactionResult.failed(
                        request.transactionId(), networkId(), "FAIL", "Forced failure"));
            }
        };

        TestContext context = withProviders(failingProvider);

        BlockchainExecutionRequests.TransferRequest request = BlockchainExecutionRequests.TransferRequest.create(
                "SOLONA", "wallet-a", "wallet-b", "SOL", 2.0);

        BlockchainTransactionResult result = context.service.executeTransfer(request).join();

        assertThat(result.outcome()).isEqualTo(BlockchainTransactionResult.TransactionOutcome.FAILED);
        assertThat(context.portfolio.balanceOf("SOL")).isZero();
    }

    @Test
    void timeoutHandledCorrectly() {
        BlockchainExecutionProvider pendingProvider = new AbstractBlockchainExecutionProvider() {
            @Override
            public String networkId() {
                return "SOLONA";
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitTransfer(
                    BlockchainExecutionRequests.TransferRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(),
                        networkId(),
                        BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending",
                        null,
                        0,
                        null,
                        null,
                        Instant.now(),
                        null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitSwap(
                    BlockchainExecutionRequests.SwapRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitOrder(
                    BlockchainExecutionRequests.OrderRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> cancelOrder(
                    BlockchainExecutionRequests.CancelOrderRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> stake(
                    BlockchainExecutionRequests.StakeRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> unstake(
                    BlockchainExecutionRequests.UnstakeRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> getTransactionStatus(
                    BlockchainExecutionRequests.TransactionStatusRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(),
                        request.networkId(),
                        BlockchainTransactionResult.TransactionOutcome.PENDING,
                        request.signature(),
                        null,
                        0,
                        null,
                        null,
                        Instant.now(),
                        null));
            }
        };

        TestContext context = withProviders(pendingProvider);

        BlockchainTransactionResult timeout = context.service.awaitConfirmation(
                "SOLONA",
                "tx-timeout",
                "sig-timeout",
                Duration.ofMillis(20)).join();

        assertThat(timeout.outcome()).isEqualTo(BlockchainTransactionResult.TransactionOutcome.TIMEOUT);
    }

    @Test
    void pendingTransactionTrackedCorrectly() {
        BlockchainExecutionProvider pendingProvider = new AbstractBlockchainExecutionProvider() {
            @Override
            public String networkId() {
                return "SOLONA";
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitTransfer(
                    BlockchainExecutionRequests.TransferRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(),
                        networkId(),
                        BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending",
                        null,
                        0,
                        null,
                        null,
                        Instant.now(),
                        null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitSwap(
                    BlockchainExecutionRequests.SwapRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> submitOrder(
                    BlockchainExecutionRequests.OrderRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> cancelOrder(
                    BlockchainExecutionRequests.CancelOrderRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> stake(
                    BlockchainExecutionRequests.StakeRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }

            @Override
            public CompletableFuture<BlockchainTransactionResult> unstake(
                    BlockchainExecutionRequests.UnstakeRequest request) {
                return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                        request.transactionId(), networkId(), BlockchainTransactionResult.TransactionOutcome.PENDING,
                        "sig-pending", null, 0, null, null, Instant.now(), null));
            }
        };

        TestContext context = withProviders(pendingProvider);
        BlockchainExecutionRequests.TransferRequest request = BlockchainExecutionRequests.TransferRequest.create(
                "SOLONA", "wallet-a", "wallet-b", "SOL", 1.0);

        BlockchainTransactionResult result = context.service.executeTransfer(request).join();

        assertThat(result.outcome()).isEqualTo(BlockchainTransactionResult.TransactionOutcome.PENDING);
        assertThat(context.tracker.get(result.transactionId())).isPresent();
        assertThat(context.tracker.get(result.transactionId()).orElseThrow().outcome())
                .isEqualTo(BlockchainTransactionResult.TransactionOutcome.PENDING);
    }

    @Test
    void solonaAndStellarProvidersReturnBlockchainTransactionResult() {
        SolonaExecutionProvider solona = new SolonaExecutionProvider();
        StellarExecutionProvider stellar = new StellarExecutionProvider();

        BlockchainTransactionResult solonaResult = solona.submitOrder(
                BlockchainExecutionRequests.OrderRequest.marketOrder(
                        "SOLONA", "wallet", "SOL/USDC", "BUY", 1.0))
                .join();

        BlockchainTransactionResult stellarResult = stellar.submitOrder(
                BlockchainExecutionRequests.OrderRequest.marketOrder(
                        "STELLAR", "wallet", "XLM/USDC", "BUY", 2.0))
                .join();

        assertThat(solonaResult.networkId()).isEqualTo("SOLONA");
        assertThat(stellarResult.networkId()).isEqualTo("STELLAR");
    }

    @Test
    void eventsEmittedCorrectly() {
        TestContext context = withProviders(new SolonaExecutionProvider());

        List<String> events = new CopyOnWriteArrayList<>();
        context.eventBus.subscribeAll(event -> events.add(event.type()));

        BlockchainExecutionRequests.TransferRequest request = BlockchainExecutionRequests.TransferRequest.create(
                "SOLONA", "wallet-a", "wallet-b", "SOL", 1.0);

        context.service.executeTransfer(request).join();

        assertThat(events).contains(AgentEvent.BLOCKCHAIN_TRANSACTION_SUBMITTED);
        assertThat(events).contains(AgentEvent.BLOCKCHAIN_TRANSACTION_CONFIRMED);
    }

    @Test
    void persistenceSurvivesRestart() {
        Path file = tempDir.resolve("blockchain-transactions.json");

        FileBlockchainTransactionRepository first = new FileBlockchainTransactionRepository(file);
        BlockchainTransactionResult result = BlockchainTransactionResult.confirmed(
                "tx-1", "SOLONA", "sig-1", 100L, 32);
        first.save(result);

        FileBlockchainTransactionRepository second = new FileBlockchainTransactionRepository(file);

        assertThat(second.load("tx-1")).isPresent();
        assertThat(second.load("tx-1").orElseThrow().signature()).isEqualTo("sig-1");
    }

    @Test
    void executionPipelineEnforcedByRiskValidation() {
        AtomicInteger submitCount = new AtomicInteger();
        BlockchainExecutionProvider provider = new SolonaExecutionProvider() {
            @Override
            public CompletableFuture<BlockchainTransactionResult> submitOrder(
                    BlockchainExecutionRequests.OrderRequest request) {
                submitCount.incrementAndGet();
                return super.submitOrder(request);
            }
        };

        TestContext context = withProviders(provider);

        BlockchainExecutionRequests.OrderRequest request = BlockchainExecutionRequests.OrderRequest.marketOrder(
                "SOLONA", "wallet-a", "SOL/USDC", "BUY", 1.0);

        BlockchainTransactionResult blocked = context.service
                .executeOrder(request, RiskDecision.rejected("risk violation")).join();

        assertThat(blocked.outcome()).isEqualTo(BlockchainTransactionResult.TransactionOutcome.SIMULATION_FAILED);
        assertThat(submitCount.get()).isEqualTo(0);
    }

    private TestContext withProviders(BlockchainExecutionProvider... providers) {
        AgentEventBus bus = new AgentEventBus();
        bus.start();

        BlockchainTransactionRepository repository = new InMemoryBlockchainTransactionRepository();
        BlockchainTransactionTracker tracker = new BlockchainTransactionTracker();
        BlockchainPortfolioIntegrationService portfolio = new BlockchainPortfolioIntegrationService();
        BlockchainExecutionTelemetry telemetry = new BlockchainExecutionTelemetry();
        ConfirmationPolicy confirmationPolicy = new ConfirmationPolicy();
        BlockchainPreflightRiskValidator riskValidator = new BlockchainPreflightRiskValidator();

        BlockchainExecutionService service = new BlockchainExecutionService(
                List.of(providers),
                repository,
                tracker,
                portfolio,
                telemetry,
                confirmationPolicy,
                riskValidator,
                bus);

        return new TestContext(service, repository, tracker, portfolio, bus);
    }

    private record TestContext(
            BlockchainExecutionService service,
            BlockchainTransactionRepository repository,
            BlockchainTransactionTracker tracker,
            BlockchainPortfolioIntegrationService portfolio,
            AgentEventBus eventBus) {
    }
}
