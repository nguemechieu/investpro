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

    public AIPredictorClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PredictorGrpc.newBlockingStub(channel);
    }

    public String[] predict(MarketDataRequest request) {

        try {


            PredictionResponse response = stub.predict(request);
            log.info("📈 Prediction: {}", response.getPrediction());
            log.info("🎯 Confidence: {}", response.getConfidence());

            return new String[]{
                    response.getPrediction(),
                    String.valueOf(response.getConfidence())
            };

        } catch (Exception e) {
            log.error("❌ Prediction Error: {}", e.getMessage(), e);
        }
        return new String[]{"error", "0"};
    }

    public void checkHealth() {
        try {
            HealthStatus status = stub.healthCheck(Empty.newBuilder().build());
            log.info("✅ Server Health Status: {}", status.getStatus());
        } catch (Exception e) {
            log.error("❌ Health Check Failed: {}", e.getMessage(), e);
        }
    }


}
