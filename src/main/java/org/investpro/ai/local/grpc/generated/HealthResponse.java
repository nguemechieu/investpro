package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hand-written POJO replacing the protobuf-generated HealthResponse.
 * Deserialised from JSON returned by the local Python AI HTTP service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HealthResponse {

    @JsonProperty("ok")
    private boolean ok;

    @JsonProperty("status")
    private String status = "UNKNOWN";

    @JsonProperty("service_name")
    private String serviceName = "";

    @JsonProperty("avg_latency_ms")
    private double avgLatencyMs;

    @JsonProperty("version")
    private String version = "";

    /** @return {@code true} if the service reports itself healthy. */
    public boolean getOk() {
        return ok;
    }

    public String getStatus() {
        return status != null ? status : "UNKNOWN";
    }

    public String getServiceName() {
        return serviceName != null ? serviceName : "";
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public String getVersion() {
        return version != null ? version : "";
    }
}
