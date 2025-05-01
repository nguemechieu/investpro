package org.investpro.investpro;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import org.investpro.grpc.HealthStatus;
import org.investpro.grpc.MarketDataRequest;
import org.investpro.grpc.PredictionResponse;
import org.investpro.grpc.PredictorGrpc;

@Slf4j
public class AIPredictorClient {

    private final PredictorGrpc.PredictorBlockingStub stub;
    private final ManagedChannel channel;

    public AIPredictorClient(String host, int port) {
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PredictorGrpc.newBlockingStub(channel);
    }

    public static void main(String[] args) {
        AIPredictorClient client = new AIPredictorClient("localhost", 50051);

        while (true) {
            try {
                client.checkHealth();
                client.predict(
                        30500.12f, 30610.25f, 30700.00f, 30420.45f, 3210.87f,
                        58.0f, 120.5f, 1.3f, 80.0f, 31000f, 30000f
                );
            } catch (Exception e) {
                log.error("❌ gRPC Error: {}", e.getMessage(), e);
            }

            try {
                Thread.sleep(1000); // Poll every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        client.shutdown();
    }

    public void checkHealth() {
        try {
            HealthStatus status = stub.healthCheck(Empty.newBuilder().build());
            log.info("✅ Server Health Status: {}", status.getStatus());
        } catch (Exception e) {
            log.error("❌ Health Check Failed: {}", e.getMessage());
        }
    }

    public String[] predict(
            float open, float close, float high, float low, float volume,
            float rsi, float atr, float macd, float stoch, float bbUpper, float bbLower) {

        try {
            MarketDataRequest request = MarketDataRequest.newBuilder()
                    .setOpen(open)
                    .setClose(close)
                    .setHigh(high)
                    .setLow(low)
                    .setVolume(volume)
                    .setRsi(rsi)
                    .setAtr(atr)
                    .setMacd(macd)
                    .setStoch(stoch)
                    .setBbUpper(bbUpper)
                    .setBbLower(bbLower)
                    .build();

            PredictionResponse response = stub.predict(request);
            log.info("📈 Prediction: {}", response.getPrediction());
            log.info("🎯 Confidence: {}", response.getConfidence());

            return new String[]{String.format("%s%s", response.getPrediction(), response.getConfidence())};

        } catch (Exception e) {
            log.error("❌ Prediction Error: {}", e.getMessage(), e);
        }
        return null;
    }

    public void shutdown() {
        if (!channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
