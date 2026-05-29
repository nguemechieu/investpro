from __future__ import annotations

from app.utils.validation import clamp


class SignalAnalyzer:
    def analyze(self, req) -> dict:
        reasons: list[str] = []
        warnings: list[str] = []

        confidence = clamp(req.confidence, 0.0, 1.0)
        spread_penalty = clamp(req.spread * 10.0, 0.0, 0.35)
        volatility_penalty = clamp(req.volatility * 0.8, 0.0, 0.25)
        ai_confidence = clamp(confidence - spread_penalty - volatility_penalty, 0.0, 1.0)

        approved = ai_confidence >= 0.55
        recommendation = "APPROVE" if approved else "WAIT"
        size_adjustment = 1.0

        if req.market_regime.lower() in {"volatile", "chaotic"}:
            size_adjustment *= 0.7
            warnings.append("Volatile regime: reduced position size")
        if req.spread > 0.003:
            size_adjustment *= 0.75
            warnings.append("Spread too wide")
        if req.volatility > 0.05:
            size_adjustment *= 0.8
            warnings.append("High volatility")

        if approved:
            reasons.append("Signal quality acceptable for advisory approval")
        else:
            reasons.append("Signal confidence/risk profile below threshold")

        return {
            "approved": approved,
            "ai_confidence": ai_confidence,
            "recommendation": recommendation,
            "size_adjustment": clamp(size_adjustment, 0.1, 1.0),
            "reasons": reasons,
            "warnings": warnings,
        }
