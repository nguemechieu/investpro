package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Hand-written POJO replacing the protobuf-generated SignalReviewResponse.
 * Deserialised from JSON returned by the local Python AI HTTP service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SignalReviewResponse {

    @JsonProperty("approved")        private boolean approved;
    @JsonProperty("ai_confidence")   private double  aiConfidence;
    @JsonProperty("recommendation")  private String  recommendation = "";
    @JsonProperty("size_adjustment") private double  sizeAdjustment = 1.0;
    @JsonProperty("reasons")         private List<String> reasons;
    @JsonProperty("warnings")        private List<String> warnings;

    public boolean getApproved()       { return approved; }
    public double  getAiConfidence()   { return aiConfidence; }
    public String  getRecommendation() { return recommendation != null ? recommendation : ""; }
    public double  getSizeAdjustment() { return sizeAdjustment; }

    /** @return the advisory reasons list; never {@code null}. */
    public List<String> getReasonsList() {
        return reasons != null ? reasons : Collections.emptyList();
    }

    /** @return the advisory warnings list; never {@code null}. */
    public List<String> getWarningsList() {
        return warnings != null ? warnings : Collections.emptyList();
    }
}
