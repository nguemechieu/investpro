package org.investpro.ai;

import java.time.Instant;
import java.util.Optional;

public record AiTradingDisclaimer(
        String text,
        String version,
        Instant acceptedAt,
        Optional<String> acceptedByUserId) {

    public static final String DISCLAIMER_TEXT = "AI can help generate strategy ideas, but AI may be inaccurate or incomplete. "
            + "Trading involves substantial risk, and you may lose money. Every AI-generated strategy must pass InvestPro "
            + "validation, backtesting, and risk checks before assignment. You are responsible for all trading decisions.";

    public static AiTradingDisclaimer accepted(Optional<String> userId) {
        return new AiTradingDisclaimer(DISCLAIMER_TEXT, "1.0", Instant.now(), userId == null ? Optional.empty() : userId);
    }
}
