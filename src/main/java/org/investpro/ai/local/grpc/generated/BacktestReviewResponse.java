package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Hand-written POJO replacing the protobuf-generated BacktestReviewResponse.
 * Deserialised from JSON returned by the local Python AI HTTP service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BacktestReviewResponse {

    @JsonProperty("accepted")          private boolean      accepted;
    @JsonProperty("ai_score")          private double       aiScore;
    @JsonProperty("overfit_risk")      private double       overfitRisk;
    @JsonProperty("warnings")          private List<String> warnings;
    @JsonProperty("rejection_reasons") private List<String> rejectionReasons;

    public boolean getAccepted()    { return accepted; }
    public double  getAiScore()     { return aiScore; }
    public double  getOverfitRisk() { return overfitRisk; }

    /** @return warnings list; never {@code null}. */
    public List<String> getWarningsList() {
        return warnings != null ? warnings : Collections.emptyList();
    }

    /** @return rejection reasons list; never {@code null}. */
    public List<String> getRejectionReasonsList() {
        return rejectionReasons != null ? rejectionReasons : Collections.emptyList();
    }
}
