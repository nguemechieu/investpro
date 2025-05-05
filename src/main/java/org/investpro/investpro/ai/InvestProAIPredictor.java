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

/**
 * AI Predictor client that communicates with a remote gRPC prediction server using streaming.
 */
@Slf4j
public class InvestProAIPredictor {

    private final ManagedChannel channel;
    private final PredictorGrpc.PredictorStub asyncStub;
    private final PredictorGrpc.PredictorBlockingStub blockingStub;

    /**
     * Constructs the predictor client with the specified host and port.
     */
    public InvestProAIPredictor(String host, int port) {
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = PredictorGrpc.newStub(channel);
        this.blockingStub = PredictorGrpc.newBlockingStub(channel);
        log.info("🔗 gRPC channel established to {}:{}", host, port);
    }

    public CompletableFuture<List<Predict.PredictionResponse>> streamBatchPredict(List<Predict.MarketDataRequest> requests) {
        CompletableFuture<List<Predict.PredictionResponse>> future = new CompletableFuture<>();
        List<Predict.PredictionResponse> responseList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<Predict.PredictionResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Predict.PredictionResponse response) {
                responseList.add(response); // collect result
                log.info("📈 Streamed Prediction: {} (Confidence: {}%)",
                        response.getPrediction(), response.getConfidence());
            }

            @Override
            public void onError(Throwable t) {
                log.error("❌ Stream prediction failed: {}", t.getMessage(), t);
                future.completeExceptionally(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("✅ Stream batch prediction completed.");
                future.complete(responseList);
                latch.countDown();
            }
        };

        StreamObserver<Predict.MarketDataRequest> requestObserver = asyncStub.batchPredictStream(responseObserver);

        try {
            for (Predict.MarketDataRequest req : requests) {
                requestObserver.onNext(req);
            }
            requestObserver.onCompleted();
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("⏰ Stream prediction timed out before completion.");
            }

        } catch (Exception e) {
            requestObserver.onError(e);
            future.completeExceptionally(e);
        }

        return future;
    }


    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdownNow();
                log.info("🔌 gRPC channel shutdown");
            } catch (Exception e) {
                log.warn("⚠️ Error during channel shutdown: {}", e.getMessage(), e);
            }
        }
    }

    public void checkHealth() {
        try {
            Predict.HealthStatus status = blockingStub.healthCheck(Empty.newBuilder().build());
            log.info("✅ Server Health Status: {}", status.getStatus());
        } catch (Exception e) {
            log.error("❌ Health Check Failed: {}", e.getMessage(), e);
        }
    }

    public void reloadModel(String path) {
        try {
            Predict.ReloadModelRequest request = Predict.ReloadModelRequest.newBuilder()
                    .setModelPath(path)
                    .build();
            Predict.ReloadModelResponse response = blockingStub.reloadModel(request);
            log.info("🔁 Reload Status: {} | Details: {}", response.getStatus(), response.getDetails());
        } catch (Exception e) {
            log.error("❌ Failed to reload model: {}", e.getMessage(), e);
        }
    }

    public void printModelInfo() {
        try {
            Predict.ModelInfo info = blockingStub.getModelInfo(Empty.newBuilder().build());
            log.info("📊 Model Info - Name: {}, Version: {}, Framework: {}, Last Trained: {}",
                    info.getName(), info.getVersion(), info.getFramework(), info.getLastTrained());
        } catch (Exception e) {
            log.error("❌ Failed to retrieve model info: {}", e.getMessage(), e);
        }
    }

    public boolean sendTradeFeedback(String tradeId, String result, double pnl) {
        try {
            Predict.TradeFeedbackRequest request = Predict.TradeFeedbackRequest.newBuilder()
                    .setTradeId(tradeId)
                    .setResult(result)
                    .setPnl(pnl)
                    .build();
            Predict.FeedbackResponse response = blockingStub.sendTradeFeedback(request);
            log.info("📤 Feedback Response: {}", response.getStatus());
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to send feedback: {}", e.getMessage(), e);
            return false;
        }
    }
}
