package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hand-written POJO replacing the protobuf-generated HealthRequest.
 * Used to carry source metadata when calling the AI health endpoint.
 */
public final class HealthRequest {

    @JsonProperty("source")
    private String source = "";

    private HealthRequest() {}

    public String getSource() {
        return source;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private final HealthRequest instance = new HealthRequest();

        public Builder setSource(String v) {
            instance.source = v != null ? v : "";
            return this;
        }

        public HealthRequest build() {
            return instance;
        }
    }
}
