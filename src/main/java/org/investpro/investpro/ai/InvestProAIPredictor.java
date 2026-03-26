package org.investpro.investpro.ai;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.investpro.grpc.Predict;
import org.investpro.grpc.PredictorGrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI predictor client that communicates with a remote gRPC prediction server.
 */
@Slf4j
public class InvestProAIPredictor {
    private static final long RETRY_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final ManagedChannel channel;
    private final PredictorGrpc.PredictorStub asyncStub;
    private final PredictorGrpc.PredictorBlockingStub blockingStub;
    private final String host;
    private final int port;
    private final AtomicBoolean outageLogged = new AtomicBoolean(false);
    private volatile long unavailableUntilMillis;

    public InvestProAIPredictor(String host, int port) {
        this.host = host;
        this.port = port;
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = PredictorGrpc.newStub(channel);
        this.blockingStub = PredictorGrpc.newBlockingStub(channel);
        log.debug("Configured gRPC predictor client for {}:{}", host, port);
    }

    public CompletableFuture<List<Predict.PredictionResponse>> streamBatchPredict(List<Predict.MarketDataRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (isInBackoffWindow()) {
            CompletableFuture<List<Predict.PredictionResponse>> unavailableFuture = new CompletableFuture<>();
            unavailableFuture.completeExceptionally(new IllegalStateException("AI predictor is temporarily unavailable."));
            return unavailableFuture;
        }

        CompletableFuture<List<Predict.PredictionResponse>> future = new CompletableFuture<>();
        List<Predict.PredictionResponse> responseList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<Predict.PredictionResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Predict.PredictionResponse response) {
                responseList.add(response);
                log.debug("Streamed prediction: {} (confidence: {}%)",
                        response.getPrediction(),
                        response.getConfidence());
            }

            @Override
            public void onError(Throwable throwable) {
                markUnavailable("Stream prediction failed", throwable);
                future.completeExceptionally(throwable);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                markAvailable();
                log.debug("Stream batch prediction completed.");
                future.complete(responseList);
                latch.countDown();
            }
        };

        StreamObserver<Predict.MarketDataRequest> requestObserver = asyncStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .batchPredictStream(responseObserver);

        try {
            for (Predict.MarketDataRequest request : requests) {
                requestObserver.onNext(request);
            }
            requestObserver.onCompleted();

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Stream prediction timed out before completion.");
            }
        } catch (Exception ex) {
            markUnavailable("Stream prediction failed", ex);
            requestObserver.onError(ex);
            future.completeExceptionally(ex);
        }

        return future;
    }

    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdownNow();
                log.debug("gRPC predictor channel shutdown");
            } catch (Exception ex) {
                log.warn("Error during channel shutdown: {}", ex.getMessage(), ex);
            }
        }
    }

    public boolean checkHealth() {
        if (isInBackoffWindow()) {
            return false;
        }

        try {
            Predict.HealthStatus status = blockingStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .healthCheck(Empty.newBuilder().build());
            markAvailable();
            log.debug("AI predictor health status: {}", status.getStatus());
            return true;
        } catch (Exception ex) {
            markUnavailable("Health check failed", ex);
            return false;
        }
    }

    public void reloadModel(String path) {
        try {
            Predict.ReloadModelRequest request = Predict.ReloadModelRequest.newBuilder()
                    .setModelPath(path)
                    .build();
            Predict.ReloadModelResponse response = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .reloadModel(request);
            markAvailable();
            log.info("Reload status: {} | Details: {}", response.getStatus(), response.getDetails());
        } catch (Exception ex) {
            markUnavailable("Failed to reload model", ex);
        }
    }

    public void printModelInfo() {
        try {
            Predict.ModelInfo info = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getModelInfo(Empty.newBuilder().build());
            markAvailable();
            log.info("Model info - Name: {}, Version: {}, Framework: {}, Last Trained: {}",
                    info.getName(),
                    info.getVersion(),
                    info.getFramework(),
                    info.getLastTrained());
        } catch (Exception ex) {
            markUnavailable("Failed to retrieve model info", ex);
        }
    }

    public boolean sendTradeFeedback(String tradeId, String result, double pnl) {
        try {
            Predict.TradeFeedbackRequest request = Predict.TradeFeedbackRequest.newBuilder()
                    .setTradeId(tradeId)
                    .setResult(result)
                    .setPnl(pnl)
                    .build();
            Predict.FeedbackResponse response = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .sendTradeFeedback(request);
            markAvailable();
            log.info("Feedback response: {}", response.getStatus());
            return true;
        } catch (Exception ex) {
            markUnavailable("Failed to send feedback", ex);
            return false;
        }
    }

    private boolean isInBackoffWindow() {
        return System.currentTimeMillis() < unavailableUntilMillis;
    }

    private void markAvailable() {
        unavailableUntilMillis = 0;
        if (outageLogged.getAndSet(false)) {
            log.info("AI predictor at {}:{} is reachable again.", host, port);
        }
    }

    private void markUnavailable(String operation, Throwable throwable) {
        unavailableUntilMillis = System.currentTimeMillis() + RETRY_BACKOFF_MILLIS;

        String reason = throwable.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = throwable.getClass().getSimpleName();
        }

        if (outageLogged.compareAndSet(false, true)) {
            log.warn("{} for AI predictor at {}:{} ({}). Pausing retries for {} seconds.",
                    operation,
                    host,
                    port,
                    reason,
                    TimeUnit.MILLISECONDS.toSeconds(RETRY_BACKOFF_MILLIS));
        } else {
            log.debug("{} for AI predictor at {}:{} ({})", operation, host, port, reason, throwable);
        }
    }
}
