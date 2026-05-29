package org.investpro.ai.local.grpc;

import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.ai.LocalAiReasoningService;

public class AiFallbackPolicy {

    private final AiReasoningService fallback;

    public AiFallbackPolicy() {
        this(new LocalAiReasoningService());
    }

    public AiFallbackPolicy(AiReasoningService fallback) {
        this.fallback = fallback == null ? new LocalAiReasoningService() : fallback;
    }

    public AiTradeReviewResponse fallbackTradeReview(AiTradeReviewRequest request) {
        return fallback.reviewTrade(request);
    }

    public String fallbackServiceName() {
        return fallback.getServiceName();
    }
}
